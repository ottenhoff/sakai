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

## First parser implementation: October 16, 2026

The selected root is **`Xml.JspDocument`**, containing an ordered `List<Xml>` of
top-level nodes. JSP pages and include fragments do not require a single root
element. Directives, DOCTYPEs, comments, scriptlets, expressions, text, and
elements retain their original order, without an invented enclosing tag.
Existing XML node types are reused for nested structure.

`JspParser` explicitly accepts `.jsp`, `.jspf`, and `.inc`. The generic XML
parser routes `.jsp`/`.jspf` to the JSP root, but does not claim unrelated `.inc`
files. `.jspx` retains the XML document model. Both document entry points now
require complete token consumption. Parser diagnostics cannot be hidden in a
successful source tree. Legal trailing XML comments/instructions remain valid.

The change includes visitors, printing, RPC codecs, basic document-aware XPath
and formatting, plus a conservative equality check for JSP source. It is an
OpenRewrite parser change; no Samigo template or runtime configuration changes.

| Original JSP result | Unmodified 8.93.8 source | Patched source |
| --- | ---: | ---: |
| Fully parsed, byte-identical | 14 (5.7%) | **82 (33.5%)** |
| Unparsed trailing content | 180 | **0** |
| Explicit parse errors | 51 | 163 |

All 14 previous successes remain successful. Of the 180 incomplete documents,
49 fully parse and 131 now report failures. Another 19 previous parse errors
fully parse. Of the five additional fragments, four fully parse and one fails.
The unchanged 250-file corpus therefore has 86 fully parsed sources overall.

The increased error count exposes source that was previously retained as opaque
text. Remaining first errors are
mainly quoted attributes containing JSP expressions or embedded markup, raw
script bodies, and template markup that is not structurally nested.

[Per-file comparison](results/jsp-document-comparison.json) records input hashes,
before/after statuses, parsed element counts, and the first diagnostic. Full
diagnostics are regenerated into the ignored `target/` directory.

### Source and validation provenance

- OpenRewrite source base: tag `v8.93.8`, commit
  `22a5e8aa29dbc465ab4f00882c585f8d9e9e70b7`.
- Implementation branch: `codex/jsp-document-parser`, commit
  `295617a269afe483843676790d8f253b9e9c7b78`.
- Portable contribution: [0001-jsp-document-root.patch](patches/0001-jsp-document-root.patch).
- Current-main draft contribution: [OpenRewrite #9220](https://github.com/openrewrite/rewrite/pull/9220).
- **346 XML module tests pass**, including 17 new JSP root tests and a new RPC
  test; the unmodified source control passes its 328 tests.
- Validation compiles the XML module's Java sources, resources, and tests using
  this standalone Maven profile with public **8.90.4 core** dependencies. The
  current project's Gradle build and the full 8.93.8 dependency graph have not
  been validated. The same dependency graph was used for the unmodified-source
  control, which reproduced the original 14/245 baseline.

Apply and measure from this Sakai repository root:

```sh
git clone --depth 1 --branch v8.93.8 https://github.com/openrewrite/rewrite.git /tmp/rewrite-jsp
git -C /tmp/rewrite-jsp switch -c jsp-document-parser
git -C /tmp/rewrite-jsp am "$PWD/build-tools/samigo-openrewrite-parser/patches/0001-jsp-document-root.patch"
mvn -B -ntp -f build-tools/samigo-openrewrite-parser/pom.xml \
    -Dopenrewrite.source.dir=/tmp/rewrite-jsp/rewrite-xml test exec:java
```

The source profile has its own `target/openrewrite-source/` output directory.
The original binary baseline still uses `target/coverage.json`. No external
OpenRewrite checkout is needed for the original baseline command.

The patch was also applied to current upstream `main` at
`f0a034098a6f5246d5d112096b2d3d1dc0be633d`, producing commit
`8d77476afc1a9f50f044ba0cf744f6a0c91b073e` on
`codex/jsp-document-parser-upstream`. All 346 XML module tests pass on that source
base too, and the same corpus still measures 82/245 JSPs fully parsed, with zero
incomplete documents. This uses the same public 8.90.4 core dependencies. When
measuring a different source revision, set `-Dcoverage.parser.label=...` to record
the correct provenance in the generated report.

## Quoted JSP attributes: October 16, 2026

The second parser change handles literal markup and backslash-quoted delimiters
in standard JSP attributes, plus Java output expressions inside quoted template
HTML attributes. It preserves their original spelling in `Xml.Attribute.Value`,
including EL and Java expression text. These expressions are accessible through
the attribute value; they are not separate Java or JSP-expression child nodes.
XML, JSPX, and HTML keep their existing lexer rules. The quoting distinction
follows [JSP 3.1 sections 1.3.5 and 1.6](https://jakarta.ee/specifications/pages/3.1/jakarta-server-pages-spec-3.1.pdf).

The parser rejects an embedded JSP action in an HTML attribute rather than
hiding that executable component in a string. Custom-tag attributes can contain
literal markup for their tag handler, but their enclosing quote still requires
escaping, including inside a request-time expression. Unterminated scripting
constructs remain errors. This adds a JSP-only lexer mode and keeps the checked-in
lexer token vocabulary synchronized with the generated Java sources.

| Corpus | JSP root patch | Plus quoted attributes |
| --- | ---: | ---: |
| Original, unchanged Samigo JSPs | 82/245 | **165/245 (67.3%)** |
| With the five-script cleanup pilot | 84/245 | **167/245 (68.2%)** |

All 250 original source hashes match the earlier corpus. No previous successful
parse regresses. Across all original sources, including fragments, 169/250 fully
parse; 81 return explicit errors. All successes have zero diagnostics, zero
unparsed trailing content, and byte-identical output. The five-script pilot has
171/250 successes and 79 errors.

**364 XML module tests pass**, including positive and negative controls for JSP
quotes, template Java expressions, literal markup, embedded executable actions,
unterminated attributes, visitor edits, and unchanged XML/JSPX/HTML strictness.
The source build still uses public 8.90.4 core; the full current Gradle dependency
graph remains unvalidated.

- Implementation: `11ebb8ece1c57ae3aa9347edf41080b71c41ee39`, following
  `8d77476afc1a9f50f044ba0cf744f6a0c91b073e` on the current-main contribution branch.
- Portable follow-up: [0002-jsp-quoted-attributes.patch](patches/0002-jsp-quoted-attributes.patch).
- [Per-file comparison](results/jsp-quoted-attributes-comparison.json).
- This step changes no Samigo application source. The earlier
  [five-script cleanup pilot](../samigo-jsp-cleanup/README.md) remains separate.

After applying patch 0001 using the commands above, apply patch 0002 and measure:

```sh
git -C /tmp/rewrite-jsp am "$PWD/build-tools/samigo-openrewrite-parser/patches/0002-jsp-quoted-attributes.patch"
mvn -B -ntp -f build-tools/samigo-openrewrite-parser/pom.xml \
    -Dopenrewrite.source.dir=/tmp/rewrite-jsp/rewrite-xml \
    '-Dcoverage.parser.label=JSP root plus quoted attributes; 8.90.4 core' test exec:java
```

### Next parser work

Represent executable JSP actions inside HTML attributes as structured nodes,
then handle raw script bodies and template output crossing element boundaries.
For example, `submitted.jsp` writes an opening `div` through `h:outputText` and
its closing tag as template text. A static XML element tree cannot pair those
correctly. The three remaining pilot pages continue to fail explicitly on these
constructs. Preserve IDs, bindings, and source bytes throughout. Parsing and byte
preservation do not establish JSF lifecycle or rendered-behavior equivalence.
