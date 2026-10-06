#!/usr/bin/env python3
"""Imports the riding sounds of Descent MTB from the extracted audio of Descenders and writes sounds.json.

Replaces the old synthesiser (gen_sounds.py). The user extracted the game's audio and allowed it for this
non-commercial fun project. Run from the repository root:

    python tools/import_descenders_sounds.py            # everything -> assets/descentmtb/sounds + sounds.json
    python tools/import_descenders_sounds.py --list     # only print what would be imported (no ffmpeg, no writes)

Needs numpy and ffmpeg (libvorbis). Environment overrides: DESCENDERS_SFX, DESCENDERS_VO, FFMPEG.

What it does, per sample:
  * mixes down to mono (Minecraft only spatialises mono sounds) and encodes OGG Vorbis, 44.1 kHz, -q:a 3;
  * one-shots: trims leading / trailing silence, adds tiny fades;
  * loops: never trimmed; a seam that would click gets a short equal-power crossfade;
  * loudness: every group is normalised to an RMS target (measured on the active part) with a peak ceiling.
    Groups that belong together (the speed ladder of one surface, the buzz loops of the freehub) share ONE gain so
    their natural level differences survive. The targets are deliberately low: the old synthesised sounds were
    "too loud", and the per-event volumes in sounds.json and the default master volume bring them down further.
  * variants of a one-shot are picked by name AND by measurement (duration, loudness outliers are dropped), spread
    evenly over the numbered files.
Everything below the line "THE SELECTION" is the curated table; sounds.json is generated from it, too.
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'descentmtb'))
OUT = os.path.join(ASSETS, 'sounds')
ROOT = {
    'SFX': os.environ.get('DESCENDERS_SFX', 'D:/SteamLibrary/steamapps/common/Descenders/Extracted_Audio/SFX'),
    'VO': os.environ.get('DESCENDERS_VO', 'D:/SteamLibrary/steamapps/common/Descenders/Extracted_Audio/VO'),
}
FFMPEG = os.environ.get('FFMPEG', 'C:/Users/jakub/Downloads/ffmpeg/bin/ffmpeg')
OUT_SR = 44100


# --------------------------------------------------------------------------- audio helpers

def load_wav(path):
    """Mono float32 samples and the sample rate of a PCM wav (8 / 16 / 24 / 32 bit)."""
    with wave.open(path) as w:
        n, ch, sw, sr = w.getnframes(), w.getnchannels(), w.getsampwidth(), w.getframerate()
        raw = w.readframes(n)
    if sw == 2:
        a = np.frombuffer(raw, '<i2').astype(np.float32) / 32768
    elif sw == 3:
        b = np.frombuffer(raw, np.uint8).reshape(-1, 3).astype(np.int32)
        a = (((b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)) << 8) >> 8) / 8388608.0
    elif sw == 4:
        a = np.frombuffer(raw, '<i4').astype(np.float32) / 2 ** 31
    elif sw == 1:
        a = (np.frombuffer(raw, np.uint8).astype(np.float32) - 128) / 128
    else:
        raise ValueError('unsupported sample width %d' % sw)
    return a.reshape(-1, ch).mean(1).astype(np.float32), sr


def db(x):
    return 20 * np.log10(max(float(x), 1e-9))


def peak_db(x):
    return db(np.abs(x).max()) if len(x) else -120.0


def active_rms_db(x, sr):
    """RMS (dBFS) over the frames that are within 25 dB of the loudest 10 ms frame - ignores silent tails."""
    hop = max(1, sr // 100)
    n = len(x) // hop
    if n == 0:
        return db(np.sqrt((x ** 2).mean())) if len(x) else -120.0
    frames = (x[:n * hop].reshape(n, hop) ** 2).mean(1)
    loud = frames.max()
    if loud <= 0:
        return -120.0
    keep = frames >= loud * 10 ** (-25 / 10)
    return db(np.sqrt(frames[keep].mean()))


def trim(x, sr):
    """Cuts leading / trailing silence (40 dB under the peak) and fades the new ends."""
    if len(x) == 0:
        return x
    thr = max(np.abs(x).max() * 0.01, 0.002)
    idx = np.nonzero(np.abs(x) >= thr)[0]
    if len(idx) == 0:
        return x
    lo = max(0, idx[0] - int(0.003 * sr))
    hi = min(len(x), idx[-1] + int(0.025 * sr))
    y = x[lo:hi].copy()
    fi, fo = min(len(y) // 4, int(0.002 * sr)), min(len(y) // 3, int(0.012 * sr))
    if fi > 1:
        y[:fi] *= np.linspace(0, 1, fi)
    if fo > 1:
        y[-fo:] *= np.linspace(1, 0, fo)
    return y


def loop_seam(x):
    """How hard the wrap-around jump is, relative to the typical sample-to-sample step (>~1.5 clicks)."""
    step = float(np.sqrt((np.diff(x) ** 2).mean())) or 1e-9
    return abs(float(x[0]) - float(x[-1])) / step


def crossfade_loop(x, sr, seconds):
    """Equal-power crossfade of the end into the start: the result is shorter by the fade and loops without a seam."""
    n = int(seconds * sr)
    n = min(n, len(x) // 3)
    t = np.linspace(0, np.pi / 2, n, dtype=np.float32)
    y = x[:-n].copy()
    y[:n] = x[:n] * np.sin(t) + x[-n:] * np.cos(t)
    return y


def encode(x, sr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cmd = [FFMPEG, '-y', '-loglevel', 'error', '-f', 'f32le', '-ar', str(sr), '-ac', '1', '-i', '-',
           '-ar', str(OUT_SR), '-ac', '1', '-c:a', 'libvorbis', '-q:a', '3', path]
    subprocess.run(cmd, input=np.clip(x, -1, 1).astype('<f4').tobytes(), check=True)


# --------------------------------------------------------------------------- the selection machinery

class Group:
    """One output sound family: its sources, how they are processed and the level they are brought to."""

    def __init__(self, key, root, sources, n=None, rms=None, peak=-6.0, loop=False, ladder=None, ref='max',
                 min_dur=0.0, max_dur=1e9, crop=None, fade=0.04):
        self.key, self.root, self.sources, self.n = key, root, sources, n
        self.rms, self.peak, self.loop, self.ladder, self.ref = rms, peak, loop, ladder, ref
        self.min_dur, self.max_dur, self.crop, self.fade = min_dur, max_dur, crop, fade
        self.files = []          # resolved source names
        self.audio = []          # processed samples, one per file
        self.sr = 48000

    def outs(self):
        if len(self.files) == 1 and not self.n:
            return [self.key]
        return ['%s_%d' % (self.key, i + 1) for i in range(len(self.files))]


GROUPS = {}


def group(*args, **kw):
    g = Group(*args, **kw)
    assert g.key not in GROUPS, g.key
    GROUPS[g.key] = g
    return g


def resolve(g):
    """Turns glob / name specs into the final file list (with the measured picking for numbered variants)."""
    base = ROOT[g.root]
    cands = []
    for spec in g.sources:
        if '*' in spec:
            cands += [os.path.basename(p)[:-4] for p in sorted(glob.glob(os.path.join(base, spec + '.wav')))]
        else:
            cands.append(spec)
    for name in cands:
        if not os.path.exists(os.path.join(base, name + '.wav')):
            raise SystemExit('missing source: %s' % os.path.join(base, name + '.wav'))
    if g.n and len(cands) > g.n:
        info = []
        for name in cands:
            x, sr = load_wav(os.path.join(base, name + '.wav'))
            info.append((name, len(x) / sr, active_rms_db(x, sr), peak_db(x)))
        good = [i for i in info if g.min_dur <= i[1] <= g.max_dur and i[3] > -40]
        if len(good) >= g.n:
            med_dur = float(np.median([i[1] for i in good]))
            med_rms = float(np.median([i[2] for i in good]))
            keep = [i for i in good if 0.4 * med_dur <= i[1] <= 2.5 * med_dur and i[2] >= med_rms - 9]
            if len(keep) >= g.n:
                good = keep
        if len(good) < g.n:
            good = info
        pick = np.unique(np.round(np.linspace(0, len(good) - 1, g.n)).astype(int))
        cands = [good[i][0] for i in pick]
    g.files = cands


def process_audio(g):
    base = ROOT[g.root]
    g.audio = []
    for name in g.files:
        x, sr = load_wav(os.path.join(base, name + '.wav'))
        g.sr = sr
        if g.crop:
            x = x[:int(g.crop * sr)]
        if g.loop:
            if g.crop or loop_seam(x) > 1.5:
                x = crossfade_loop(x, sr, g.fade)
        else:
            x = trim(x, sr)
        g.audio.append(x)


def gains():
    """Gain (dB) per group file. Ladders share one gain; other groups normalise every file individually."""
    result = {}
    ladders = {}
    for g in GROUPS.values():
        if g.ladder:
            ladders.setdefault(g.ladder, []).append(g)
    for members in ladders.values():
        rms_list = [active_rms_db(x, g.sr) for g in members for x in g.audio]
        ref = max(rms_list) if members[0].ref == 'max' else float(np.median(rms_list))
        peak_cap = min(g.peak - peak_db(x) for g in members for x in g.audio)
        gain = min(members[0].rms - ref, peak_cap)
        for g in members:
            result[g.key] = [gain] * len(g.audio)
    for g in GROUPS.values():
        if g.ladder:
            continue
        raw = []
        for x in g.audio:
            by_rms = (g.rms - active_rms_db(x, g.sr)) if g.rms is not None else 1e9
            raw.append((by_rms, g.peak - peak_db(x)))
        if g.rms is not None:
            med = float(np.median([r[0] for r in raw]))
            # variants may differ by a few dB, not by 20 (a very quiet outlier must not be blown up)
            raw = [(float(np.clip(r[0], med - 4, med + 4)), r[1]) for r in raw]
        result[g.key] = [min(r[0], r[1]) for r in raw]
    return result


# =========================================================================== THE SELECTION
# Source names are Descenders files (without .wav); '*' globs are picked down to n variants by measurement.

LOOP_RMS = -23.0       # the loudest loop of a family at full volume; sounds.json volumes bring it down further

# ---- freehub: Descenders has one freewheel; hubs differ by pitch / rate in the code ----
group('hub/click', 'SFX', ['fol_bike_coast_single_fix_01', 'fol_bike_coast_single_fix_04', 'fol_bike_coast_single_fix_08',
                           'fol_bike_coast_single_fix_10', 'fol_bike_coast_single_fix_19'], n=0, rms=None, peak=-9.0)
# buzz loops: file b_<N> is baked at N clicks per second (a loop is 7.2 clicks long)
for rate, name in ((6, 'fol_bike_coast_loop_b_6_0'), (10, 'fol_bike_coast_loop_b_10'), (16, 'fol_bike_coast_loop_b_16'),
                   (25, 'fol_bike_coast_loop_b_25'), (40, 'fol_bike_coast_loop_b_40')):
    group('hub/buzz_%d' % rate, 'SFX', [name], loop=True, rms=-27.0, peak=-8.0, ladder='hub_buzz', ref='median')

# ---- pedalling, take-off (the heel click of a trick reuses the soft bike impacts, the flick of a barspin / tailwhip the bunny hop) ----
group('ride/pedal', 'SFX', ['fol_bike_pedal_single_*'], n=6, peak=-8.0)
group('ride/bunnyhop', 'SFX', ['fol_bike_bunnyhop_*'], n=4, rms=-22.0, peak=-6.0)

# ---- tyres rolling: the speed ladders (level 1 = slowest) of each surface family ----
for fam, prefix, levels in (('soft', 'fol_surface_dirt_speed', (1, 2, 3, 4)),
                            ('hard', 'fol_surface_asphalt_speed', (1, 2, 3, 4)),
                            ('wood', 'fol_surface_wood_speed', (2, 3, 4)),
                            ('snow', 'fol_surface_snow_speed', (1, 2, 3, 4))):
    for lv in levels:
        group('ride/roll_%s_%d' % (fam, lv), 'SFX', ['%s%d' % (prefix, lv)], loop=True, rms=LOOP_RMS, peak=-8.0,
              ladder='roll_' + fam)

# ---- slides and locked-wheel skids ----
for key, name in (('soft', 'fol_surface_dirt_slide_loop'), ('hard', 'fol_surface_asphalt_slide_loop'),
                  ('wood', 'fol_surface_wood_slide_loop'), ('snow', 'fol_surface_snow_slide_loop'),
                  ('skid_soft', 'fol_surface_dirt_brake_loop')):
    group('ride/slide_' + key, 'SFX', [name], loop=True, rms=-23.0, peak=-6.0)

# ---- wind: the 20 s loop is cut to 10 s (crossfaded) so it is a short static sample ----
group('ride/wind', 'SFX', ['sfx_gen_windspeed'], loop=True, rms=-25.0, peak=-8.0, crop=10.5, fade=0.5)

# ---- landings by tier: soft / medium / hard, front or back wheel first ----
LAND_TIER = {'soft': -27.0, 'medium': -23.0, 'hard': -19.0}
for wheel in ('front', 'back'):
    for tier, rms in LAND_TIER.items():
        group('land/%s_%s' % (wheel, tier), 'SFX', ['fol_bike_land_%s_%s_*' % (wheel, tier)], n=4, rms=rms, peak=-5.0)
group('land/bigdrop', 'SFX', ['fol_bike_land_bigdrop'], rms=-18.0, peak=-4.0)

# ---- crashes: the bike hitting the ground, then the ground (by surface family) ----
for tier, rms in LAND_TIER.items():
    group('crash/bike_' + tier, 'SFX', ['fol_impact_bike_%s_*' % tier], n=4, rms=rms, peak=-5.0)
CRASH_TIER = {'small': -27.0, 'med': -22.0, 'hard': -18.0}
for surf, prefix in (('dirt', 'fol_impact_dirt'), ('grass', 'fol_impact_grass'), ('stone', 'fol_impact_stone'),
                     ('wood', 'fol_impact_woodramp')):
    for tier, rms in CRASH_TIER.items():
        group('crash/%s_%s' % (surf, tier), 'SFX', ['%s_%s_*' % (prefix, tier)], n=3, rms=rms, peak=-5.0)

# ---- rider voice (male = vo_default_*, female = vo_female_*) ----
for who, prefix, landed in (('male', 'vo_default', ('normal', 'big', 'huge')),
                            ('female', 'vo_female', ('small', 'medium', 'large'))):
    group('voice/%s_scream' % who, 'VO', [prefix + '_bailmeter_midair_*'], n=5, rms=-20.0, peak=-6.0, min_dur=1.3, max_dur=2.5)
    group('voice/%s_bail' % who, 'VO', [prefix + '_bail_overhandlebars_*'], n=4, rms=-22.0, peak=-6.0)
    for tier, rms in (('soft', -26.0), ('med', -23.0), ('hard', -20.0)):
        group('voice/%s_impact_%s' % (who, tier), 'VO', ['%s_impact_%s_*' % (prefix, tier)], n=4, rms=rms, peak=-6.0)
    for grade, name, rms in zip(('normal', 'big', 'huge'), landed, (-25.0, -22.0, -20.0)):
        group('voice/%s_landed_%s' % (who, grade), 'VO', ['%s_landedtrick_%s_*' % (prefix, name)], n=4, rms=rms,
              peak=-6.0, max_dur=2.5)

# ---- bells and horns (played by the server for everyone) ----
group('bell/ding', 'SFX', ['nm_bell_single_2_*', 'nm_bell_single_3_*'], n=4, rms=-22.0, peak=-6.0)
group('bell/mini', 'SFX', ['nm_bell_double_2_*'], rms=-22.0, peak=-6.0)
group('bell/classic', 'SFX', ['nm_bell_ratchet_2_*', 'nm_bell_ratchet_3_*', 'nm_bell_ratchet_6_*'], n=4, rms=-22.0, peak=-6.0)
group('bell/horn', 'SFX', ['nm_horn_classic_1_*', 'nm_horn_classic_2_*'], n=4, rms=-22.0, peak=-6.0)
group('bell/duck', 'SFX', ['nm_misc_duck_toy_*'], rms=-22.0, peak=-6.0)

# ---- for a later feature: tape snapping when a rider rides through trail tape ----
group('misc/tape_break', 'SFX', ['sfx_gen_tapebreak_*'], n=3, rms=-24.0, peak=-6.0)


# =========================================================================== EVENTS (-> sounds.json)
# (event id, subtitle key suffix or None, [group keys], per-sound volume in sounds.json)

EVENTS = []


def event(eid, subtitle, groups, volume):
    EVENTS.append((eid, subtitle, groups, volume))


event('bike.hub.click', 'hub_click', ['hub/click'], 0.40)
for rate in (6, 10, 16, 25, 40):
    event('bike.hub.buzz.%d' % rate, 'hub_buzz', ['hub/buzz_%d' % rate], 0.40)
event('bike.pedal', None, ['ride/pedal'], 0.30)
event('bike.bunnyhop', None, ['ride/bunnyhop'], 0.45)
event('bike.trick.heel_click', 'heel_click', ['crash/bike_soft'], 0.40)
event('bike.trick.flick', 'flick', ['ride/bunnyhop'], 0.40)
event('bike.wind', 'wind', ['ride/wind'], 0.45)
for fam, levels in (('soft', (1, 2, 3, 4)), ('hard', (1, 2, 3, 4)), ('wood', (2, 3, 4)), ('snow', (1, 2, 3, 4))):
    for lv in levels:
        event('bike.roll.%s.%d' % (fam, lv), 'roll', ['ride/roll_%s_%d' % (fam, lv)], 0.50)
for key in ('soft', 'hard', 'wood', 'snow', 'skid_soft'):
    event('bike.slide.' + key.replace('_', '.'), 'slide', ['ride/slide_' + key], 0.50)
for wheel in ('front', 'back'):
    for tier in LAND_TIER:
        event('bike.land.%s.%s' % (wheel, tier), 'land', ['land/%s_%s' % (wheel, tier)], 0.60)
event('bike.land.bigdrop', 'land', ['land/bigdrop'], 0.70)
for tier in LAND_TIER:
    event('bike.crash.bike.' + tier, 'crash', ['crash/bike_' + tier], 0.60)
for surf in ('dirt', 'grass', 'stone', 'wood'):
    for tier in CRASH_TIER:
        event('bike.crash.%s.%s' % (surf, tier), 'crash', ['crash/%s_%s' % (surf, tier)], 0.60)
for who in ('male', 'female'):
    event('bike.rider.%s.scream' % who, 'scream', ['voice/%s_scream' % who], 0.70)
    event('bike.rider.%s.bail' % who, 'grunt', ['voice/%s_bail' % who], 0.70)
    for tier in ('soft', 'med', 'hard'):
        event('bike.rider.%s.impact.%s' % (who, tier), 'grunt', ['voice/%s_impact_%s' % (who, tier)], 0.70)
    for grade in ('normal', 'big', 'huge'):
        event('bike.rider.%s.landed.%s' % (who, grade), 'cheer', ['voice/%s_landed_%s' % (who, grade)], 0.70)
for kind, sub in (('ding', 'bell'), ('mini', 'bell'), ('classic', 'bell'), ('horn', 'horn'), ('duck', 'duck')):
    event('bike.bell.' + kind, sub, ['bell/' + kind], 0.80)
event('tape.break', 'tape', ['misc/tape_break'], 0.70)


def sounds_json():
    data = {}
    for eid, sub, keys, vol in EVENTS:
        entries = []
        for k in keys:
            for out in GROUPS[k].outs():
                entries.append({'name': 'descentmtb:' + out, 'volume': vol, 'stream': False})
        item = {}
        if sub:
            item['subtitle'] = 'descentmtb.subtitle.' + sub
        item['sounds'] = entries
        data[eid] = item
    return data


# =========================================================================== main

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--list', action='store_true', help='print the selection and the levels, write nothing')
    args = ap.parse_args()

    for g in GROUPS.values():
        resolve(g)
        process_audio(g)
    gain_db = gains()

    total = 0
    rows = []
    for g in GROUPS.values():
        for out, name, x, gdb in zip(g.outs(), g.files, g.audio, gain_db[g.key]):
            y = x * (10 ** (gdb / 20))
            path = os.path.join(OUT, out + '.ogg')
            size = 0
            if not args.list:
                encode(y, g.sr, path)
                size = os.path.getsize(path)
                total += size
            rows.append((out, name, len(y) / g.sr, gdb, active_rms_db(y, g.sr), peak_db(y), size))
    for r in rows:
        print('%-28s <- %-40s %6.2fs gain %+6.1f dB  rms %6.1f  peak %6.1f  %6d B' % r)
    print('%d files, %.2f MB' % (len(rows), total / 1e6))

    if not args.list:
        with open(os.path.join(ASSETS, 'sounds.json'), 'w', encoding='utf-8') as f:
            json.dump(sounds_json(), f, indent=2)
            f.write('\n')
        print('wrote sounds.json with %d events' % len(EVENTS))


if __name__ == '__main__':
    sys.exit(main())
