#!/usr/bin/env python3
"""Convert original Samigo JSPs, compile JSP/JSPX, and compare generated Java."""
import ast
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).resolve().parent
WORK = ROOT / 'target/jspx-verification'
WEBAPP = Path('samigo/samigo-app/src/webapp')
BASE = 'a2f52bae02d'
CASES = [
    ('jsf/print/printAssessment.jsp', False),
    ('jsf/evaluation/gradeStudentResultAttachment.jsp', True),
    ('jsf/author/searchResults.jsp', True),
    ('jsf/delivery/submitted.jsp', False),
    ('jsf/evaluation/item/displayImageMapQuestion.jsp', True),
    ('jsf/delivery/getSubmissionStatus.jsp', False),
    ('jsf/delivery/getTimerProgress.jsp', False),
    ('jsf/delivery/getTimerStrings.jsp', False),
    ('jsf/delivery/stopTimerProgress.jsp', False),
    ('jsf/qti/xmlDisplay.jsp', False),
    ('jsf/qti/xmlPoolDisplay.jsp', False),
    ('jsf/author/createAssessment_title.jsp', False),
    ('jsf/author/editLink.jsp', False),
    ('jsf/security/roleCheckStaticInclude.jsp', False),
    ('jsf/upload/closeWindow.jsp', False),
    ('jsf/upload/uploadFile.jsp', False),
]
UTF8_NORMALIZED = {
    'jsf/author/createAssessment_title.jsp', 'jsf/author/editLink.jsp',
    'jsf/security/roleCheckStaticInclude.jsp', 'jsf/upload/closeWindow.jsp',
    'jsf/upload/uploadFile.jsp',
}
CONTROLS = {
    'controls/escaped-el.jsp': r'<%@ page deferredSyntaxAllowedAsLiteral="true" %>\${name} / \\${name} / ${name} / #{literal}',
    'controls/attribute-escaping.jsp': r'<h:outputText id="friendly" escape="false" value="literal \${name} &lt;span&gt; &amp; &#10;"/>',
    'controls/cdata-and-whitespace.jsp': '<%@ page contentType="text/html;charset=UTF-8" %>\r\n<script>if (1 < 2 && true) { console.log("]]>é"); }</script>\r\n',
    'controls/trimmed-whitespace.jsp': '<%@ page trimDirectiveWhitespaces="true" %>\r\n \t<%-- comment --%>\r\n<% String value = "ok"; %>\n<%= value %>\r\n',
    'controls/trimmed-mixed-text.jsp': '<%@ page trimDirectiveWhitespaces="true" %>\r\n<% String value = "ok"; %> \t {"message":"<%= value %>"} \r\n',
}
TAGLIBS = {
    'h': 'http://java.sun.com/jsf/html', 'f': 'http://java.sun.com/jsf/core',
    't': 'http://myfaces.apache.org/tomahawk', 'samigo': 'http://www.sakaiproject.org/samigo',
    'c': 'http://java.sun.com/jsp/jstl/core',
}
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
WRITE = re.compile(r'''out\.write\(("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')\);''')
WRITES = re.compile(r'(?m)(?:^[ \t]*' + WRITE.pattern + r'\n)+')


def canonical(source):
    def writes(match):
        value = ''.join(ast.literal_eval(item[1]) for item in WRITE.finditer(match[0]))
        return '      out.write(' + json.dumps(value, ensure_ascii=False) + ');\n'
    source = WRITES.sub(writes, source)
    source = re.sub(r'\.jspx?\(\d+,\d+\)', '.jsp(L,C)', source)
    # Dependency timestamps/paths describe translation inputs, not request behavior.
    source = re.sub(r'(?m)^.*_jspx_dependants\.put\(.*\n', '', source)
    source = re.sub(r'(_jspx_dependants = new java.util.LinkedHashMap<[^\n]+>)\(\d+\)', r'\1(0)', source)
    return source


