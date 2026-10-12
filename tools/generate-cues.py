"""Generate Tempo's original notification sounds with Python's standard library."""

import math
from pathlib import Path
import struct
import wave

SAMPLE_RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def silence(seconds):
    return [0.0] * round(seconds * SAMPLE_RATE)


def note(frequency, seconds, bell=False):
    count = round(seconds * SAMPLE_RATE)
    samples = []
    for index in range(count):
        time = index / SAMPLE_RATE
        attack = min(1.0, time / 0.004)
        release = min(1.0, (seconds - time) / 0.025)
        if bell:
            value = sum(
                amplitude * math.exp(-time * decay) * math.sin(2 * math.pi * frequency * ratio * time)
                for ratio, amplitude, decay in ((1, 1, 3.5), (2.76, 0.30, 7), (4.07, 0.13, 10))
            )
        else:
            value = math.sin(2 * math.pi * frequency * time)
            value += 0.15 * math.sin(4 * math.pi * frequency * time)
        samples.append(value * attack * release)
    return samples


def write_sound(name, samples):
    peak = max(abs(value) for value in samples)
    normalized = [round(value / peak * 0.85 * 32767) for value in samples]
    with wave.open(str(OUTPUT / name), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(struct.pack(f"<{len(normalized)}h", *normalized))
    print(f"{name}: {len(samples) / SAMPLE_RATE:.3f}s, mono PCM 16-bit / {SAMPLE_RATE} Hz")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    # Last three seconds: one high, short tick each second.
    write_sound("countdown_tick.wav", note(1550, 0.14))
    # A stage reaches zero: one lower, rounded bell strike (no leading high note).
    write_sound("stage_end.wav", note(440, 0.48, True))
    # The entire workout finishes: three ascending notes, with a sustained final note.
    write_sound("workout_complete.wav", note(659.25, 0.16, True) + silence(0.045)
                + note(880, 0.16, True) + silence(0.045) + note(1318.51, 0.58, True))


if __name__ == "__main__":
    main()
