import numpy as np, wave, sys
SR=44100
rng=np.random.default_rng(11)
def buf(sec): return np.zeros(int(SR*sec))
def add(dst, src, at):
    i=int(at*SR); n=min(len(src), len(dst)-i)
    if n>0: dst[i:i+n]+=src[:n]
def lowpass(x, cutoff):
    a=np.exp(-2*np.pi*cutoff/SR); y=np.zeros_like(x); s=0.0
    for i,v in enumerate(x): s=(1-a)*v+a*s; y[i]=s
    return y
def highpass(x,cutoff): return x-lowpass(x,cutoff)

def thock(scale=1.0, bright=1.0, amp=1.0, dur=0.12, seed=None):
    """a wooden piece set down on a wooden board: a dense cluster of short, uneven
    (inharmonic) wood resonances, a little hollow body underneath, and the contact click"""
    r=np.random.default_rng(seed if seed is not None else rng.integers(1<<30))
    t=np.arange(int(SR*dur))/SR
    y=np.zeros_like(t)
    # hollow board body: low and short, felt more than heard
    y+=0.30*np.exp(-t/0.016)*np.sin(2*np.pi*190*scale*t)
    # the wood itself: many modes from ~550 Hz to ~4.5 kHz, higher ones die faster
    for k in range(22):
        f=scale*550*(8.2**r.random())
        tau=0.024*(700/f)**0.55*r.uniform(0.7,1.3)
        a=r.uniform(0.35,1.0)*(1.0 if f<2600 else 0.6*bright)
        y+=a*np.exp(-t/tau)*np.sin(2*np.pi*f*t+r.uniform(0,6.28))*0.32
    # contact: a 3 ms burst of mid/high noise
    n=r.normal(0,1,len(t))*np.exp(-t/0.0026)
    y+=1.4*bright*lowpass(highpass(n,1100),6000)
    y*=np.minimum(1,t/0.0004)
    return amp*y

def chime(freqs, amp=0.25, dur=0.32, tau=0.11):
    t=np.arange(int(SR*dur))/SR
    y=sum(np.sin(2*np.pi*f*t)*np.exp(-t/tau)*(0.6**k) for k,f in enumerate(freqs))
    return amp*y*np.minimum(1,t/0.004)

def pop(f0, amp=1.0, dur=0.09, tau=0.016):
    """a soft rounded pop: a sine that drops quickly into its pitch and fades fast"""
    t=np.arange(int(SR*dur))/SR
    f=f0*(1+0.8*np.exp(-t/0.005))
    ph=2*np.pi*np.cumsum(f)/SR
    y=(np.sin(ph)+0.22*np.sin(2*ph)+0.08*np.sin(3*ph))*np.exp(-t/tau)
    y*=np.minimum(1,t/0.0018)
    return amp*lowpass(y,5000)

def save(path, y, peak=0.48):
    y=y/np.max(np.abs(y))*peak
    # tiny fade at the very end
    n=int(0.004*SR); y[-n:]*=np.linspace(1,0,n)
    d=(y*32767).astype(np.int16)
    w=wave.open(path,'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(d.tobytes()); w.close()

# ── the default set (Neo pieces) and the game sounds every set shares ──
# Neo pieces are glossy, so these are crisper than the wooden set: a short, bright
# "tock" with fewer, cleaner resonances, like a lacquered piece on a hard board.

def tock(scale=1.0, amp=1.0, dur=0.11, seed=None, bright=1.0):
    r=np.random.default_rng(seed if seed is not None else rng.integers(1<<30))
    t=np.arange(int(SR*dur))/SR
    y=np.zeros_like(t)
    y+=0.35*np.exp(-t/0.012)*np.sin(2*np.pi*240*scale*t)          # small body
    for k in range(9):                                            # a few clean modes
        f=scale*900*(4.5**r.random())
        tau=0.018*(900/f)**0.6*r.uniform(0.8,1.2)
        y+=r.uniform(0.4,1.0)*np.exp(-t/tau)*np.sin(2*np.pi*f*t+r.uniform(0,6.28))*0.38
    n=r.normal(0,1,len(t))*np.exp(-t/0.0018)                      # crisp contact
    y+=1.2*bright*lowpass(highpass(n,1800),9000)
    y*=np.minimum(1,t/0.0003)
    return amp*y

def bell(freq, amp=0.3, dur=0.5, tau=0.18):
    """a soft glassy bell: a fundamental plus a few slightly stretched overtones"""
    t=np.arange(int(SR*dur))/SR
    y=np.zeros_like(t)
    for k,(m,a,d) in enumerate([(1,1.0,1.0),(2.01,0.35,0.6),(3.03,0.15,0.4),(4.2,0.06,0.25)]):
        y+=a*np.sin(2*np.pi*freq*m*t)*np.exp(-t/(tau*d))
    return amp*y*np.minimum(1,t/0.003)

D=sys.argv[1]+'/'
b=buf(0.13); add(b,tock(seed=21),0); save(D+'move-self.wav',b,0.5)
b=buf(0.28); add(b,tock(scale=1.25,amp=1.0,bright=1.5,seed=22),0); add(b,tock(scale=0.9,amp=0.6,seed=23),0.04); save(D+'capture.wav',b,0.5)
b=buf(0.28); add(b,tock(amp=0.95,seed=24),0); add(b,tock(scale=1.1,amp=0.8,seed=25),0.1); save(D+'castle.wav',b,0.5)
b=buf(0.32); add(b,tock(bright=1.3,seed=26),0); add(b,bell(1760,amp=0.22,dur=0.26,tau=0.07),0.045); save(D+'move-check.wav',b,0.45)
b=buf(0.42); add(b,tock(seed=27),0); [add(b,bell(f,amp=0.2,dur=0.32,tau=0.1),d) for f,d in [(1047,0.05),(1319,0.11),(1568,0.17)]]; save(D+'promote.wav',b,0.46)
# game start: two soft rising notes, "ready, go"
b=buf(0.42); add(b,bell(784,amp=0.5,dur=0.3,tau=0.09),0); add(b,bell(1175,amp=0.55,dur=0.36,tau=0.12),0.09); save(D+'after pressing start game.wav',b,0.2)
# game end: a short settled three-note fall
b=buf(0.75); [add(b,bell(f,amp=0.5,dur=0.5,tau=0.16),d) for f,d in [(1175,0),(988,0.12),(784,0.24)]]; save(D+'game-end.wav',b,0.2)
# illegal move: a dull double knock, low and short
b=buf(0.24); add(b,lowpass(tock(scale=0.55,bright=0.3,seed=31),1800),0); add(b,lowpass(tock(scale=0.5,bright=0.3,amp=0.8,seed=32),1800),0.09); save(D+'illegal.wav',b,0.27)
# ten seconds left: two quick clock ticks, then a small high ping
b=buf(0.62); add(b,tock(scale=1.8,amp=0.7,dur=0.05,seed=41),0); add(b,tock(scale=1.6,amp=0.7,dur=0.05,seed=42),0.14); add(b,bell(2093,amp=0.35,dur=0.34,tau=0.1),0.28); save(D+'tensecondsleftonclock.wav',b,0.55)
print('ok')
