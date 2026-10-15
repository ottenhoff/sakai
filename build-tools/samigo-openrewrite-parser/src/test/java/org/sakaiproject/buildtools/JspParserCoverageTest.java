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
package org.sakaiproject.buildtools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JspParserCoverageTest {
    @TempDir
    Path root;

    @Test
    void validJspRoundTripsWithDirectivesScriptletAndVoidElement() throws Exception {
        Path input = root.resolve("valid.jsp");
        Files.writeString(input, "<%@ page contentType=\"text/html\" %>\r\n"
                + "<html><body><% String name = \"test\"; %><br>#{bean.title}</body></html>\r\n");
        JspParserCoverage.Measurement result = JspParserCoverage.measure(root, input, false);
        assertEquals("PASS", result.status());
        assertTrue(result.byteIdentical());
        assertEquals(0, result.diagnosticCount());
    }

    @Test
    void parseErrorPreservingOriginalBytesDoesNotCountAsSuccess() throws Exception {
        Path input = root.resolve("broken.jsp");
        Files.writeString(input, "<html><body><p>missing closing tags");
        JspParserCoverage.Measurement result = JspParserCoverage.measure(root, input, false);
        assertEquals("PARSE_ERROR", result.status());
        assertTrue(result.byteIdentical());
    }

    @Test
    void fragmentExtensionRejectionIsSeparateFromParserCapability() throws Exception {
        Path input = root.resolve("include.jspf");
        Files.writeString(input, "<h:panelGroup>#{bean.title}</h:panelGroup>\n");
        assertEquals("UNSUPPORTED_EXTENSION", JspParserCoverage.measure(root, input, false).status());
        assertEquals("PASS", JspParserCoverage.measure(root, input, true).status());
        assertEquals("<h:panelGroup>#{bean.title}</h:panelGroup>\n", Files.readString(input));
    }

    @Test
    void unparsedDocumentAfterDirectivesIsNotACompleteParse() throws Exception {
        Path input = root.resolve("directives-before-doctype.jsp");
        Files.writeString(input, "<%@ taglib prefix=\"h\" uri=\"http://java.sun.com/jsf/html\" %>\n"
                + "<!DOCTYPE html>\n<html><body><h:form id=\"friendlyName\"/></body></html>");
        JspParserCoverage.Measurement result = JspParserCoverage.measure(root, input, false);
        assertEquals("UNPARSED_CONTENT", result.status());
        assertTrue(result.byteIdentical());
        assertEquals(0, result.elementCount());
        assertEquals(0, result.diagnosticCount());
    }

    @Test
    void secondRootPreservedAsRawTextIsNotACompleteParse() throws Exception {
        Path input = root.resolve("two-roots.jsp");
        Files.writeString(input, "<h:panelGroup/><h:panelGroup/>");
        JspParserCoverage.Measurement result = JspParserCoverage.measure(root, input, false);
        assertEquals("UNPARSED_CONTENT", result.status());
        assertTrue(result.byteIdentical());
        assertEquals(1, result.elementCount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parserBlockers")
    void recordsSmallReproductionsOfSamigoBlockers(String name, String source, String status) throws Exception {
        Path input = root.resolve(name + ".jsp");
        Files.writeString(input, source);
        JspParserCoverage.Measurement result = JspParserCoverage.measure(root, input, false);
        assertEquals(status, result.status());
        assertTrue(result.byteIdentical());
    }

    static Stream<Arguments> parserBlockers() {
        return Stream.of(
                Arguments.of("include-inside-element",
                        "<div><%@ include file=\"permissionsNav.jsp\" %></div>", "PARSE_ERROR"),
                Arguments.of("scriptlet-before-root",
                        "<%@ page language=\"java\" %>\n<% String name = \"test\"; %>\n<html/>", "UNPARSED_CONTENT"),
                Arguments.of("doctype-in-verbatim",
                        "<f:view><f:verbatim><!DOCTYPE html></f:verbatim><html/></f:view>", "PARSE_ERROR"),
                Arguments.of("component-inside-attribute",
                        "<input value='<h:outputText value=\"#{bean.title}\"/>'/>", "PARSE_ERROR"),
                Arguments.of("raw-javascript",
                        "<script>for (i=0; i<document.links.length; i++) { run(); }</script>", "PARSE_ERROR")
        );
    }
}
