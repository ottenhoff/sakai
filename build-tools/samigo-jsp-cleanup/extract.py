#!/usr/bin/env python3
"""Extract the five selected pure scripts; refuse to overwrite unrelated edits."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parents[2]
WEBAPP = Path('samigo/samigo-app/src/webapp')
MANIFEST = json.loads(Path(__file__).with_name('pilot.json').read_text())
SCRIPT = re.compile(rb'(?m)^[ \t]*<script\b([^>]*)>(.*?)</script\s*>', re.S)


def original(case):
    return subprocess.check_output(['git', 'show', f"{MANIFEST['baseCommit']}:{WEBAPP / case['view']}"], cwd=ROOT)


def selected_script(source):
    blocks = []
    for match in SCRIPT.finditer(source):
        attributes, body = match[1], match[2]
        if attributes.strip() or not (b'<' in body or b'&' in body):
            continue
        if re.search(rb'<(?:%|/?[A-Za-z_][\w.-]*:)|[#$]\{|currentScript|document\.write', body):
            continue
        blocks.append(match)
    if len(blocks) != 1:
        raise ValueError(f'Expected exactly one eligible classic script, found {len(blocks)}')
    return blocks[0]


def candidate(case):
    source = original(case)
    block = selected_script(source)
    indentation = re.match(rb'[ \t]*', block[0])[0]
    replacement = indentation + f'<script src="/samigo-app/{case["script"]}"></script>'.encode()
    view = source[:block.start()] + replacement + source[block.end():]
    body = '\n'.join(line.rstrip() for line in textwrap.dedent(
        block[2].decode().replace('\r\n', '\n')).splitlines()).strip() + '\n'
    copyright_match = re.search(rb'Copyright[^\r\n]*', source)
    copyright_line = copyright_match[0].decode() if copyright_match else 'Copyright (c) 2026 The Apereo Foundation'
    version = '1.0' if b'Version 1.0' in source else '2.0'
    license_header = f'''/*
 * {copyright_line}
 *
 * Licensed under the Educational Community License, Version {version} (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/ECL-{version}
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'''
    return source, view, (license_header + body).encode(), block


def apply():
    for case in MANIFEST['cases']:
        before, after, script, _ = candidate(case)
        view_path = ROOT / WEBAPP / case['view']
        script_path = ROOT / WEBAPP / case['script']
        if view_path.read_bytes() not in (before, after):
            raise ValueError(f'Refusing to overwrite edited view: {view_path}')
        if script_path.exists() and script_path.read_bytes() != script:
            raise ValueError(f'Refusing to overwrite edited script: {script_path}')
    for case in MANIFEST['cases']:
        _, after, script, _ = candidate(case)
        (ROOT / WEBAPP / case['view']).write_bytes(after)
        (ROOT / WEBAPP / case['script']).write_bytes(script)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Apply the reviewed, bounded extraction')
    args = parser.parse_args()
    if args.apply:
        apply()
    else:
        for case in MANIFEST['cases']:
            _, after, script, _ = candidate(case)
            assert (ROOT / WEBAPP / case['view']).read_bytes() == after, case['view']
            assert (ROOT / WEBAPP / case['script']).read_bytes() == script, case['script']
        print('Five views and script assets match the bounded extraction exactly.')
