# Dexed Mark I Java synthesis core

The Redstone Synthesizer uses a pure-Java port of the active DX7 Mark I path
from Dexed commit `2e182b3db85c09083ab13c8b9b00565ce7d9ff85`:
<https://github.com/asb2m10/dexed>.

The port retains the Mark I operator renderer, algorithm routing, fixed-point
tables, envelopes, pitch envelope, LFO, portamento, controller behavior, and
the 16-voice M3E host. The generic FM renderer, unused higher-resolution sine
path, MTS support, OPL emulation, effects, JUCE, and plugin code are omitted.

Dexed and `EngineMkI` are GPL-3.0. The ported MSFA portions retain their
Apache-2.0 provenance. See the repository `LICENSE` and `NOTICE.md`.
