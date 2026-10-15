# Samigo OpenRewrite parser coverage

This standalone, read-only Maven tool measures whether OpenRewrite can fully parse
Samigo's original JSP sources and print them byte for byte. It does not migrate
pages, expand includes, normalize markup, run recipes, or start Sakai/Tomcat.
It is intentionally outside Sakai's reactor and does not change its dependencies.

## Run

Use JDK 21 and run from the repository root:

```sh
mvn -B -ntp -f build-tools/samigo-openrewrite-parser/pom.xml test exec:java
```

The report is written to `target/coverage.json` under this directory. Every input
has a SHA-256 digest, result type, byte comparison, parsed root and element count,
unparsed trailing content count/preview, and parser diagnostics. At most five
diagnostics per input are included, with each message capped at 2,000 characters;
`diagnosticCount` includes all diagnostics. The separate parse-error marker is
also capped at 2,000 characters. The process succeeds when the measurement
completes; individual JSP failures are recorded in the report.

All `.jsp`, `.jspx`, `.jspf`, and `.inc` files under `samigo/` are discovered,
excluding `target/` directories. Fragments with unsupported extensions receive
an additional, separately reported probe using an in-memory `.jsp` filename.
Those probes use the exact original bytes, without a synthetic enclosing root.

## Baseline measured on October 15, 2026

- Sakai upstream/master: `d4792fb3a1d398846f2a605dfdbfad8b42efa9e7`.
- Samigo working tree: clean; 245 JSPs and five other fragments.
- OpenRewrite `rewrite-xml`: **8.90.4**, pinned in the POM; JDK 21.0.3.
- Full per-file evidence: [baseline/coverage.json](baseline/coverage.json).
- Ten harness/control/reproduction tests pass.

| Original JSP result | Files | Percentage |
| --- | ---: | ---: |
| Fully parsed and byte-identical | 14 | 5.7% |
| Document returned, but content left unparsed | 180 | 73.5% |
| ParseError returned | 51 | 20.8% |
| Total | 245 | 100% |

All 245 results print the original bytes. **That alone is not parser coverage.**
`ParseError` intentionally preserves its input, and `Xml.Document` can retain
arbitrary unparsed source in its `eof` field. A success requires an
`Xml.Document`, zero parser diagnostics, byte-identical `printAllAsBytes()`, and
only whitespace in `eof`. Of the 180 incomplete documents, **110 have no parsed
root at all**. The counts are file coverage, not an estimate of migration effort.

All five `.jspf`/`.inc` files are rejected by the native extension filter. With
only their in-memory filenames changed, one fully parses, three retain unparsed
content, and one returns a parse error. These are not added to the JSP success
count.

## First visible blockers

These groups classify the first visible blocker, not every defect in a file.
Fixing one can reveal additional blockers; the counts do not predict how many
files a single change will make fully parseable.

| Incomplete document: start of unparsed suffix | Files |
| --- | ---: |
| DOCTYPE after JSP directives | 99 |
| Further markup after the parsed root/prolog | 53 |
| HTML comment followed by further source | 19 |
| JSP code, directive, or comment outside the parsed root/prolog | 9 |

| Parse error: first visible source construct | Files |
| --- | ---: |
| JSP directive inside markup | 36 |
| Literal HTML, JSP expressions, or nested tags inside attributes | 10 |
| DOCTYPE inside `f:verbatim` | 3 |
| Raw JavaScript containing markup or `<` | 2 |

Examples:

- `jsf/author/createAssessment_title.jsp`: the DOCTYPE follows taglib directives;
  no page root is parsed, and a 5,295-character unparsed suffix is retained.
- `jsf/author/allHeadings.jsp:86`: an include directive occurs inside a panel.
- `jsf/author/item/rubricAssociation.jsp:4`: a JSF output tag supplies an HTML
  attribute value; conditional component tags also occur inside the opening tag.
- `jsf/delivery/login.jsp:6`: a DOCTYPE occurs inside `f:verbatim`.
- `jsf/author/searchResults.jsp:40`: a JavaScript string contains HTML markup.
- `jsf/evaluation/gradeStudentResultAttachment.jsp:28`: a raw JavaScript loop
  contains `<`.

Paths in these examples are relative to `samigo/samigo-app/src/webapp/`.
Small reproductions are in `JspParserCoverageTest`, alongside positive controls
and checks preventing false positives from ParseError and unparsed EOF content.
They document 8.90.4 behavior; they are not tests of a completed conversion recipe.

## Version limitation

OpenRewrite 8.93.8 was released on the measurement date. Its POM was accessible,
but Maven received HTTP 401 for its JAR, core JAR, and required dependencies from
`https://artifacts.codegenomeproject.org/maven`. The run therefore uses 8.90.4,
the newest version listed in Maven Central metadata at measurement time.
No 8.93.8 coverage result is claimed.

The tagged `XMLLexer.g4` and `XMLParser.g4` files are identical between 8.90.4
and 8.93.8; `XmlParser.java` and `XmlParserVisitor.java` differ. Those source
comparisons do not substitute for running 8.93.8.

## Recommended next work

Start with parser correctness: detect non-whitespace unconsumed input instead
of silently returning an apparently successful document. Then support JSP
prolog interleaving, include directives in content, and fragments with multiple
top-level nodes. Attribute syntax and script bodies need their own fixtures.
These are parser prerequisites for a structural JSP-to-Facelets recipe.

Preserve this baseline and rerun the same inputs after each parser improvement.
Source IDs, bean expressions, and includes should remain untouched during these
measurements. Parsing and byte preservation do not establish JSF lifecycle or
rendered-behavior equivalence.
