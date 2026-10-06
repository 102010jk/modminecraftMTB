#!/usr/bin/env python3
"""Synthesises every riding sound of Descent MTB (milestone 06) and encodes it to OGG Vorbis.

No recorded audio exists in the project, so everything here is built from filtered noise, damped resonances,
click trains and an additive vowel model. Run from the repository root:

    python tools/gen_sounds.py                 # all sounds -> src/main/resources/assets/descentmtb/sounds/
    python tools/gen_sounds.py scream wind     # only these (names as in SOUNDS below)
    python tools/gen_sounds.py --wav out/      # additionally keep the 44.1 kHz WAVs (for listening / tuning)
    python tools/gen_sounds.py --list          # names, durations and levels

Needs numpy and ffmpeg (libvorbis). Output is mono, 44.1 kHz, `-q:a 4`. Loops are built circularly (periodic
noise filtering in the FFT domain, integer click counts, periodic modulation), so they repeat without a seam.
Every sound uses a fixed seed: re-running gives byte-identical audio, tune the constants below to change them.

The click rates of the hub buzz loops (HUB_LOOP_HZ) must match BikeParts.HubType.loopHz in the mod.
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'descentmtb', 'sounds'))
FFMPEG = os.environ.get('FFMPEG', 'C:/Users/jakub/Downloads/ffmpeg/bin/ffmpeg')

# click rate (per second) baked into each buzz loop at playback pitch 1.0 == BikeParts.HubType.loopHz
HUB_LOOP_HZ = {'i9': 2000.0, 'dt': 160.0, 'ck': 215.0, 'hope': 130.0}


# --------------------------------------------------------------------------- building blocks

def tt(seconds):
    return np.arange(int(round(seconds * SR))) / SR


def freqs(n):
    return np.fft.rfftfreq(n, 1.0 / SR)


def filt(x, response):
    """Zero-phase (and circular) filtering with a magnitude response f(freq) -> gain."""
    spec = np.fft.rfft(x)
    return np.fft.irfft(spec * response(np.maximum(freqs(len(x)), 1e-3)), len(x))


def lowpass(x, fc, order=2):
    return filt(x, lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order)))


def highpass(x, fc, order=2):
    return filt(x, lambda f: 1.0 / np.sqrt(1.0 + (fc / f) ** (2 * order)))


def bandpass(x, lo, hi, order=2):
    return filt(x, lambda f: 1.0 / np.sqrt(1.0 + (lo / f) ** (2 * order)) / np.sqrt(1.0 + (f / hi) ** (2 * order)))


def resonance(x, fc, q, gain=1.0):
    """Adds a resonant peak at fc (quality q) on top of the signal (formant / body resonance)."""
    return x + gain * filt(x, lambda f: 1.0 / np.sqrt(1.0 + (q * (f / fc - fc / f)) ** 2))


def noise(n, rng):
    return rng.standard_normal(n)


def pink(n, rng):
    """1/f noise (circular)."""
    return filt(noise(n, rng), lambda f: 1.0 / np.sqrt(f / 100.0 + 1.0))


def periodic_env(n, cycles, depths, rng):
    """Strictly periodic amplitude modulation: sinusoids with integer cycles per loop."""
    t = np.arange(n) / n
    env = np.ones(n)
    for c, d in zip(cycles, depths):
        env += d * np.sin(2 * np.pi * c * t + rng.uniform(0, 2 * np.pi))
    return np.maximum(env, 0.05)


def normalize(x, peak_db=-3.0):
    m = np.max(np.abs(x))
    return x * (10 ** (peak_db / 20.0) / m) if m > 0 else x


def fade(x, ms_in=2.0, ms_out=6.0):
    x = x.copy()
    a, b = int(SR * ms_in / 1000), int(SR * ms_out / 1000)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def damped(t, f, decay, phase=0.0):
    """A ringing resonance: starts at t = 0, zero before."""
    return np.where(t >= 0, np.exp(-np.maximum(t, 0) / decay) * np.sin(2 * np.pi * f * np.maximum(t, 0) + phase), 0.0)


def swept_noise(n, center, bw_oct, rng, frame=1024):
    """Noise through a band-pass whose centre frequency follows center(t_seconds): overlap-add of Hann frames."""
    hop = frame // 4
    win = np.hanning(frame)
    out = np.zeros(n + frame)
    src = noise(n + frame, rng)
    f = np.fft.rfftfreq(frame, 1.0 / SR)
    for start in range(0, n, hop):
        c = center((start + frame / 2) / SR)
        lo, hi = c / 2 ** (bw_oct / 2), c * 2 ** (bw_oct / 2)
        gain = np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / c) / (bw_oct / 2.355)) ** 2) if bw_oct > 0 else 1.0
        spec = np.fft.rfft(src[start:start + frame] * win) * gain
        out[start:start + frame] += np.fft.irfft(spec, frame) * win
    return out[:n] / 1.5


# --------------------------------------------------------------------------- hubs

# per hub: partials (freq Hz, amplitude, decay seconds), noise burst (highpass Hz, amount, decay s), jitter
HUBS = {
    # 54T ratchet: a clean, medium-pitched tick - short, crisp, little body
    'dt': dict(partials=[(2800, 1.0, 0.0022), (4600, 0.55, 0.0016), (1500, 0.40, 0.0040), (700, 0.28, 0.0055)],
               burst=(3000, 0.45, 0.0006), click_len=0.07, amp_jit=0.07, time_jit=0.02, gain=1.0),
    # Chris King: deeper and fatter, a nasal buzz of the 72 point ring drive - "angry bee"
    'ck': dict(partials=[(1100, 1.0, 0.0050), (2350, 0.70, 0.0036), (3700, 0.45, 0.0026), (360, 0.55, 0.0062), (5200, 0.2, 0.0016)],
               burst=(1500, 0.30, 0.0009), click_len=0.09, amp_jit=0.13, time_jit=0.035, gain=1.05),
    # Hope: loud, inharmonic, metallic clack; the loose rattle comes from the timing jitter
    'hope': dict(partials=[(2150, 1.0, 0.0060), (3580, 0.80, 0.0050), (5120, 0.65, 0.0040), (7400, 0.45, 0.0030), (950, 0.60, 0.0085)],
                 burst=(2500, 0.60, 0.0008), click_len=0.12, amp_jit=0.10, time_jit=0.07, gain=1.15),
    # Industry Nine Hydra: 690 points -> 2 kHz of tiny ticks fuse into a very high pitched buzz
    'i9': dict(partials=[(4200, 1.0, 0.00055), (6600, 0.55, 0.00042), (2100, 0.70, 0.00070), (8800, 0.20, 0.0003)],
               burst=(5000, 0.20, 0.0002), click_len=0.012, amp_jit=0.025, time_jit=0.004, gain=0.55),
}


def hub_click_wave(spec, rng, length=None):
    n = int(round((length or spec['click_len']) * SR))
    t = np.arange(n) / SR
    w = np.zeros(n)
    for f, a, d in spec['partials']:
        w += a * damped(t, f, d, rng.uniform(0, 0.6))
    hp, amt, dec = spec['burst']
    burst = highpass(noise(n, rng), hp) * np.exp(-t / dec)
    return w + amt * burst * (np.max(np.abs(w)) / max(np.max(np.abs(burst)), 1e-9))


def hub_click(name):
    spec = HUBS[name]
    rng = np.random.default_rng(100 + sorted(HUBS).index(name))
    w = hub_click_wave(spec, rng)
    return fade(normalize(w * spec['gain'], -3.0 + 20 * np.log10(min(spec['gain'], 1.0))), 0.5, 4)


def hub_buzz(name, seconds=0.8):
    """Loop of the freewheel: a click train at HUB_LOOP_HZ with the hub's jitter, exactly periodic."""
    spec = HUBS[name]
    rng = np.random.default_rng(200 + sorted(HUBS).index(name))
    rate = HUB_LOOP_HZ[name]
    clicks = max(2, int(round(rate * seconds)))
    n = int(round(clicks * SR / rate))          # an integer number of clicks per loop
    period = n / clicks
    w_len = int(spec['click_len'] * SR)
    variants = [hub_click_wave(spec, rng) for _ in range(12)]
    out = np.zeros(n)
    for k in range(clicks):
        pos = (k + rng.normal(0, spec['time_jit'])) * period
        base = int(np.floor(pos)) % n
        v = variants[rng.integers(len(variants))] * (1 + rng.normal(0, spec['amp_jit']))
        idx = (base + np.arange(len(v))) % n
        np.add.at(out, idx, v)
    return normalize(out * spec['gain'], -4.0 + 20 * np.log10(min(spec['gain'], 1.0)))


