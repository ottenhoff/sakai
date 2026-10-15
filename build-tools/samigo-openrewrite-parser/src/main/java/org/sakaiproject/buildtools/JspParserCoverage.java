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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.ParseExceptionResult;
import org.openrewrite.tree.ParseError;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Measures unmodified source; a ParseError printing its original input is never a success. */
public final class JspParserCoverage {
    public record Measurement(String path, String parserPath, String status, String treeType,
                              boolean byteIdentical, String sha256, String charset,
                              String rootTag, int elementCount, int unparsedTrailingCharacters,
                              String unparsedTrailingPreview,
                              int diagnosticCount, List<String> diagnostics, String parseError) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected repository root, output JSON path, parser version");
        }
        Path root = Path.of(args[0]).toRealPath();
        Path reportPath = Path.of(args[1]).toAbsolutePath();
        List<Path> inputs;
        try (Stream<Path> files = Files.walk(root.resolve("samigo"))) {
            inputs = files.filter(Files::isRegularFile).filter(JspParserCoverage::isJspSource)
                    .filter(path -> !root.relativize(path).toString().contains("/target/"))
                    .sorted().toList();
        }
        if (inputs.isEmpty()) {
            throw new IllegalStateException("No Samigo JSP inputs found in " + root);
        }
        List<Measurement> nativeResults = new ArrayList<>();
        List<Measurement> fragmentProbes = new ArrayList<>();
        for (Path input : inputs) {
            nativeResults.add(measure(root, input, false));
            if (isFragment(input)) {
                // Only change the in-memory parser path to bypass extension rejection. No wrapper or cleanup.
                fragmentProbes.add(measure(root, input, true));
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sakaiCommit", git(root, "rev-parse", "HEAD").trim());
        report.put("samigoWorkingTreeStatus", git(root, "status", "--porcelain", "--", "samigo").trim());
        report.put("rewriteVersion", args[2]);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("successDefinition", "Xml.Document, zero parser diagnostics, no non-whitespace EOF content, byte-identical printAllAsBytes");
        report.put("jspSummary", counts(nativeResults.stream().filter(result -> !isFragment(Path.of(result.path()))).toList()));
        report.put("nativeSummary", counts(nativeResults));
        report.put("fragmentProbeSummary", counts(fragmentProbes));
        report.put("nativeResults", nativeResults);
        report.put("fragmentProbes", fragmentProbes);
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.println("JSPs: " + report.get("jspSummary"));
        System.out.println("All sources with native extensions: " + report.get("nativeSummary"));
        System.out.println("Fragments with synthetic .jsp paths: " + report.get("fragmentProbeSummary"));
        System.out.println("Report: " + reportPath);
    }

    public static Measurement measure(Path root, Path input, boolean forceJspExtension) throws IOException {
        Path relative = root.relativize(input);
        Path parserPath = forceJspExtension ? Path.of(relative + ".jsp") : relative;
        byte[] original = Files.readAllBytes(input);
        List<String> diagnostics = new ArrayList<>();
        ExecutionContext context = new InMemoryExecutionContext(error -> diagnostics.add(error.toString()));
        XmlParser parser = XmlParser.builder().build();
        String digest = sha256(original);
        if (!parser.accept(parserPath)) {
            return new Measurement(relative.toString(), parserPath.toString(), "UNSUPPORTED_EXTENSION",
                    "", false, digest, "", "", 0, 0, "", 0, List.of(), "");
        }
        Parser.Input parserInput = new Parser.Input(parserPath, () -> new ByteArrayInputStream(original));
        try (Stream<SourceFile> parsed = parser.parseInputs(List.of(parserInput), null, context)) {
            List<SourceFile> sources = parsed.toList();
            if (sources.size() != 1) {
                throw new IllegalStateException("Expected one result, got " + sources.size());
            }
            SourceFile source = sources.getFirst();
            boolean byteIdentical = Arrays.equals(original, source.printAllAsBytes());
            String parseError = source.getMarkers().findFirst(ParseExceptionResult.class)
                    .map(ParseExceptionResult::getMessage).orElse("");
            Xml.Document document = source instanceof Xml.Document xml ? xml : null;
            Xml.Tag rootTag = document == null ? null : document.getRoot();
            String trailing = document == null ? "" : document.getEof().strip();
            String status;
            if (source instanceof ParseError) {
                status = "PARSE_ERROR";
            } else if (!(source instanceof Xml.Document)) {
                status = "UNEXPECTED_TREE";
            } else if (!diagnostics.isEmpty()) {
                status = "PARSER_DIAGNOSTICS";
            } else if (!byteIdentical) {
                status = "ROUND_TRIP_MISMATCH";
            } else if (!trailing.isEmpty()) {
                status = "UNPARSED_CONTENT";
            } else {
                status = "PASS";
            }
            return new Measurement(relative.toString(), parserPath.toString(), status,
                    source.getClass().getSimpleName(), byteIdentical, digest,
                    source.getCharset() == null ? "" : source.getCharset().name(),
                    rootTag == null ? "" : rootTag.getName(), elementCount(rootTag), trailing.length(),
                    trailing.substring(0, Math.min(240, trailing.length())), diagnostics.size(),
                    diagnostics.stream().limit(5).map(JspParserCoverage::abbreviate).toList(), abbreviate(parseError));
        }
    }

    private static int elementCount(Xml.Tag tag) {
        return tag == null ? 0 : 1 + tag.getChildren().stream().mapToInt(JspParserCoverage::elementCount).sum();
    }

    private static Map<String, Long> counts(List<Measurement> results) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("TOTAL", (long) results.size());
        for (Measurement result : results) {
            counts.merge(result.status(), 1L, Long::sum);
        }
        return counts;
    }

    private static boolean isJspSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsp") || name.endsWith(".jspx") || isFragment(path);
    }

    private static boolean isFragment(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jspf") || name.endsWith(".inc");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String abbreviate(String value) {
        return value.length() > 2_000 ? value.substring(0, 2_000) + " [truncated]" : value;
    }

    private static String git(Path root, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output;
        try (java.io.InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (process.waitFor() != 0) {
            throw new IOException("Git metadata command failed: " + command);
        }
        return output;
    }
}
