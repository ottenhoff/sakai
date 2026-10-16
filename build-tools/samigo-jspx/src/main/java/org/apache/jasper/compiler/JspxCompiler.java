/*
 * Copyright (c) 2026 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jasper.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sakaiproject.buildtools.jspx.ConversionJspC;
import org.xml.sax.Attributes;

/** Narrow adapter to the pinned Jasper AST; never reads generated servlet names. */
public class JspxCompiler extends JDTCompiler {
    @Override
    protected Map<String, SmapStratum> generateJava() throws Exception {
        Map<String, SmapStratum> result = super.generateJava();
        ConversionJspC conversion = (ConversionJspC) ctxt.getOptions();
        if (conversion.isFragment()) {
            // The including translation unit owns automatic preludes/codas.
            getPageInfo().setIncludePrelude(null);
            getPageInfo().setIncludeCoda(null);
        }
        // Parse afresh: code generation may optimize text and apply tag plugins.
        Node.Nodes original = new ParserController(ctxt, this).parse(ctxt.getJspFile());
        conversion.setConvertedSource(new Emitter(conversion.isFragment(), getPageInfo()).emit(original));
        return result;
    }

    private static final class Emitter {
        private final boolean fragment;
        private final PageInfo pageInfo;
        private final Map<String, String> namespaces = new LinkedHashMap<>();
        private final StringBuilder body = new StringBuilder();
        private int depth = 1;

        private Emitter(boolean fragment, PageInfo pageInfo) {
            this.fragment = fragment;
            this.pageInfo = pageInfo;
            namespaces.put("jsp", "http://java.sun.com/JSP/Page");
        }

        private String emit(Node.Nodes nodes) throws Exception {
            if (!fragment && !pageInfo.getContentType().toLowerCase(java.util.Locale.ROOT).contains("charset=")) {
                throw new UnsupportedOperationException("Full JSP page needs an explicit response charset before JSPX conversion");
            }
            if (pageInfo.isTrimDirectiveWhitespaces()) {
                throw new UnsupportedOperationException("trimDirectiveWhitespaces needs a separate conversion rule");
            }
            collectNamespaces(nodes);
            write(nodes);
            StringBuilder output = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jsp:root version=\"3.0\"");
            namespaces.forEach((prefix, uri) -> output.append("\n    xmlns:").append(prefix)
                    .append("=\"").append(attribute(uri)).append('"'));
            output.append(">\n");
            if (!fragment) {
                output.append("<jsp:directive.page pageEncoding=\"UTF-8\" contentType=\"")
                        .append(attribute(pageInfo.getContentType())).append("\" isELIgnored=\"")
                        .append(pageInfo.isELIgnored()).append("\" deferredSyntaxAllowedAsLiteral=\"")
                        .append(pageInfo.isDeferredSyntaxAllowedAsLiteral()).append("\"/>\n")
                        .append("<jsp:output omit-xml-declaration=\"yes\"/>\n");
            }
            return output.append(body).append("</jsp:root>\n").toString();
        }

        private void bind(String prefix, String uri) {
            String previous = namespaces.putIfAbsent(prefix, uri);
            if (previous != null && !previous.equals(uri)) {
                throw new IllegalArgumentException("Conflicting taglib prefix: " + prefix);
            }
        }