# --------------------------------------------------------------------------- air, tyres, suspension

def wind(seconds=2.4):
    """Air rushing past the helmet: a broad low-mid rush tilting down at 3 dB/oct, gusting; the high hiss gusts harder."""
    rng = np.random.default_rng(300)
    n = int(seconds * SR)
    tilt = lambda f: (1.0 + f / 260.0) ** -0.8 / np.sqrt(1.0 + (70.0 / f) ** 4)
    rush = filt(noise(n, rng), tilt)
    hiss = bandpass(noise(n, rng), 2500, 8000, 2)
    roar = lowpass(noise(n, rng), 220)
    mod_hiss = periodic_env(n, [2, 5, 11], [0.35, 0.20, 0.12], rng)
    mod_rush = periodic_env(n, [1, 3, 7], [0.16, 0.12, 0.07], rng)
    x = (rush / np.std(rush)) * mod_rush + 0.10 * (hiss / np.std(hiss)) * mod_hiss + 0.55 * roar / np.std(roar)
    return normalize(x, -5.0)


def roll_soft(seconds=1.6):
    """Knobbly tyre on dirt / grass / sand: deep rumble, soft hiss and a few small crackles."""
    rng = np.random.default_rng(310)
    n = int(seconds * SR)
    rumble = bandpass(noise(n, rng), 55, 650, 2)
    grain = bandpass(noise(n, rng), 900, 3800, 2) * 0.07
    pops = np.zeros(n)
    hits = rng.random(n) < 150.0 / SR
    pops[hits] = rng.exponential(1.0, hits.sum())
    pops = bandpass(pops, 300, 2200, 2) * 1.6
    x = (rumble * periodic_env(n, [3, 8], [0.18, 0.10], rng) + grain + pops)
    return normalize(x, -5.0)


