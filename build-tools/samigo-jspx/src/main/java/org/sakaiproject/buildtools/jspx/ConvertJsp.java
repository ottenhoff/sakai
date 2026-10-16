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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.SourceFile;
import org.openrewrite.xml.tree.Xml;

/** Small CLI around the real OpenRewrite recipe; input paths are webapp-relative. */
public final class ConvertJsp {
    public static void main(String[] args) throws Exception {
        if (args.length < 5 || args.length > 7) {
            throw new IllegalArgumentException("Usage: webapp relative.jsp output.jspx classpath-file fragment [taglibs-json] [default-response-charset]");
        }
        Path webapp = Path.of(args[0]).toAbsolutePath();
        Path input = Path.of(args[1]);
        Map<String, String> taglibs = args.length >= 6 ? new ObjectMapper().readValue(
                Path.of(args[5]).toFile(), new TypeReference<Map<String, String>>() { }) : Map.of();
        List<Throwable> errors = new ArrayList<>();
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(errors::add);
        SourceFile source = PlainTextParser.builder().build().parse(List.of(webapp.resolve(input)), webapp, ctx)
                .findFirst().orElseThrow();
        JspToJspx recipe = new JspToJspx(webapp.toString(), Files.readString(Path.of(args[3])).trim(),
                taglibs, Boolean.parseBoolean(args[4]), args.length == 7 ? args[6] : null);
        RecipeRun run = recipe.run(new InMemoryLargeSourceSet(List.of(source)), ctx);
        List<Result> results = run.getChangeset().getAllResults();
        if (!errors.isEmpty() || results.size() != 1 || !(results.get(0).getAfter() instanceof Xml.Document document)
                || !document.getSourcePath().toString().endsWith(".jspx")) {
            throw new IllegalStateException("Recipe did not produce one valid JSPX document", errors.isEmpty() ? null : errors.get(0));
        }
        Path output = Path.of(args[2]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, document.printAllAsBytes());
        System.out.println("Converted " + input + " -> " + output);
    }
}
