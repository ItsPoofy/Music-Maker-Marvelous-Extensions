#!/usr/bin/env python3
"""Render the four MatrixSF drum presets into exact GM-mapped M3E assets."""

from __future__ import annotations

import argparse
import json
import re
import struct
import subprocess
import tempfile
from pathlib import Path

PRESETS = {"909": 30, "78": 27, "707": 29, "808": 25}
MIN_NOTE, MAX_NOTE = 35, 87
PPQ = 480
SECONDS_PER_STEP = 6.0
FIRST_NOTE_SECONDS = 1.0
TARGET_PEAK_DB = -8.0


def vlq(value: int) -> bytes:
    encoded = bytearray([value & 0x7F])
    value >>= 7
    while value:
        encoded.insert(0, 0x80 | (value & 0x7F))
        value >>= 7
    return bytes(encoded)


def make_midi(path: Path, program: int) -> None:
    track = bytearray()
    track += vlq(0) + bytes((0xB9, 0, 1))
    track += vlq(0) + bytes((0xB9, 32, 0))
    track += vlq(0) + bytes((0xC9, program))
    first_delta = round(FIRST_NOTE_SECONDS * PPQ * 2)
    step_ticks = round(SECONDS_PER_STEP * PPQ * 2)
    note_ticks = PPQ // 4
    for note in range(MIN_NOTE, MAX_NOTE + 1):
        track += vlq(first_delta if note == MIN_NOTE else step_ticks - note_ticks)
        track += bytes((0x99, note, 116))
        track += vlq(note_ticks) + bytes((0x89, note, 0))
    track += vlq(step_ticks) + b"\xFF\x2F\x00"
    header = b"MThd" + struct.pack(">IHHH", 6, 0, 1, PPQ)
    path.write_bytes(header + b"MTrk" + struct.pack(">I", len(track)) + track)


def run(command: list[str], capture: bool = False) -> str:
    completed = subprocess.run(command, check=True, text=True,
                               stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                               stderr=subprocess.STDOUT if capture else subprocess.DEVNULL)
    return completed.stdout or ""


def peak_db(ffmpeg: Path, source: Path) -> float:
    output = run([str(ffmpeg), "-hide_banner", "-nostats", "-i", str(source),
                  "-af", "volumedetect", "-f", "null", "-"], True)
    match = re.search(r"max_volume:\s*(-?(?:\d+(?:\.\d+)?|inf))\s*dB", output)
    if not match or match.group(1) == "-inf":
        raise RuntimeError(f"rendered drum key is silent: {source.name}")
    return float(match.group(1))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--soundfont", type=Path, required=True)
    parser.add_argument("--fluidsynth", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    args = parser.parse_args()
    for required in (args.soundfont, args.fluidsynth, args.ffmpeg):
        if not required.is_file():
            raise FileNotFoundError(required)

    sounds_root = args.resources / "assets/m3e/sounds"
    (sounds_root / "redstone_drum_kit/render-manifest.json").unlink(missing_ok=True)
    sound_entries: list[dict[str, object]] = []
    with tempfile.TemporaryDirectory(prefix="m3e-redstone-drum-kit-") as temporary:
        temp = Path(temporary)
        for variation, program in PRESETS.items():
            midi, rendered = temp / f"{variation}.mid", temp / f"{variation}.wav"
            make_midi(midi, program)
            run([str(args.fluidsynth), "-ni", "-F", str(rendered), "-T", "wav",
                 "-r", "48000", "-g", "1.0", "-R", "0", "-C", "0",
                 str(args.soundfont), str(midi)])
            output_dir = sounds_root / "redstone_drum_kit" / variation
            output_dir.mkdir(parents=True, exist_ok=True)
            for index, note in enumerate(range(MIN_NOTE, MAX_NOTE + 1)):
                sliced = temp / f"{variation}-{note}-trimmed.wav"
                start = FIRST_NOTE_SECONDS + index * SECONDS_PER_STEP - 0.05
                run([str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
                     "-ss", f"{start:.3f}", "-t", "5.80", "-i", str(rendered),
                     "-af", "silenceremove=start_periods=1:start_duration=0:start_threshold=-70dB:"
                            "stop_periods=-1:stop_duration=0.10:stop_threshold=-70dB,apad=pad_dur=0.05",
                     "-ac", "1", "-ar", "48000", str(sliced)])
                gain = TARGET_PEAK_DB - peak_db(args.ffmpeg, sliced)
                destination = output_dir / f"redstone_drum_kit_{variation}_{note}.ogg"
                run([str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
                     "-i", str(sliced), "-af", f"volume={gain:.3f}dB", "-ac", "1",
                     "-ar", "48000", "-c:a", "libvorbis", "-q:a", "5", str(destination)])
                final_peak = peak_db(args.ffmpeg, destination)
                if not -10.0 <= final_peak <= -6.0:
                    raise RuntimeError(f"{destination.name} peak {final_peak} dB is out of range")
                sound_entries.append({"variation": variation, "gm_note": note,
                    "asset": f"m3e:redstone_drum_kit/{variation}/{destination.stem}"})

    sounds_path = args.resources / "assets/m3e/sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    sounds = {key: value for key, value in sounds.items()
              if not key.startswith("redstone_drum_kit_")}
    for entry in sound_entries:
        event = f"redstone_drum_kit_{entry['variation']}_{entry['gm_note']}"
        sounds[event] = {"category": "player", "sounds": [{
            "name": entry["asset"], "volume": 1.42,
            "attenuation_distance": 48}]}
    sound_lines = ["{"]
    entries = list(sounds.items())
    for index, (event, definition) in enumerate(entries):
        comma = "," if index + 1 < len(entries) else ""
        sound_lines.append(f"  {json.dumps(event)}: "
                           f"{json.dumps(definition, separators=(',', ':'))}{comma}")
    sound_lines.append("}")
    sounds_path.write_text("\n".join(sound_lines) + "\n", encoding="utf-8")
    print(f"Rendered {len(sound_entries)} drum assets")


if __name__ == "__main__":
    main()
