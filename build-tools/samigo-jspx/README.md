# Standard JSP to validating JSPX

`org.sakaiproject.buildtools.jspx.JspToJspx` is an executable OpenRewrite recipe
that converts standard JSP source to XML-syntax JSP (`.jspx`). It retains the JSP
runtime, JSF tags, friendly IDs, binding expressions, listeners, and include
boundaries. It does not convert to Facelets.

The input is parsed by pinned **Jasper 10.1.60**, using the application's real tag
libraries, classes, `web.xml`, and includes. This avoids requiring sloppy JSP
template HTML to parse as XML before it can be converted. The recipe uses stock
**OpenRewrite 8.90.4** and does not require the experimental JSP parser patches.

The serializer follows [JSP 3.1 chapters 6 and 10](https://jakarta.ee/specifications/pages/3.1/jakarta-server-pages-spec-3.1.pdf):

| Standard JSP construct | JSPX representation |
| --- | --- |
| Taglib directive | Namespace on `jsp:root` |
| Page/include directive | `jsp:directive.page` / `jsp:directive.include` |
| Declaration, scriptlet, expression | `jsp:declaration`, `jsp:scriptlet`, `jsp:expression` |
| JSF/JSTL/custom tag | Original tag, attributes, and children |
| Literal HTML, JavaScript, and whitespace | `jsp:text`, with escaped CDATA boundaries |
| JSP comment | XML comment, preserving the comment text except illegal `--` sequences |

For example, the original image-map fragment contains:

```jsp
<img src='<h:outputText value="#{question.imageMapSrc}"/>'>
```

Its JSPX representation keeps the same render-time JSF component:

```xml
<jsp:text><![CDATA[<img src=']]></jsp:text>
<h:outputText value="#{question.imageMapSrc}"/>
<jsp:text><![CDATA['>]]></jsp:text>
```

The document is valid XML even though HTML is emitted in separate pieces. This
validates the JSP structure and namespace bindings; it **does not validate HTML
inside `jsp:text`**. Converting that template text to structured, well-formed HTML
is a separate transformation, with its own behavior checks.

## Validation built into the recipe

Before returning a changed source file, the recipe:

1. Compiles the original JSP through Jasper, including Java compilation.
2. Serializes Jasper's source AST, preserving original names. It never reads
   generated servlet method or parameter names to construct JSPX.
3. Checks namespace-aware XML well-formedness with external entities disabled.
4. Compiles the JSPX through Jasper, including Java compilation. Include fragments
   are validated through a JSP parent with their supplied taglib context.
5. Parses and byte-round-trips the result as an OpenRewrite `Xml.Document`.

Failures leave the original input in the OpenRewrite changeset with a diagnostic;
the CLI refuses to write an output file for that failure. Existing `.jspx` targets
are never overwritten. The recipe is idempotent for converted `.jspx` sources.

Full pages materialize their original automatic preludes/codas as explicit
includes because Samigo's `*.jsp` configuration does not match `*.jspx`.
Fragments leave that responsibility with their parent. A target that also
matches automatic prelude/coda configuration is rejected to prevent duplication.

Tomcat's XML JSP parser enumerates attributes in reverse order. This adapter
reverses their XML spelling so the generated setter calls retain the original
order on the pinned compiler. Changing compiler version or XML formatting needs
the generated-code check below; setter order is not discarded by that check.

## Run the verified Samigo pilot

Use JDK 21, Maven, Python 3, and the normal installed Sakai dependencies. From
the repository root:

```sh
mvn -B -ntp -f samigo/samigo-app/pom.xml -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile=target/jspx-app-classpath.txt
mvn -B -ntp -f build-tools/samigo-jspx/pom.xml -P samigo-pilot verify
```

The Maven profile runs unit tests and `verify.py`. It takes the five **original**
JSPs from commit `a2f52bae02d`, including their inline scripts, and writes previews
under `target/jspx-verification/converted/`. It does not edit application sources
or start Tomcat/Sakai. A copy of the application's webapp is used for compilation.

Each original JSP and converted JSPX is compiled using the same generated class
name and output path. Include fragments use equivalent parent wrappers. Generated
Java must match after normalizing only:

- Source filenames/line/column coordinates in Jasper diagnostics.
- Dependency-file metadata and timestamps.
- Adjacent literal `out.write` chunk boundaries.

IDs, bindings, listeners, types, calls, **setter order**, scriptlets, and output
strings remain significant. Changed-binding and changed-ID controls verify that
normalization does not erase those differences. Separate fixtures exercise
escaped EL in template text and attributes, entities, CRLF, Unicode, and `]]>`.

Logs, generated Java, diffs, and machine-readable results are under
`target/jspx-verification/`. The checked-in [pilot results](results/pilot.json)
record source/output hashes. A complete converted
[image-map fragment](src/test/resources/examples/displayImageMapQuestion.jspx)
is included for inspection and XML validation during unit tests.

## Recipe inputs and preview CLI

The recipe accepts source files with webapp-relative paths and four options:

- `webappDirectory`: source webapp directory, for includes and configuration.
- `classpath`: platform-separated dependency JARs and app class directories.
- `taglibs`: inherited prefix-to-URI mappings for fragments; empty for self-contained pages.
- `fragment`: whether the source is an include fragment rather than a translation unit.

The CLI invokes the actual OpenRewrite recipe, not a separate conversion script:

```sh
java -cp "$JSPX_TOOL_CLASSPATH" org.sakaiproject.buildtools.jspx.ConvertJsp \
  /path/to/webapp jsf/example.jsp /tmp/example.jspx /path/to/app-classpath.txt \
  false
```

For fragments use `true` and append a JSON file containing the inherited taglib
mappings. The pilot generates its complete runtime classpath and a Samigo taglib
map under `target/jspx-verification/` for reproducible examples.

## Full Samigo conversion census

After building the module and application classpath as above, run:

```sh
python3 build-tools/samigo-jspx/census.py --workers 4
```

The census takes a complete Git snapshot of the original webapp at `a2f52bae02d`
and runs the actual recipe independently on all **245 JSPs**. Application sources
are unchanged. Previews, compiler logs, and results go under `target/jspx-census/`.
The [recorded full-corpus results](results/full-corpus.json) contain each source
hash, conversion mode, include parents, outcome, and failure diagnostic.

| Conversion mode | Passed | Total |
| --- | ---: | ---: |
| Full pages | 115 | 128 |
| Include fragments | 117 | 117 |
| All JSPs | **232** | **245** |

Passing requires namespace-aware XML validation, original and converted Jasper
Java compilation, and an OpenRewrite XML round-trip. Include-only files are
tested as fragments with the corpus's nonconflicting taglib mappings; parent
Java variables are not synthesized. Unreferenced files remain full pages.

The **13 failures** comprise six unsupported whitespace-trimming cases, five
pages requiring explicit response-charset normalization, and two pre-existing
source failures: malformed EL in `author/previewAssessment.jsp` and a missing
include in `delivery/reviewAssessment.jsp`.

This is per-file validity coverage. Includes still target original JSPs; the
census does not test an application in which every include has been renamed,
compare generated Java across the full corpus, or validate literal HTML.
The four JSPF files and `header.inc` are dependencies, not conversion targets.

## Current boundaries

This is a tested syntax-normalization recipe and preview workflow, not a
repository-wide deployment recipe. Include references intentionally retain their
existing `.jsp` targets. Applying renames across a whole application still needs
a batch step that updates references only after their targets convert, plus JSF
view mappings, security constraints, and matching JSP configuration. The CLI
leaves running application files unchanged while those changes are prepared.

Unsupported source constructs fail explicitly. These currently include
tag-dependent bodies, whitespace trimming, unsupported JSP action node kinds,
and conflicting taglib prefixes. A full page without an explicit response
charset is rejected: JSPX otherwise adds a charset and changes the generated
response-header behavior. Encoding normalization must be an explicit preceding
step rather than an accidental effect of this recipe.

The prototype uses Jasper internals through one adapter pinned to 10.1.60.
Compiler comparisons cover the listed five pages and fixtures, not every Sakai
flow or arbitrary container. The separate census measures per-file conversion
validity. No live deployment or complete application migration is claimed.
