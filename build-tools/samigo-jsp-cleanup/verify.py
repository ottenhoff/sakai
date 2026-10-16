#!/usr/bin/env python3
"""Verify the bounded extraction, JavaScript ASTs, and offline Jasper output."""
import argparse
import ast
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

import extract

ROOT = extract.ROOT
CASES = extract.MANIFEST['cases']
WORK = ROOT / 'target/jsp-cleanup-verification'
WRITE = re.compile(r'''out\.write\(("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')\);''')
WRITES = re.compile(r'(?m)(?:^[ \t]*' + WRITE.pattern + r'\n)+')


def syntax_tree(value):
    if isinstance(value, list):
        return [syntax_tree(item) for item in value]
    if isinstance(value, dict):
        return {key: syntax_tree(item) for key, item in value.items()
                if key not in ('start', 'end', 'loc', 'range')}
    return value


def check_scripts():
    results = []
    for index, case in enumerate(CASES):
        _, view, script, block = extract.candidate(case)
        assert (ROOT / extract.WEBAPP / case['view']).read_bytes() == view, case['view']
        asset = ROOT / extract.WEBAPP / case['script']
        assert asset.read_bytes() == script, case['script']
        original = WORK / f'inline-{index}.js'
        original.write_bytes(block[2])
        trees = [syntax_tree(json.loads(subprocess.check_output(
            ['npx', '-y', 'acorn@8.15.0', '--ecma2022', str(path)])))
            for path in (original, asset)]
        assert trees[0] == trees[1], f'JavaScript program changed: {case["view"]}'
        results.append({'view': case['view'], 'script': case['script'],
                        'astSha256': hashlib.sha256(json.dumps(trees[0], sort_keys=True).encode()).hexdigest()})
        print(f'JavaScript AST identical: {case["script"]}', flush=True)
    return results


def canonical_java(source, block, replacement):
    """Keep generated code; normalize literal write chunking and JSP coordinates."""
    substitutions = 0

    def literal_writes(match):
        nonlocal substitutions
        text = ''.join(ast.literal_eval(item[1]) for item in WRITE.finditer(match[0]))
        if block:
            substitutions += text.count(block)
            text = text.replace(block, replacement)
        return '      out.write(' + json.dumps(text, ensure_ascii=False) + ');\n'

    source = WRITES.sub(literal_writes, source)
    # Jasper embeds source positions in comments and EL error messages. Keep the
    # expression text, types, component IDs, tag ordinals, and all calls intact.
    source = re.sub(r'(\.jsp)\(\d+,\d+\)', r'\1(L,C)', source)
    return source, substitutions


def check_jasper(classpath_file, app_classpath):
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else 'java'
    classpath = classpath_file.read_text().strip()
    if app_classpath:
        classpath += os.pathsep + str(ROOT / 'samigo/samigo-app/target/classes')
        classpath += os.pathsep + app_classpath.read_text().strip()
    entries = [Path(entry) for entry in classpath.split(os.pathsep)]
    assert all(entry.exists() for entry in entries), 'Compiler classpath has missing entries'
    stage = WORK / 'webapp'
    if stage.exists():
        shutil.rmtree(stage)
    shutil.copytree(ROOT / extract.WEBAPP, stage)
    libraries = stage / 'WEB-INF/lib'
    libraries.mkdir(exist_ok=True)
    for entry in entries:
        if entry.suffix == '.jar':
            destination = libraries / entry.name
            if not destination.exists():
                destination.symlink_to(entry.resolve())
    declarations = ''.join(f'<%@ taglib uri="{uri}" prefix="{prefix}" %>\n'
                           for prefix, uri in (
                               ('h', 'http://java.sun.com/jsf/html'),
                               ('f', 'http://java.sun.com/jsf/core'),
                               ('t', 'http://myfaces.apache.org/tomahawk'),
                               ('samigo', 'http://www.sakaiproject.org/samigo'),
                               ('c', 'http://java.sun.com/jsp/jstl/core')))
    results = {}
    for index, case in enumerate(CASES):
        (stage / f'pilot{index}.jsp').write_text(
            declarations + f'<%@ include file="/{case["view"]}" %>\n')
    for version in ('before', 'after'):
        for case in CASES:
            before, after, _, _ = extract.candidate(case)
            (stage / case['view']).write_bytes(before if version == 'before' else after)
        # Reuse staging and output paths so Jasper's path-derived JSP IDs stay equal.
        for path in stage.rglob('*'):
            if path.is_file() and not path.is_symlink():
                os.utime(path, (1700000000, 1700000000))
        generated = WORK / 'generated'
        if generated.exists():
            shutil.rmtree(generated)
        generated.mkdir()
        with (generated / 'compile.log').open('w') as log:
            for index in range(len(CASES)):
                # Fresh compiler per translation unit avoids shared TLD cache
                # ordering changing Jasper's dependency metadata between runs.
                subprocess.run([java, '-cp', classpath, 'org.apache.jasper.JspC',
                                '-uriroot', str(stage), '-d', str(generated),
                                '-compile', '-die', f'pilot{index}.jsp'],
                               stdout=log, stderr=subprocess.STDOUT, check=True)
        assert len(list(generated.rglob('*.class'))) >= len(CASES)
        destination = WORK / version
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(generated, destination)
        print(f'Jasper compiled all five {version} views.', flush=True)
    for index, case in enumerate(CASES):
        _, _, _, block = extract.candidate(case)
        replacement = re.match(rb'[ \t]*', block[0])[0].decode() + (
            f'<script src="/samigo-app/{case["script"]}"></script>')
        left, count = canonical_java(
            (WORK / f'before/org/apache/jsp/pilot{index}_jsp.java').read_text(),
            block[0].decode(), replacement)
        right, _ = canonical_java(
            (WORK / f'after/org/apache/jsp/pilot{index}_jsp.java').read_text(), '', '')
        assert count == 1, f'Expected one generated inline script: {case["view"]}'
        if left != right:
            diff = ''.join(difflib.unified_diff(left.splitlines(True), right.splitlines(True)))
            (WORK / f'pilot{index}.diff').write_text(diff)
            raise AssertionError(f'Unexpected generated code difference: pilot{index}.diff')
        # Sensitivity control: normalization must not erase a changed EL binding.
        mutated = right.replace('#{', '#{changed.', 1)
        assert mutated != right, 'Expected an EL binding in this view'
        assert canonical_java(mutated, '', '')[0] != left
        results[case['view']] = hashlib.sha256(left.encode()).hexdigest()
        print(f'Generated Java matches except script reference/source coordinates: {case["view"]}')
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jasper-classpath', type=Path,
                        help='Classpath file with Jasper, Ant, ECJ, app classes and dependencies')
    parser.add_argument('--app-classpath', type=Path,
                        help='Optional separate app dependency classpath; also adds app target/classes')
    args = parser.parse_args()
    WORK.mkdir(parents=True, exist_ok=True)
    results = check_scripts()
    if args.jasper_classpath:
        fingerprints = check_jasper(args.jasper_classpath, args.app_classpath)
        for result in results:
            result['jasperNormalizedSha256'] = fingerprints[result['view']]
    (WORK / 'verification.json').write_text(json.dumps({
        'baseCommit': extract.MANIFEST['baseCommit'], 'results': results}, indent=2) + '\n')
