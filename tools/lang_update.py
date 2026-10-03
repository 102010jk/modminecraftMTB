"""Applies lang changes (add / remove / rename keys) to en_us.json and cs_cz.json, preserving order and UTF-8."""
import json
import os

LANG = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'descentmtb', 'lang')


def load(name):
    with open(os.path.join(LANG, name + '.json'), encoding='utf-8') as f:
        return json.load(f)


def save(name, data):
    with open(os.path.join(LANG, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def apply(name, remove_prefixes=(), remove_keys=(), rename=None, add=None):
    d = load(name)
    out = {}
    rename = rename or {}
    for k, v in d.items():
        if k in remove_keys or any(k.startswith(p) for p in remove_prefixes):
            continue
        for old, new in rename.items():
            if k.startswith(old):
                k = new + k[len(old):]
                break
        out[k] = v
    out.update(add or {})
    save(name, out)
    return out
