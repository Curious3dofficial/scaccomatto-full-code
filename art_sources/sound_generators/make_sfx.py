"""Original sound effects for Spell Chess, made from scratch (no samples), so they're
free to ship: a teleport, putting a shield on, and a hit bouncing off a shield."""
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 44100
rng = np.random.default_rng(7)


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def env_ad(t, attack, decay):
    a = np.clip(t / attack, 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay)


def bandpass(x, lo, hi, order=2):
    sos = butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x)


def sweep_bandpass(noise, f_start, f_end, width=0.35, chunks=60):
    """Noise through a band-pass whose centre glides from f_start to f_end."""
    out = np.zeros_like(noise)
    n = len(noise)
    edges = np.linspace(0, n, chunks + 1).astype(int)
    win = np.hanning(2 * (edges[1] - edges[0]) + 2)
    for i in range(chunks):
        frac = i / (chunks - 1)
        fc = f_start * (f_end / f_start) ** frac
        lo, hi = fc * (1 - width), min(fc * (1 + width), SR / 2 - 100)
        seg_lo = max(0, edges[i] - (edges[1] - edges[0]))
        seg_hi = min(n, edges[i + 1] + (edges[1] - edges[0]))
        y = bandpass(noise[seg_lo:seg_hi], lo, hi)
        w = np.hanning(seg_hi - seg_lo)
        out[seg_lo:seg_hi] += y * w
    return out


def normalise(x, peak_db):
    x = x - np.mean(x)
    return x / np.max(np.abs(x)) * 10 ** (peak_db / 20)


def fade(x, fin=0.004, fout=0.03):
    n_in, n_out = int(SR * fin), int(SR * fout)
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def save(name, x):
    wavfile.write(name, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


# ── teleport: a quick airy "vwoop" up and back down, with a sparkle on top ──
def teleport():
    t = t_axis(0.95)
    noise = rng.standard_normal(len(t))
    up = sweep_bandpass(noise, 300, 3200)
    shape = np.sin(np.pi * np.clip(t / 0.95, 0, 1)) ** 1.5
    whoosh = up * shape
    # a falling, wobbling tone underneath: pitch drops as the piece "lands"
    f = 900 * np.exp(-t * 2.2) + 180
    wobble = 1 + 0.04 * np.sin(2 * np.pi * 11 * t)
    phase = 2 * np.pi * np.cumsum(f * wobble) / SR
    tone = (np.sin(phase) + 0.35 * np.sin(2 * phase)) * env_ad(t, 0.03, 0.35)
    # sparkle: a few short high chimes
    sparkle = np.zeros_like(t)
    for start, freq in [(0.08, 2637), (0.17, 3136), (0.27, 3951), (0.38, 3520)]:
        tt = t - start
        m = tt >= 0
        sparkle[m] += np.sin(2 * np.pi * freq * tt[m]) * env_ad(tt[m], 0.004, 0.06)
    x = 0.9 * normalise(whoosh, 0) + 0.55 * normalise(tone, 0) + 0.18 * sparkle
    return fade(normalise(x, -9), 0.01, 0.12)


# ── armour on: a bright metal "shing-clink" (two small plates meeting) ──
def armor_equip():
    t = t_axis(0.62)
    x = np.zeros_like(t)
    # two hits, the second a little softer, like a buckle clicking home
    for start, gain, base in [(0.0, 1.0, 1180), (0.13, 0.7, 1560)]:
        tt = t - start
        m = tt >= 0
        hit = np.zeros_like(t)
        # inharmonic partials = sounds like metal, not a bell
        for ratio, amp, dec in [(1.0, 1.0, 0.16), (2.31, 0.6, 0.10), (3.87, 0.45, 0.07),
                                (5.12, 0.3, 0.05), (7.03, 0.2, 0.035)]:
            hit[m] += amp * np.sin(2 * np.pi * base * ratio * tt[m]) * np.exp(-tt[m] / dec)
        click = rng.standard_normal(len(t)) * 0
        click[m] = bandpass(rng.standard_normal(m.sum()), 2500, 9000) * np.exp(-tt[m] / 0.008)
        x += gain * (hit + 1.4 * click)
    # a soft cloth/leather rustle under it
    rustle = bandpass(rng.standard_normal(len(t)), 600, 2500) * env_ad(t, 0.02, 0.12) * 0.25
    x = normalise(x, 0) + rustle
    return fade(normalise(x, -10), 0.002, 0.08)


# ── shield block: a short, solid "thunk-tang" -- the hit bounces off ──
def shield_block():
    t = t_axis(0.3)
    thump_f = 140 * np.exp(-t * 9) + 70
    thump = np.sin(2 * np.pi * np.cumsum(thump_f) / SR) * np.exp(-t / 0.06)
    clang = np.zeros_like(t)
    for ratio, amp, dec in [(1.0, 1.0, 0.09), (2.17, 0.55, 0.06), (3.41, 0.4, 0.045), (4.9, 0.25, 0.03)]:
        clang += amp * np.sin(2 * np.pi * 620 * ratio * t) * np.exp(-t / dec)
    crack = bandpass(rng.standard_normal(len(t)), 1500, 8000) * np.exp(-t / 0.012)
    x = 1.0 * normalise(thump, 0) + 0.7 * normalise(clang, 0) + 0.6 * normalise(crack, 0)
    return fade(normalise(x, -1), 0.001, 0.05)


if __name__ == "__main__":
    save("teleport.wav", teleport())
    save("shield_on.wav", armor_equip())
    save("shield_block.wav", shield_block())
    print("done")
