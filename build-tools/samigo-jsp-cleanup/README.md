# Samigo JSP cleanup pilot

Five existing JSP views now load named JavaScript assets instead of embedding
pure JavaScript in their markup. They still use JSP. The scripts remain blocking
classic scripts at their original positions, with the original function names,
globals, listeners, and JSF bindings. This pilot deliberately preserves existing
JavaScript behavior, including its legacy jQuery APIs, rather than combining a
JavaScript rewrite with parser preparation.

`pilot.json` identifies the five views and their source baseline. `extract.py`
reproduces this bounded change and refuses to overwrite unrelated edits. It is
not a general JSP-to-Facelets converter.

| View | Extracted asset | Patched OpenRewrite before → after |
| --- | --- | --- |
| `print/printAssessment.jsp` | `assessment-print-preview.js` | Error → Pass |
| `evaluation/gradeStudentResultAttachment.jsp` | `evaluation-attachments.js` | Error → Pass |
| `author/searchResults.jsp` | `question-search-results.js` | Error → Error |
| `delivery/submitted.jsp` | `assessment-submission.js` | Error → Error |
| `evaluation/item/displayImageMapQuestion.jsp` | `image-map-evaluation.js` | Error → Error |

The same 245 JSP corpus improves from **82 to 84 full parses**, with 161 explicit
errors and zero partial parses. Including four JSPF files and `header.inc`, the
result is **88/250**, up from 86/250. All 245 unchanged template files retain
their exact source hashes and parser outcomes. See `results.json` for the five
changed files, hashes, and remaining diagnostics.

This uses the parser proposed in [OpenRewrite PR #9220](https://github.com/openrewrite/rewrite/pull/9220),
commit `8d77476afc1a9f50f044ba0cf744f6a0c91b073e`, compiled with public
OpenRewrite 8.90.4 core. It does not describe stock OpenRewrite support. The
parser benchmark and its build limitation are documented in
[`../samigo-openrewrite-parser/README.md`](../samigo-openrewrite-parser/README.md).

## Verification

Run from the repository root with Java 21, Maven, Python 3, and Node/npm available.
The application dependencies must already be installed, as for a normal
standalone Samigo app build.

```sh
mvn -B -ntp -f samigo/samigo-app/pom.xml -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile=target/jsp-cleanup-classpath.txt
mvn -B -ntp -f build-tools/samigo-jsp-cleanup/pom.xml \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile=target/jasper-classpath.txt
python3 build-tools/samigo-jsp-cleanup/verify.py \
  --jasper-classpath build-tools/samigo-jsp-cleanup/target/jasper-classpath.txt \
  --app-classpath samigo/samigo-app/target/jsp-cleanup-classpath.txt
mvn -B -ntp -f e2e-tests/pom.xml -Dtest=SamigoScriptAssetsTest test
mvn -B -ntp -f samigo/samigo-app/pom.xml -DskipTests package
```

The verifier performs three distinct checks:

1. Each JSP and JS asset exactly matches the bounded extraction from the baseline.
2. Acorn 8.15.0 parses both the original inline script and the extracted asset.
   Their complete ASTs match after removing only source offsets. Literal values,
   names, and program structure remain significant.
3. Offline Jasper 10.1.60 compiles all five views before and after, including
   fragments through static-include wrappers with the real tag libraries and
   application's `web.xml` prelude. Generated Java matches after applying only
   the expected script replacement, merging adjacent literal `out.write` chunks,
   and normalizing JSP source coordinates. Component IDs, generated tag ordinals,
   bindings, actions, types, and calls remain significant. A changed-EL control
   verifies that normalization does not erase binding changes.

Jasper uses the same staging and generated-output paths for both versions, and
a fresh compiler for each translation unit, so dependency cache ordering and
path-derived JSP identifiers do not create false differences. Artifacts and
logs go under `target/jsp-cleanup-verification`. This invokes the compiler only;
it does not launch Tomcat or Sakai.

The three Playwright checks use a real Chromium browser, the production assets,
and rendered-markup fixtures. They verify that resizing does not reload the PDF
preview, changing print settings submits the correct button, attachment buttons
dispatch to the hidden JSF link without submitting their form, and submission
review calls the matching link handler. These are browser checks, not full Sakai
end-to-end tests. The question-search dialog and image-map behavior have AST and
Jasper checks but still need live flow coverage.

The app compiled and packaged successfully; the WAR contains all five changed
JSPs and all five JS assets byte-for-byte. Full live Sakai flows were unavailable
because `https://sakai.example` returned HTTP 502. No server was started or restarted.

## Remaining blockers

The three remaining failures are after the extracted script blocks: JSP/JSF
output embedded inside HTML attributes, a Java request expression inside
`body.onload`, and literal HTML inside a JSF attribute. Source cleanup needs to
handle those constructs explicitly. Escaping literal `<` to `&lt;` in a standard
JSP custom-tag attribute is not a safe mechanical fix: a separate Jasper probe
showed it changes the string passed to the tag handler.

This pilot supports selective cleanup before a migration recipe. It does not
establish Facelets compatibility or runtime equivalence between JSP and Facelets.
Further changes should address one syntax family at a time and retain these
compiler and browser checks.
