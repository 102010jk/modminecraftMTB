"""Prints pass/fail + [feel] log lines from the last `gradlew test` run."""
import glob, re, html
for f in glob.glob('build/test-results/test/*.xml'):
    s = open(f, encoding='utf-8').read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*?(/>|>(.*?)</testcase>)', s, re.S):
        body = m.group(3) or ''
        fail = re.search(r'<failure message="([^"]*)"', body)
        print(('FAIL ' if fail else 'ok   ') + m.group(1) + (' :: ' + html.unescape(fail.group(1))[40:170] if fail else ''))
    out = re.search(r'<system-out><!\[CDATA\[(.*?)\]\]>', s, re.S)
    if out:
        print(out.group(1))
