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
package org.sakaiproject.buildtools.jspx;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;

/** Standard JSP to XML-syntax JSP, using Jasper's source AST as the semantic boundary. */
public class JspToJspx extends Recipe {
    @Option(displayName = "Web application directory", description = "Exploded source webapp used to resolve includes and web.xml.", example = "/work/samigo/src/webapp")
    private final String webappDirectory;

    @Option(displayName = "Application classpath", description = "Platform-separated dependency JARs and application class directories.", example = "/work/classes:/work/faces.jar")
    private final String classpath;

    @Option(displayName = "Inherited taglibs", description = "Prefix-to-URI mappings supplied by the parent of a JSP fragment.", required = false, example = "h: http://java.sun.com/jsf/html")
    private final Map<String, String> taglibs;

    @Option(displayName = "Fragment", description = "Convert an include fragment; omit translation-unit output settings and automatic preludes.", required = false, example = "true")
    private final boolean fragment;

    @Option(displayName = "Default response charset", description = "Explicitly normalize full pages without a response charset to this charset; existing response charsets are preserved.", required = false, example = "UTF-8")
    private final String defaultResponseCharset;

    public JspToJspx(String webappDirectory, String classpath, Map<String, String> taglibs, boolean fragment) {
        this(webappDirectory, classpath, taglibs, fragment, null);
    }