        private void collectNamespaces(Node.Nodes nodes) throws Exception {
            if (nodes == null) {
                return;
            }
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.getNode(i);
                if (node instanceof Node.TaglibDirective) {
                    String uri = node.getAttributeValue("uri");
                    if (uri == null) {
                        uri = "urn:jsptagdir:" + node.getAttributeValue("tagdir");
                    } else if (uri.startsWith("/")) {
                        uri = "urn:jsptld:" + uri;
                    }
                    bind(node.getAttributeValue("prefix"), uri);
                } else if (node instanceof Node.CustomTag tag) {
                    bind(tag.getPrefix(), tag.getURI());
                }
                collectNamespaces(node.getBody());
            }
        }

        private void write(Node.Nodes nodes) throws Exception {
            if (nodes == null) {
                return;
            }
            StringBuilder pending = new StringBuilder();
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.getNode(i);
                if (node instanceof Node.TemplateText) {
                    pending.append(pageInfo.isELIgnored() ? node.getText() :
                            node.getText().replace("${", "\\${").replace("#{", "\\#{"));
                } else {
                    if (!pending.isEmpty()) {
                        text("jsp:text", pending.toString());
                        pending.setLength(0);
                    }
                    write(node);
                }
            }
            if (!pending.isEmpty()) {
                text("jsp:text", pending.toString());
            }
        }

        private void write(Node node) throws Exception {
            if (node instanceof Node.Root) {
                write(node.getBody());
            } else if (node instanceof Node.TaglibDirective) {
                // Namespace declarations replace these directives.
            } else if (node instanceof Node.IncludeDirective) {
                element("jsp:directive.include", node.getAttributes(), null);
            } else if (node instanceof Node.PageDirective) {
                StringBuilder directive = new StringBuilder();
                Attributes attributes = node.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    String name = attributes.getQName(i);
                    if (!name.equals("pageEncoding") && (fragment || !name.equals("contentType"))) {
                        directive.append(' ').append(name).append("=\"")
                                .append(attribute(attributes.getValue(i))).append('"');
                    }
                }
                if (!directive.isEmpty()) {
                    body.append("  ".repeat(depth)).append("<jsp:directive.page").append(directive).append("/>\n");
                }
            } else if (node instanceof Node.Comment) {
                body.append("  ".repeat(depth)).append("<!--").append(node.getText().replace("--", "- -"))
                        .append(" -->\n");
            } else if (node instanceof Node.TemplateText) {
                text("jsp:text", node.getText());
            } else if (node instanceof Node.ELExpression expression) {
                text("jsp:text", expression.getType() + "{" + expression.getText() + "}");
            } else if (node instanceof Node.Declaration) {
                text("jsp:declaration", node.getText());
            } else if (node instanceof Node.Expression) {
                text("jsp:expression", node.getText());
            } else if (node instanceof Node.Scriptlet) {
                text("jsp:scriptlet", node.getText());
            } else if (node instanceof Node.CustomTag tag) {
                if ("tagdependent".equalsIgnoreCase(tag.getTagInfo().getBodyContent())) {
                    throw new UnsupportedOperationException("Tag-dependent body: " + tag.getQName());
                }
                element(tag.getQName(), tag.getAttributes(), tag.getBody());
            } else {
                throw new UnsupportedOperationException("JSP node needs a conversion rule: " + node.getClass().getSimpleName());
            }
        }

        private void element(String name, Attributes attributes, Node.Nodes children) throws Exception {
            body.append("  ".repeat(depth)).append('<').append(name);
            // Tomcat 10.1.60 JspDocumentParser enumerates SAX attributes in
            // reverse. Reverse their spelling here to retain JSP setter order.
            // The generated-Java verification deliberately keeps setter order.
            for (int i = attributes == null ? -1 : attributes.getLength() - 1; i >= 0; i--) {
                body.append(' ').append(attributes.getQName(i)).append("=\"")
                        .append(JspUtil.getExprInXml(attributes.getValue(i))
                                .replace("\r", "&#13;").replace("\n", "&#10;").replace("\t", "&#9;"))
                        .append('"');
            }
            if (children == null) {
                body.append("/>\n");
            } else {
                body.append(">\n");
                depth++;
                write(children);
                depth--;
                body.append("  ".repeat(depth)).append("</").append(name).append(">\n");
            }
        }

        private void text(String name, String text) {
            body.append("  ".repeat(depth)).append('<').append(name).append("><![CDATA[")
                    .append(text.replace("]]>", "]]]]><![CDATA[>")
                            .replace("\r", "]]>&#13;<![CDATA["))
                    .append("]]></").append(name).append(">\n");
        }

        private static String attribute(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
                    .replace("\r", "&#13;").replace("\n", "&#10;").replace("\t", "&#9;");
        }
    }
}
