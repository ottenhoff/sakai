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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.tree.Xml;

class JspToJspxTest {
    @TempDir Path webapp;

    @Test
    void convertsScriptletsExpressionsAndMessyHtmlThroughTheActualRecipe() throws Exception {
        String source = "<%@ page contentType=\"text/html;charset=UTF-8\" %>\r\n" +
                "<%-- License --%><% String name = \"A&B\"; %>" +
                "<body onload=\"<%= request.getAttribute(\"html.body.onload\") %>\">" +
                "<script>if (1 < 2 && true) { console.log(']]>'); }</script>" +
                "<br>é\r\n<%= name %></body>";
        Xml.Document converted = convert(source, Map.of());
        String xml = converted.printAll();
        assertTrue(xml.contains("<jsp:scriptlet>"));
        assertTrue(xml.contains("<jsp:expression>"));
        assertTrue(xml.contains("<jsp:text><![CDATA[<body onload=\"]]>"));
        assertTrue(xml.contains("]]>&#13;<![CDATA["));
        assertTrue(xml.contains("]]]]><![CDATA[>"));
        assertTrue(xml.contains("License"));
        assertFalse(xml.contains("jsp:id"));
        List<Throwable> errors = new ArrayList<>();
        JspToJspx recipe = recipe(Map.of());
        assertTrue(recipe.run(new InMemoryLargeSourceSet(List.of(converted)),
                new InMemoryExecutionContext(errors::add)).getChangeset().getAllResults().isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void retainsIncludeDirectivesInsteadOfInliningThem() throws Exception {
        Files.writeString(webapp.resolve("part.jsp"), "<p>included</p>");
        String xml = convert("Before<%@ include file=\"part.jsp\" %>After", Map.of()).printAll();
        assertTrue(xml.contains("<jsp:directive.include file=\"part.jsp\"/>"));
        assertFalse(xml.contains("<p>included</p>"));
    }

    @Test
    void refusesToOverwriteAnExistingJspxTarget() throws Exception {
        Files.writeString(webapp.resolve("page.jspx"), "Existing work");
        rejects("<%@ page contentType=\"text/html;charset=UTF-8\" %>Hello");
        assertEquals("Existing work", Files.readString(webapp.resolve("page.jspx")));
    }

    @Test
    void rejectsPagesWhoseImplicitResponseEncodingWouldChange() throws Exception {
        rejects("Hello");
    }

    @Test
    void explicitlyNormalizesMissingResponseCharsetsToUtf8() throws Exception {
        String source = "<%@ page contentType=\"application/json\" %>{\"message\":\"Hello\"}";
        String xml = convertSource(source, new JspToJspx(webapp.toString(),
                System.getProperty("java.class.path"), Map.of(), false, "UTF-8")).printAll();
        assertTrue(xml.contains("contentType=\"application/json;charset=UTF-8\""));
        assertEquals(source, Files.readString(webapp.resolve("page.jsp")));
    }

    @Test
    void retainsAnExistingResponseCharsetWhenUtf8NormalizationIsEnabled() throws Exception {
        String source = "<%@ page contentType=\"text/html;charset=ISO-8859-1\" %>Hello";
        String xml = convertSource(source, new JspToJspx(webapp.toString(),
                System.getProperty("java.class.path"), Map.of(), false, "UTF-8")).printAll();
        assertTrue(xml.contains("contentType=\"text/html;charset=ISO-8859-1\""));
    }

    @Test
    void trimsOnlyWhitespaceTemplateTextAndPreservesMixedText() throws Exception {
        String source = "<%@ page contentType=\"application/json;charset=UTF-8\" " +
                "trimDirectiveWhitespaces=\"true\" %>\r\n  <%-- before --%>\t\n" +
                "<% String value = \"ok\"; %>\n\t {\"message\":\"<%= value %>\"} \r\n";
        String xml = convert(source, Map.of()).printAll();
        assertTrue(xml.contains("trimDirectiveWhitespaces=\"true\""));
        assertEquals(2, xml.split("<jsp:text>", -1).length - 1);
        assertTrue(xml.contains("<jsp:text>\n\t {\"message\":\"</jsp:text>"));
        assertTrue(xml.contains("\"} &#13;\n</jsp:text>"));
    }

    @Test
    void rejectsInvalidJavaInsteadOfProducingXmlThatOnlyLooksValid() throws Exception {
        rejects("<%@ page contentType=\"text/html;charset=UTF-8\" %><%= missingVariable %>");
    }

    @Test
    void validatesTheCheckedInJspxExample() throws Exception {
        try (java.io.InputStream input = getClass().getResourceAsStream("/examples/displayImageMapQuestion.jspx")) {
            assertNotNull(input);
            String example = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            XmlValidation.validate(example);
            SourceFile parsed = org.openrewrite.xml.XmlParser.builder().build().parse(example).findFirst().orElseThrow();
            assertInstanceOf(Xml.Document.class, parsed);
        }
    }

    @Test
    void retainsTheSourceDirectoryWhenRenamingAnAbsoluteInputPath() throws Exception {
        String source = "<%@ page contentType=\"text/html;charset=UTF-8\" %>Hello";
        Path inputPath = webapp.resolve("page.jsp");
        Files.writeString(inputPath, source);
        SourceFile input = PlainText.builder().sourcePath(inputPath).text(source).build();
        List<Throwable> errors = new ArrayList<>();
        List<Result> results = recipe(Map.of()).run(new InMemoryLargeSourceSet(List.of(input)),
                new InMemoryExecutionContext(errors::add)).getChangeset().getAllResults();
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(webapp.resolve("page.jspx"), results.get(0).getAfter().getSourcePath());
    }

    private void rejects(String source) throws Exception {
        Files.writeString(webapp.resolve("page.jsp"), source);
        SourceFile input = PlainText.builder().sourcePath(Path.of("page.jsp")).text(source).build();
        List<Throwable> errors = new ArrayList<>();
        List<Result> results = recipe(Map.of()).run(new InMemoryLargeSourceSet(List.of(input)),
                new InMemoryExecutionContext(errors::add)).getChangeset().getAllResults();
        assertFalse(errors.isEmpty());
        assertTrue(results.stream().noneMatch(result -> result.getAfter() instanceof Xml.Document));
        assertEquals(source, Files.readString(webapp.resolve("page.jsp")));
    }

    private JspToJspx recipe(Map<String, String> taglibs) {
        return new JspToJspx(webapp.toString(), System.getProperty("java.class.path"), taglibs, false);
    }

    private Xml.Document convert(String source, Map<String, String> taglibs) throws Exception {
        if (!source.contains("contentType=")) {
            source = "<%@ page contentType=\"text/html;charset=UTF-8\" %>" + source;
        }
        return convertSource(source, recipe(taglibs));
    }

    private Xml.Document convertSource(String source, JspToJspx conversionRecipe) throws Exception {
        Files.writeString(webapp.resolve("page.jsp"), source);
        SourceFile input = PlainText.builder().sourcePath(Path.of("page.jsp")).text(source).build();
        List<Throwable> errors = new ArrayList<>();
        List<Result> results = conversionRecipe.run(new InMemoryLargeSourceSet(List.of(input)),
                new InMemoryExecutionContext(errors::add)).getChangeset().getAllResults();
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(1, results.size());
        Xml.Document result = assertInstanceOf(Xml.Document.class, results.get(0).getAfter());
        assertEquals(Path.of("page.jspx"), result.getSourcePath());
        return result;
    }
}