def roll_hard(seconds=1.6):
    """Gravel / rock: lots of small stones - a dense crunch of resonant impulses."""
    rng = np.random.default_rng(311)
    n = int(seconds * SR)
    imp = np.zeros(n)
    hits = rng.random(n) < 720.0 / SR
    imp[hits] = rng.rayleigh(1.0, hits.sum()) * rng.choice([-1, 1], hits.sum())
    crunch = bandpass(imp, 700, 7000, 2)
    crunch = resonance(crunch, 1800, 5, 1.2)
    crunch = resonance(crunch, 3300, 6, 0.9)
    clusters = lowpass(np.abs(noise(n, rng)), 9)
    clusters = 0.45 + clusters / np.max(clusters)
    rumble = bandpass(noise(n, rng), 90, 420, 2) * 0.25
    return normalize(crunch / np.std(crunch) * clusters + rumble / np.std(rumble) * 0.12, -5.0)


def roll_wood(seconds=1.6):
    """Boards and bridges: a hollow, resonant drum plus the knock of every plank seam."""
    rng = np.random.default_rng(312)
    n = int(seconds * SR)
    body = bandpass(noise(n, rng), 70, 330, 3)
    body = resonance(body, 180, 6, 2.2)
    body = resonance(body, 340, 8, 1.4)
    body = body / np.std(body) * 0.55
    seams = 8
    t = np.arange(int(0.16 * SR)) / SR
    knock = (damped(t, 150, 0.040) + 0.7 * damped(t, 238, 0.030) + 0.35 * damped(t, 410, 0.018)
             + 0.30 * highpass(noise(len(t), rng), 800) * np.exp(-t / 0.006))
    tok = np.zeros(n)
    for k in range(seams):
        base = int((k + rng.normal(0, 0.01)) * n / seams) % n
        np.add.at(tok, (base + np.arange(len(knock))) % n, knock * (1 + rng.normal(0, 0.12)))
    x = body * periodic_env(n, [2, 5], [0.16, 0.08], rng) + tok * 0.9
    return normalize(x, -5.0)


