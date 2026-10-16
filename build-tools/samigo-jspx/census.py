#!/usr/bin/env python3
"""Measure actual recipe conversion over an unchanged, pinned Samigo webapp."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import io
import json
import os
from pathlib import Path
import posixpath
import re
import shutil
import subprocess
import tarfile
import time

ROOT = Path(__file__).resolve().parents[2]
TOOL = Path(__file__).resolve().parent
WEBAPP = Path('samigo/samigo-app/src/webapp')
BASE = 'a2f52bae02d'


def uncomment(source):
    return re.sub(r'<%--.*?--%>', '', source, flags=re.S)


def diagnostic(log):
    """Retain the causal diagnostic, with complete compiler output in the log."""
    causes = re.findall(r'^Caused by: (.+)', log, flags=re.M)
    causes = [cause for cause in causes
              if not cause.startswith(('Generation completed with',
                                       'java.lang.IllegalStateException: JSPX conversion failed for'))]
    message = causes[-1] if causes else '\n'.join(log.splitlines()[-12:])
    return re.sub(r'file:/[^\s]*?/jspx-conversion-[^/]+/webapp/', 'webapp/', message)


def refresh_report(report, work):
    for result in report['results']:
        if not result['validatingConversionPassed']:
            log = work / 'logs' / Path(result['view']).with_suffix('.log')
            if result['diagnostic'] != 'Conversion exceeded 120 seconds':
                result['diagnostic'] = diagnostic(log.read_text())
            message = result['diagnostic']
            if 'explicit response charset' in message:
                result['failureCategory'] = 'response-charset-required'
            elif 'trimDirectiveWhitespaces needs' in message:
                result['failureCategory'] = 'whitespace-trimming-unsupported'
            elif 'contains invalid expression(s)' in message:
                result['failureCategory'] = 'invalid-source-el'
            elif 'JSP file [' in message and 'not found' in message:
                result['failureCategory'] = 'missing-source-include'
            else:
                result['failureCategory'] = 'unclassified'
    report['summary'] = dict(Counter('passed' if item['validatingConversionPassed'] else 'failed'
                                    for item in report['results']))
    report['failureCategories'] = dict(sorted(Counter(item['failureCategory'] for item in report['results']
                                                     if not item['validatingConversionPassed']).items()))
    (work / 'results.json').write_text(json.dumps(report, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default=BASE)
    parser.add_argument('--workers', type=int, default=4)
    args = parser.parse_args()
    if args.workers < 1:
        parser.error('--workers must be positive')
    work = ROOT / 'target/jspx-census'
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    # A complete Git snapshot avoids depending on pilot edits or dirty sources.
    archive = subprocess.check_output(['git', 'archive', args.base, str(WEBAPP)], cwd=ROOT)
    with tarfile.open(fileobj=io.BytesIO(archive)) as files:
        files.extractall(work / 'snapshot', filter='data')
    app = work / 'snapshot' / WEBAPP
    sources = {str(path.relative_to(app)): uncomment(path.read_text())
               for path in sorted(app.rglob('*'))
               if path.is_file() and path.suffix in ('.jsp', '.jspf', '.inc')}
    includes = defaultdict(list)
    taglibs = {}
    for view, source in sources.items():
        for target in re.findall(r'<%@\s*include\s+file\s*=\s*[\"\']([^\"\']+)', source):
            target = posixpath.normpath(target.lstrip('/') if target.startswith('/')
                                        else str(Path(view).parent / target))
            includes[target].append(view)
        for declaration in re.findall(r'<%@\s*taglib\b(.*?)%>', source, re.S):
            attributes = dict(re.findall(r'(\w+)\s*=\s*[\"\']([^\"\']+)', declaration))
            prefix, uri = attributes['prefix'], attributes['uri']
            if prefix in taglibs and taglibs[prefix] != uri:
                raise ValueError(f'Conflicting inherited taglib: {prefix}')
            taglibs[prefix] = uri
    taglib_file = work / 'taglibs.json'
    taglib_file.write_text(json.dumps(taglibs, indent=2) + '\n')
    cp = os.pathsep.join([
        str(TOOL / 'target/classes'), (TOOL / 'target/classpath.txt').read_text().strip(),
        str(ROOT / 'samigo/samigo-app/target/classes'),
        (ROOT / 'samigo/samigo-app/target/jspx-app-classpath.txt').read_text().strip(),
    ])
    cpfile = work / 'runtime-classpath.txt'
    cpfile.write_text(cp)
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'

    def convert(view):
        source = sources[view]
        # Include-only files are fragments. Unreferenced files remain full pages;
        # do not use fragment mode to evade response-charset preservation checks.
        fragment = bool(includes[view]) and not re.search(r'<f:view\b', source)
        output = work / 'converted' / Path(view).with_suffix('.jspx')
        log = work / 'logs' / Path(view).with_suffix('.log')
        log.parent.mkdir(parents=True, exist_ok=True)
        command = [java, '-Xmx512m', '-cp', cp, 'org.sakaiproject.buildtools.jspx.ConvertJsp',
                   str(app), view, str(output), str(cpfile), str(bool(fragment)).lower()]
        if fragment:
            command.append(str(taglib_file))
        started = time.monotonic()
        try:
            with log.open('w') as stream:
                process = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=120)
            passed = process.returncode == 0 and output.is_file()
            error = None if passed else diagnostic(log.read_text())
        except subprocess.TimeoutExpired:
            passed, error = False, 'Conversion exceeded 120 seconds'
        return {
            'view': view,
            'mode': 'fragment' if fragment else 'full',
            'includedBy': sorted(includes[view]),
            'sourceSha256': hashlib.sha256((app / view).read_bytes()).hexdigest(),
            'validatingConversionPassed': passed,
            'jspxSha256': hashlib.sha256(output.read_bytes()).hexdigest() if passed else None,
            'diagnostic': error,
            'seconds': round(time.monotonic() - started, 2),
        }

    views = [view for view in sources if view.endswith('.jsp')]
    results = []
    base_commit = subprocess.check_output(['git', 'rev-parse', args.base], cwd=ROOT, text=True).strip()
    report = {
        'baseCommit': base_commit,
        'recipeCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'jasperVersion': '10.1.60',
        'openRewriteVersion': '8.90.4',
        'totalJspFiles': len(views),
        'validation': ['namespace-aware XML', 'original Jasper Java compilation',
                       'converted Jasper Java compilation', 'OpenRewrite XML round-trip'],
        'fragmentContext': 'Include-only files, with the union of nonconflicting source taglib declarations. '
                           'Parent Java variables are not synthesized.',
        'limitations': ['Per-file conversions retain original .jsp include targets.',
                        'Generated-Java equivalence is measured separately by the five-page pilot.',
                        'Literal HTML in jsp:text is not HTML-validated.',
                        'JSPF and INC files are dependencies, not conversion targets in this census.'],
    }
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(convert, view) for view in views]
        for future in as_completed(futures):
            result = future.result()
            results.append(result)
            report['results'] = sorted(results, key=lambda item: item['view'])
            refresh_report(report, work)
            print(f"{len(results)}/{len(views)} {'PASS' if result['validatingConversionPassed'] else 'FAIL'} "
                  f"{result['view']} {result['diagnostic'] or ''}", flush=True)
    print(json.dumps(report['summary']), flush=True)


if __name__ == '__main__':
    main()
