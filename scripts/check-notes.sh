#!/usr/bin/env bash
# Checks the release notes before they are uploaded: every locale under Play's 500-character
# limit, and the fastlane changelogs the same text as release_notes/.
#
#     scripts/check-notes.sh 35
set -euo pipefail
cd "$(dirname "$0")/.."
code=${1:-$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)}
notes="release_notes/release_notes_v$code.txt"
[ -f "$notes" ] || { echo "no $notes" >&2; exit 1; }

python3 - "$notes" "$code" <<'PY'
import io, re, sys, pathlib
notes, code = sys.argv[1], sys.argv[2]
text = io.open(notes, encoding='utf-8').read()
bad = 0
seen = 0
for match in re.finditer(r'<([\w-]+)>\n(.*?)</\1>', text, re.S):
    locale, body = match.group(1), match.group(2).strip('\n')
    seen += 1
    room = 500 - len(body)
    mirror = pathlib.Path(f'fastlane/metadata/android/{locale}/changelogs/{code}.txt')
    problems = []
    if room < 0:
        problems.append(f'{-room} characters over Play\'s limit')
    elif room < 5:
        problems.append(f'only {room} characters left, and a trailing newline may count')
    if not mirror.is_file():
        problems.append('no fastlane changelog')
    elif mirror.read_text(encoding='utf-8').strip('\n') != body:
        problems.append('the fastlane changelog says something else')
    mark = 'ok ' if not problems else 'NO '
    bad += bool(problems)
    print(f"  {mark}{locale:8} {len(body):3}/500 {'; '.join(problems)}")
if seen == 0:
    print('no locales found in the notes file'); sys.exit(1)
print(f"{seen} locales, {bad} to fix")
sys.exit(1 if bad else 0)
PY