    @JsonCreator
    public JspToJspx(@JsonProperty("webappDirectory") String webappDirectory,
                     @JsonProperty("classpath") String classpath,
                     @JsonProperty("taglibs") Map<String, String> taglibs,
                     @JsonProperty("fragment") boolean fragment,
                     @JsonProperty("defaultResponseCharset") String defaultResponseCharset) {
        this.webappDirectory = webappDirectory;
        this.classpath = classpath;
        this.taglibs = taglibs == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(taglibs));
        this.fragment = fragment;
        this.defaultResponseCharset = defaultResponseCharset == null ? null :
                java.nio.charset.Charset.forName(defaultResponseCharset).name();
    }

    @Override
    public String getDisplayName() {
        return "Convert standard JSP to JSPX";
    }

    @Override
    public String getDescription() {
        return "Preserve JSP execution while converting source constructs into XML syntax, retaining original IDs and includes.";
    }

    public String getWebappDirectory() { return webappDirectory; }
    public String getClasspath() { return classpath; }
    public Map<String, String> getTaglibs() { return taglibs; }
    public boolean isFragment() { return fragment; }
    public String getDefaultResponseCharset() { return defaultResponseCharset; }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !source.getSourcePath().toString().endsWith(".jsp")) {
                    return tree;
                }
                try {
                    return convert(source, ctx);
                } catch (Exception e) {
                    throw new IllegalStateException("JSPX conversion failed for " + source.getSourcePath(), e);
                }
            }
        };
    }

    private Xml.Document convert(SourceFile source, ExecutionContext ctx) throws Exception {
        Path root = Path.of(webappDirectory).toAbsolutePath().normalize();
        Path relative = source.getSourcePath().normalize();
        if (relative.isAbsolute()) {
            relative = root.relativize(relative);
        } else {
            Path fromWorkingDirectory = relative.toAbsolutePath().normalize();
            if (fromWorkingDirectory.startsWith(root)) {
                relative = root.relativize(fromWorkingDirectory);
            }
        }
        if (relative.startsWith("..") || !Files.isRegularFile(root.resolve(relative))) {
            throw new IllegalArgumentException("Input paths must be relative to the webapp: " + relative);
        }
        Path target = relative.resolveSibling(relative.getFileName().toString().replaceFirst("\\.jsp$", ".jspx"));
        if (Files.exists(root.resolve(target), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("JSPX target already exists: " + target);
        }
        Path stage = Files.createTempDirectory("jspx-conversion-");
        try {
            Path webapp = stage.resolve("webapp");
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path original : paths.toList()) {
                    Path destination = webapp.resolve(root.relativize(original));
                    if (Files.isDirectory(original)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createSymbolicLink(destination, original.toAbsolutePath());
                    }
                }
            }
            Path input = webapp.resolve(relative);
            Files.delete(input);
            StringBuilder declarations = new StringBuilder();
            taglibs.forEach((prefix, uri) -> {
                if (!prefix.matches("[A-Za-z_][A-Za-z0-9_.-]*") || uri.contains("\"") || uri.contains("%>")) {
                    throw new IllegalArgumentException("Invalid inherited taglib: " + prefix);
                }
                declarations.append("<%@ taglib prefix=\"").append(prefix).append("\" uri=\"").append(uri).append("\" %>");
            });
            byte[] content = source.printAllAsBytes();
            byte[] prefix = declarations.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int bom = content.length >= 3 && content[0] == (byte) 0xef && content[1] == (byte) 0xbb && content[2] == (byte) 0xbf ? 3 : 0;
            try (java.io.OutputStream output = Files.newOutputStream(input)) {
                output.write(content, 0, bom);
                output.write(prefix);
                output.write(content, bom, content.length - bom);
            }
            Path libraries = webapp.resolve("WEB-INF/lib");
            Files.createDirectories(libraries);
            for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path path = Path.of(entry).toAbsolutePath();
                if (entry.endsWith(".jar") && !Files.exists(libraries.resolve(path.getFileName()))) {
                    Files.createSymbolicLink(libraries.resolve(path.getFileName()), path);
                }
            }
            ConversionJspC compiler = new ConversionJspC(fragment, defaultResponseCharset);
            compiler.setUriroot(webapp.toString());
            compiler.setJspFiles(relative.toString());
            compiler.setOutputDir(stage.resolve("generated").toString());
            compiler.setClassPath(classpath + File.pathSeparator + System.getProperty("java.class.path"));
            compiler.setThreadCount("1");
            compiler.setCompile(true);
            compiler.setFailOnError(true);
            compiler.execute();
            org.apache.jasper.compiler.JspConfig.JspProperty targetProperties =
                    compiler.getJspConfig().findJspProperty("/" + target);
            if (!fragment && ((targetProperties.getIncludePrelude() != null && !targetProperties.getIncludePrelude().isEmpty())
                    || (targetProperties.getIncludeCoda() != null && !targetProperties.getIncludeCoda().isEmpty()))) {
                throw new UnsupportedOperationException("JSPX target still matches automatic preludes/codas; web.xml adaptation is required");
            }
            String converted = compiler.getConvertedSource();
            XmlValidation.validate(converted);
            Path convertedFile = webapp.resolve(target);
            Files.writeString(convertedFile, converted, java.nio.charset.StandardCharsets.UTF_8);
            String validationPage = target.toString();
            if (fragment) {
                validationPage = "__jspx_validation.jsp";
                Path wrapper = webapp.resolve(validationPage);
                Files.deleteIfExists(wrapper);
                Files.writeString(wrapper, declarations + "<%@ include file=\"/" + target + "\" %>");
            }
            org.apache.jasper.JspC validation = new org.apache.jasper.JspC();
            validation.setUriroot(webapp.toString());
            validation.setJspFiles(validationPage);
            validation.setOutputDir(stage.resolve("validated").toString());
            validation.setClassPath(classpath + File.pathSeparator + System.getProperty("java.class.path"));
            validation.setThreadCount("1");
            validation.setCompile(true);
            validation.setFailOnError(true);
            validation.execute();
            SourceFile parsed;
            try (Stream<SourceFile> results = XmlParser.builder().build().parseInputs(
                    List.of(Parser.Input.fromString(target, converted)), null, ctx)) {
                parsed = results.findFirst().orElseThrow();
            }
            if (!(parsed instanceof Xml.Document document) || !document.printAll().equals(converted)) {
                throw new IllegalArgumentException("Converted JSPX did not round-trip through the XML parser");
            }
            Path outputPath = source.getSourcePath().resolveSibling(
                    source.getSourcePath().getFileName().toString().replaceFirst("\\.jsp$", ".jspx"));
            return document.withSourcePath(outputPath).withId(source.getId()).withMarkers(source.getMarkers());
        } finally {
            try (Stream<Path> paths = Files.walk(stage)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