def suspension_hiss(seconds=0.5):
    """Air spring / damper: a short descending 'pshh' with a faint low thump under it."""
    rng = np.random.default_rng(320)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    air = swept_noise(n, lambda s: 8200 * np.exp(-s / 0.11) + 2300, 1.4, rng)
    air *= np.minimum(t / 0.005, 1.0) * np.exp(-t / 0.10)
    tail = bandpass(noise(n, rng), 3200, 9000, 2) * np.exp(-t / 0.22) * np.minimum(t / 0.02, 1.0) * 0.18
    thump = np.sin(2 * np.pi * 95 * t) * np.exp(-t / 0.018) * 0.55
    return fade(normalize(air + tail + thump * np.max(np.abs(air)), -3.0), 1, 30)


# --------------------------------------------------------------------------- rider


def scream(seconds=2.4):
    """A comedic cartoon 'aaaaAAAAAh': additive vowel /a/ with a rising pitch contour, vibrato, a shaky tremolo
    and some breath. Harmonic amplitudes follow the formant envelope, so the vowel stays correct as pitch moves."""
    rng = np.random.default_rng(330)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    # pitch: quick rise into the scream, climbs to a peak, then sags
    base = np.interp(t, [0, 0.10, 0.45, 1.0, 1.45, 2.0, seconds], [250, 430, 500, 600, 610, 500, 400])
    vib_depth = np.interp(t, [0, 0.4, 1.0, seconds], [0.005, 0.02, 0.045, 0.05])
    vib = 1 + vib_depth * np.sin(2 * np.pi * (5.2 + 0.9 * t) * t + 0.4)
    wob = 1 + 0.012 * lowpass(noise(n, rng), 9) / 0.5
    f0 = base * vib * wob
    phase = 2 * np.pi * np.cumsum(f0) / SR
    # the mouth opens as the scream climbs: F1 sweeps up, F2 moves a little
    f1 = np.interp(t, [0, 0.12, 0.6, seconds], [560, 760, 900, 820])
    f2 = np.interp(t, [0, 0.15, seconds], [1050, 1180, 1300])
    out = np.zeros(n)
    kmax = int((SR / 2 - 800) / 250)
    for k in range(1, kmax + 1):
        fk = k * f0
        amp = k ** -1.15                                           # glottal tilt, a bit strained
        env = (np.exp(-0.5 * ((fk - f1) / 230.0) ** 2) * 1.0
               + np.exp(-0.5 * ((fk - f2) / 320.0) ** 2) * 0.60
               + np.exp(-0.5 * ((fk - 2650) / 480.0) ** 2) * 0.30
               + np.exp(-0.5 * ((fk - 3450) / 600.0) ** 2) * 0.15
               + 0.05)
        env = env * (fk < SR / 2 - 1000)
        out += amp * env * np.sin(k * phase + rng.uniform(0, 2 * np.pi))
    breath = bandpass(noise(n, rng), 900, 4200, 2)
    breath = resonance(breath, 1300, 3, 0.5)
    amp_env = np.interp(t, [0, 0.03, 0.12, 1.7, 2.05, seconds], [0, 0.55, 1.0, 1.0, 0.45, 0.0])
    trem = 1 + 0.14 * np.sin(2 * np.pi * 6.3 * t)
    out = out / np.max(np.abs(out)) + 0.07 * breath / np.std(breath) * (0.4 + 0.6 * amp_env)
    return normalize(out * amp_env * trem, -3.5)


# --------------------------------------------------------------------------- tricks

