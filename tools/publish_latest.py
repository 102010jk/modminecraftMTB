"""Copies the built jar to dist/descentmtb-latest.jar and writes dist/latest.json for the in-game updater.

    python tools/publish_latest.py "short notes" [version]
Then commit dist/descentmtb-latest.jar + dist/latest.json and push.
"""
import datetime, hashlib, json, os, shutil, sys
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
src = os.path.join(root, 'build', 'libs', 'descentmtb-1.0.0-alpha.jar')
dst = os.path.join(root, 'dist', 'descentmtb-latest.jar')
shutil.copyfile(src, dst)
data = open(dst, 'rb').read()
notes = sys.argv[1] if len(sys.argv) > 1 else ''
version = sys.argv[2] if len(sys.argv) > 2 else datetime.date.today().isoformat()
json.dump({'version': version, 'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data), 'notes': notes},
          open(os.path.join(root, 'dist', 'latest.json'), 'w'), indent=2)
print('published', version, len(data), 'bytes')
