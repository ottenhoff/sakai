#!/usr/bin/env python3
"""Compare generated Java for every successful conversion in the Samigo census."""
import argparse
import ast
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

from census import diagnostic
from verify import canonical

ROOT = Path(__file__).resolve().parents[2]
HEADER = re.compile(r'response\.setContentType\(("(?:[^"\\]|\\.)*")\);')


def expected_java(before, fragment, charset):
    """Allow exactly the response-charset normalization selected for conversion."""
    if not fragment and charset:
        matches = list(HEADER.finditer(before))
        if len(matches) != 1:
            raise ValueError('Expected exactly one generated response content-type call')
        match = matches[0]
        content_type = ast.literal_eval(match[1])
        if 'charset=' not in content_type.lower():
            normalized = content_type + ';charset=' + charset
            call = 'response.setContentType(' + json.dumps(normalized) + ');'
            return before[:match.start()] + call + before[match.end():], {
                'from': content_type, 'to': normalized,
            }
    return before, None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--census', type=Path, default=ROOT / 'target/jspx-census')
    parser.add_argument('--workers', type=int, default=4)
    args = parser.parse_args()
    if args.workers < 1:
        parser.error('--workers must be positive')
    census = args.census.resolve()
    report_file = census / 'results.json'
    conversion = json.loads(report_file.read_text())
    cases = conversion['results']
    if len(cases) != conversion['totalJspFiles'] or not all(case['validatingConversionPassed'] for case in cases):
        raise ValueError('A complete, entirely successful conversion census is required')
    snapshot = census / 'snapshot/samigo/samigo-app/src/webapp'
    cp = (census / 'runtime-classpath.txt').read_text().strip()
    declarations = ''.join(f'<%@ taglib prefix="{prefix}" uri="{uri}" %>'
                           for prefix, uri in json.loads((census / 'taglibs.json').read_text()).items())
    for name, digest in conversion['recipeClassFilesSha256'].items():
        if hashlib.sha256((census / 'recipe-classes' / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Compiler snapshot changed since conversion: ' + name)
    work = ROOT / 'target/jspx-comparison'
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'

    def compare(case):
        started = time.monotonic()
        view = Path(case['view'])
        fragment = case['mode'] == 'fragment'
        candidate = census / 'converted' / view.with_suffix('.jspx')
        for path, expected in [(snapshot / view, case['sourceSha256']), (candidate, case['jspxSha256'])]:
            if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise ValueError('Input changed since conversion: ' + str(path))
        folder = work / 'cases' / view.with_suffix('')
        app = folder / 'webapp'
        shutil.copytree(snapshot, app, copy_function=lambda source, target: os.symlink(Path(source).resolve(), target))
        libraries = app / 'WEB-INF/lib'
        libraries.mkdir(exist_ok=True)
        for entry in cp.split(os.pathsep):
            path = Path(entry)
            if path.suffix == '.jar' and not (libraries / path.name).exists():
                (libraries / path.name).symlink_to(path)
        (app / view.with_suffix('.jspx')).symlink_to(candidate)
        generated = folder / 'generated'
        outputs = []
        result = {'view': str(view), 'mode': case['mode'], 'sourceSha256': case['sourceSha256'],
                  'jspxSha256': case['jspxSha256'], 'comparisonPassed': False}
        for version in ('before', 'after'):
            if generated.exists():
                shutil.rmtree(generated)
            page = str(view if version == 'before' else view.with_suffix('.jspx'))
            if fragment:
                wrapper = app / '__jspx_comparison.jsp'
                if wrapper.is_symlink():
                    raise ValueError('Comparison wrapper would overwrite a source file')
                wrapper.write_text(declarations + f'<%@ include file="/{page}" %>')
                page = wrapper.name
            log = folder / f'compile-{version}.log'
            try:
                with log.open('w') as stream:
                    process = subprocess.run([java, '-Xmx512m', '-cp', cp, 'org.apache.jasper.JspC',
                                              '-uriroot', str(app), '-d', str(generated), '-c', 'Comparison',
                                              '-compile', '-die', '-source', '17', '-target', '17', page],
                                             stdout=stream, stderr=subprocess.STDOUT, timeout=120)
                if process.returncode:
                    result['diagnostic'] = version + ': ' + diagnostic(log.read_text())
                    return result
            except subprocess.TimeoutExpired:
                result['diagnostic'] = version + ': compilation exceeded 120 seconds'
                return result
            files = list(generated.rglob('Comparison.java'))
            if len(files) != 1:
                raise ValueError('Expected one generated comparison class for ' + str(view))
            source = files[0].read_text()
            (folder / f'java-{version}.java').write_text(source)
            outputs.append(canonical(source))
        expected, change = expected_java(outputs[0], fragment, conversion['defaultResponseCharset'])
        result.update({'generatedJavaMatches': outputs[0] == outputs[1],
                       'intentionalResponseCharsetChange': change,
                       'comparisonPassed': expected == outputs[1],
                       'beforeCanonicalSha256': hashlib.sha256(outputs[0].encode()).hexdigest(),
                       'afterCanonicalSha256': hashlib.sha256(outputs[1].encode()).hexdigest(),
                       'seconds': round(time.monotonic() - started, 2)})
        # Negative controls retain IDs and bindings as significant differences.
        before = (folder / 'java-before.java').read_text()
        for token, changed in [('.setId("', '.setId("changed-'),
                               ('getELContext(),"#{', 'getELContext(),"#{changed.')]:
            if token in before and canonical(before.replace(token, changed, 1)) == outputs[0]:
                raise AssertionError('Comparison erased an ID or binding change in ' + str(view))
        if not result['comparisonPassed']:
            (folder / 'comparison.diff').write_text(''.join(difflib.unified_diff(
                expected.splitlines(True), outputs[1].splitlines(True), fromfile='expected JSP', tofile='JSPX')))
        shutil.rmtree(app)
        shutil.rmtree(generated)
        return result

    results = []
    report = {'baseCommit': conversion['baseCommit'], 'totalJspFiles': len(cases),
              'conversionReportSha256': hashlib.sha256(report_file.read_bytes()).hexdigest(),
              'jasperVersion': conversion['jasperVersion'], 'generatedJavaSourceTarget': '17',
              'scope': 'Per-file conversion with original .jsp include targets; excludes full include renaming.',
              'normalization': ['Jasper source coordinates', 'dependency paths, timestamps, and map capacity',
                                'adjacent literal out.write chunk boundaries'],
              'allowedBehaviorChanges': 'Only the explicitly selected default response charset on pages lacking one.'}
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(compare, case) for case in cases]
        for future in as_completed(futures):
            result = future.result()
            results.append(result)
            category = lambda item: ('failed' if not item['comparisonPassed'] else
                                     'charsetChange' if item['intentionalResponseCharsetChange'] else 'strictMatch')
            report['summary'] = dict(Counter(category(item) for item in results))
            report['results'] = sorted(results, key=lambda item: item['view'])
            (work / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
            print(f"{len(results)}/{len(cases)} {category(result)} {result['view']}", flush=True)
    if any(not result['comparisonPassed'] for result in results):
        raise SystemExit('Generated-Java comparison failed; inspect target/jspx-comparison/')


if __name__ == '__main__':
    main()