def heel_click():
    """Two heels knocking together: 'tik-tik'."""
    rng = np.random.default_rng(340)
    n = int(0.20 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for start, pitch, amp in [(0.004, 1.0, 1.0), (0.075, 1.14, 0.78)]:
        local = t - start
        w = (damped(local, 2400 * pitch, 0.0032) + 0.6 * damped(local, 4100 * pitch, 0.0021)
             + 0.55 * damped(local, 900 * pitch, 0.0065) + 0.35 * damped(local, 6200 * pitch, 0.0014))
        burst = highpass(noise(n, rng), 1800) * np.where(local >= 0, np.exp(-np.maximum(local, 0) / 0.0007), 0)
        out += amp * (w + 0.35 * burst * np.max(np.abs(w)) / np.max(np.abs(burst)))
    return fade(normalize(out, -3.0), 0.5, 20)


def barspin_whirr():
    """Bearing whirr of the bars spinning past: a rising then falling tone with harmonics and ball noise."""
    rng = np.random.default_rng(341)
    seconds = 0.56
    n = int(seconds * SR)
    t = np.arange(n) / SR
    u = t / seconds
    f = 340 + 880 * np.sin(np.pi * np.minimum(u * 1.15, 1.0)) ** 1.4          # sweeps up and settles
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.5 * np.sin(2 * phase + 0.7) + 0.28 * np.sin(3 * phase + 1.9) + 0.12 * np.sin(5 * phase)
    balls = 1 + 0.30 * np.sin(2 * np.pi * np.cumsum(f * 0.11) / SR)             # rolling elements modulate it
    hiss = bandpass(noise(n, rng), 2400, 7000, 2)
    env = np.sin(np.pi * u) ** 1.3
    x = (tone * balls * 0.9 + hiss / np.std(hiss) * 0.45) * env
    return fade(normalize(x, -4.0), 6, 25)


def tailwhip_swish():
    """The frame whipping around the bars: a swept-noise 'whoosh' with a low pass-by whoomp."""
    rng = np.random.default_rng(342)
    seconds = 0.66
    n = int(seconds * SR)
    t = np.arange(n) / SR
    u = t / seconds
    whoosh = swept_noise(n, lambda s: 900 + 3300 * np.sin(np.pi * np.clip(s / seconds * 1.1, 0, 1)) ** 1.5, 1.6, rng)
    env = np.sin(np.pi * u) ** 1.8
    whoomp = lowpass(noise(n, rng), 380) * np.sin(np.pi * np.clip(u * 1.1 - 0.05, 0, 1)) ** 3
    x = whoosh / np.std(whoosh) * env + 0.55 * whoomp / np.std(whoomp)
    return fade(normalize(x, -4.0), 8, 30)


# --------------------------------------------------------------------------- registry and output

SOUNDS = {}
for _h in ('i9', 'dt', 'ck', 'hope'):
    SOUNDS['hub/%s_click' % _h] = (lambda h=_h: hub_click(h))
    SOUNDS['hub/%s_buzz' % _h] = (lambda h=_h: hub_buzz(h))
SOUNDS.update({
    'ride/wind_loop': wind,
    'ride/roll_soft': roll_soft,
    'ride/roll_hard': roll_hard,
    'ride/roll_wood': roll_wood,
    'ride/susp_hiss': suspension_hiss,
    'ride/scream': scream,
    'trick/heel_click': heel_click,
    'trick/barspin_whirr': barspin_whirr,
    'trick/tailwhip_swish': tailwhip_swish,
})


def write_wav(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def encode(wav, ogg):
    os.makedirs(os.path.dirname(ogg), exist_ok=True)
    cmd = [FFMPEG, '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '4', ogg]
    subprocess.run(cmd, check=True)


def describe(name, x):
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x))))
    centroid = float((spec * freqs(len(x))).sum() / max(spec.sum(), 1e-9))
    return '%-22s %5.2fs  peak %6.1f dB  rms %6.1f dB  centroid %5.0f Hz  seam %.3f' % (
        name, len(x) / SR, 20 * np.log10(np.max(np.abs(x)) + 1e-9), 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-9),
        centroid, abs(x[0] - x[-1]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('names', nargs='*', help='sounds to build (default: all); a name or a path prefix such as hub')
    ap.add_argument('--wav', help='also keep the WAV files in this directory')
    ap.add_argument('--list', action='store_true', help='only print the sounds and their levels, write nothing')
    args = ap.parse_args()
    wanted = [k for k in SOUNDS if not args.names or any(k == a or k.startswith(a.rstrip('/') + '/') or a in k for a in args.names)]
    if not wanted:
        sys.exit('no sound matches %s; known: %s' % (args.names, ', '.join(SOUNDS)))
    with tempfile.TemporaryDirectory() as tmp:
        for key in wanted:
            x = SOUNDS[key]()
            print(describe(key, x))
            if args.list:
                continue
            wav = os.path.join(args.wav or tmp, key + '.wav')
            write_wav(wav, x)
            encode(wav, os.path.join(OUT, key + '.ogg'))


if __name__ == '__main__':
    main()