def run(command, logfile):
    with logfile.open('w') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    app = WORK / 'webapp'
    if app.exists():
        shutil.rmtree(app)
    shutil.copytree(ROOT / WEBAPP, app)
    for view, _ in CASES:
        original = subprocess.check_output(['git', 'show', f'{BASE}:{WEBAPP / view}'], cwd=ROOT)
        (app / view).write_bytes(original)
    declarations = ''.join(f'<%@ taglib prefix="{p}" uri="{u}" %>' for p, u in TAGLIBS.items())
    for view, content in CONTROLS.items():
        (app / view).parent.mkdir(exist_ok=True)
        (app / view).write_text(declarations + '<%@ page contentType="text/html;charset=UTF-8" %>' + content)
    cp = os.pathsep.join([
        str(TOOL / 'target/classes'), (TOOL / 'target/classpath.txt').read_text().strip(),
        str(ROOT / 'samigo/samigo-app/target/classes'),
        (ROOT / 'samigo/samigo-app/target/jspx-app-classpath.txt').read_text().strip(),
    ])
    cpfile = WORK / 'runtime-classpath.txt'
    cpfile.write_text(cp)
    taglibs = WORK / 'taglibs.json'
    taglibs.write_text(json.dumps(TAGLIBS))
    libraries = app / 'WEB-INF/lib'
    libraries.mkdir(exist_ok=True)
    for entry in cp.split(os.pathsep):
        path = Path(entry)
        if path.suffix == '.jar' and not (libraries / path.name).exists():
            (libraries / path.name).symlink_to(path)
    results = []
    failures = []
    for index, (view, fragment) in enumerate(CASES + [(name, False) for name in CONTROLS]):
        converted = WORK / 'converted' / Path(view).with_suffix('.jspx')
        run([JAVA, '-cp', cp, 'org.sakaiproject.buildtools.jspx.ConvertJsp', str(app), view,
             str(converted), str(cpfile), str(fragment).lower(), str(taglibs), 'UTF-8'], WORK / f'convert-{index}.log')
        target = app / Path(view).with_suffix('.jspx')
        shutil.copyfile(converted, target)
        generated = WORK / 'generated'
        outputs = []
        for version in ('before', 'after'):
            if generated.exists():
                shutil.rmtree(generated)
            generated.mkdir()
            if fragment:
                include = view if version == 'before' else str(Path(view).with_suffix('.jspx'))
                (app / 'comparison.jsp').write_text(declarations + f'<%@ include file="/{include}" %>')
                page = 'comparison.jsp'
            else:
                page = view if version == 'before' else str(Path(view).with_suffix('.jspx'))
            run([JAVA, '-cp', cp, 'org.apache.jasper.JspC', '-uriroot', str(app), '-d', str(generated),
                 '-c', 'Comparison', '-compile', '-die', page], WORK / f'compile-{index}-{version}.log')
            files = list(generated.rglob('Comparison.java'))
            assert len(files) == 1, files
            java = files[0].read_text()
            (WORK / f'java-{index}-{version}.java').write_text(java)
            outputs.append(canonical(java))
        same = outputs[0] == outputs[1]
        expected = outputs[0]
        charset_change = None
        if view in UTF8_NORMALIZED:
            before_header = 'response.setContentType("text/html");'
            after_header = 'response.setContentType("text/html;charset=UTF-8");'
            assert expected.count(before_header) == 1, view
            expected = expected.replace(before_header, after_header, 1)
            charset_change = {'from': 'text/html', 'to': 'text/html;charset=UTF-8'}
        expected_match = expected == outputs[1]
        original_java = (WORK / f'java-{index}-before.java').read_text()
        if 'getELContext(),"#{' in original_java:
            changed_binding = original_java.replace('getELContext(),"#{', 'getELContext(),"#{changed.', 1)
            assert canonical(changed_binding) != outputs[0]
        if '.setId("' in original_java:
            assert canonical(original_java.replace('.setId("', '.setId("changed-', 1)) != outputs[0]
        if not expected_match:
            (WORK / f'comparison-{index}.diff').write_text(''.join(difflib.unified_diff(
                expected.splitlines(True), outputs[1].splitlines(True), fromfile='expected JSP', tofile='JSPX')))
            failures.append(view)
        results.append({'view': view, 'fragment': fragment, 'xmlAndOpenRewriteParse': True,
                        'sourceSha256': hashlib.sha256((app / view).read_bytes()).hexdigest(),
                        'jspxSha256': hashlib.sha256(converted.read_bytes()).hexdigest(),
                        'jasperCompiledBeforeAndAfter': True, 'generatedJavaMatches': same,
                        'intentionalResponseCharsetChange': charset_change,
                        'generatedJavaMatchesExpectedChanges': expected_match})
        print(view, ('MATCH' if same else 'MATCH WITH EXPLICIT UTF-8 HEADER CHANGE')
              if expected_match else 'DIFFERENCE', flush=True)
    (WORK / 'results.json').write_text(json.dumps({'baseCommit': BASE, 'results': results}, indent=2) + '\n')
    if failures:
        raise AssertionError(f'Inspect generated Java differences for {failures}')


if __name__ == '__main__':
    main()
