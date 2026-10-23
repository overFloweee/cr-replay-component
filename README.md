# CR Replay Component

Private review extraction of the Android native replay component from `cr-battle-history` main commit `d815abb34bbb3847ca2fec95df3096059ea7dda0`.

The source code is licensed under Apache-2.0. This repository contains the Java integration, native bridge, local example host, packaging tools and synthetic checks. Game binaries, game data, runtime dumps, real replays, signing keys and generated APKs are supplied locally and are excluded from Git. See [NOTICE](NOTICE) and [provenance](docs/provenance.md).

This is an unofficial project, not affiliated with or endorsed by Supercell or Tencent. The example is a local development host. No network or account service is configured.

## Build

Requirements: Python 3.9+, JDK 17+ with `javac` and `keytool`, a host `clang++` for synthetic native tests, Android SDK platform 36, build tools 36.0.0, NDK 27.1.12297006, and an external Apktool 3.0.3 jar. Android device playback requires API 30+ and arm64-v8a. The tested SDK location on macOS is `/opt/homebrew/share/android-commandlinetools`; pass `--sdk` elsewhere. Do not run Python with `-O`, `-OO` or `PYTHONOPTIMIZE`.

Prepare the exact external inputs listed in [materials](docs/materials.md). The APK alone is insufficient to restore the registered core.

```sh
python3 tools/build.py \
  --input-root /path/to/local-input-root \
  --visual-source /path/to/asm_hero_electrowizard_battle.json \
  --apktool /path/to/apktool-3.0.3.jar \
  --sdk /path/to/android-sdk
```

Outputs are under `build/local/`: `ReplayComponent.apk`, `resources.pack`, `resource-registration.json`, `replay-rules.zip`, `replay-visuals.zip`, and `build-proof.json`. The build validates all registered inputs, restores the exact registered core, compiles Java/native code, creates a locally signed APK, verifies the signature, and compares the final embedded rule/visual bundles. A local development key is created under ignored `.local/`. It is not a release credential.

`build-proof.json` records the generated pack's own digest and size. `metadata/public_resources.json` preserves the source main registration as provenance; it is not substituted for the freshly built pack's identity. Rebuilding the pack with the complete registered rules can produce different archive bytes from the historical main distribution.

The builder deliberately refuses an existing `build/local/decoded` directory. Preserve the proof/logs needed for review, then remove the rebuildable decoded APK and unsigned/aligned intermediates before rerunning. No source tree or external input is deleted by the builder.

## Run host checks

```sh
python3 tests/check.py
```

The runner creates synthetic fixtures under `build/local/tests`. On first run it downloads the test-only `org.json:json:20180813` jar and checks its pinned SHA-256 before use. It covers exact compatibility mappings and rejection cases, rule/visual failure cleanup, archive determinism, unsafe paths, the actual 2 GiB expansion boundary, 12 native terminal-result fixtures and optimized-Python rejection. These are host checks; device playback and lifecycle evidence are recorded separately.

## Play a local replay

Use an existing authorized emulator or an arm64 Android device. Commands below require an explicit serial; they do not create an AVD.

```sh
adb -s SERIAL install -r build/local/ReplayComponent.apk
adb -s SERIAL shell am start -n org.crreplay.example/cn.crhistory.player.ExampleActivity
adb -s SERIAL push build/local/resources.pack /sdcard/Android/data/org.crreplay.example/files/resources.pack
adb -s SERIAL push /path/to/private/replay.json /sdcard/Android/data/org.crreplay.example/files/replay.json
```

Tap **Play local replay**. The example validates/imports the pack, prepares a separate private run directory, preserves the input, selects registered rules and launches the native game host in `:replay`. Returning and starting another replay reclaims an exited run after recording a local outcome under `files/replay/evidence`. Full telemetry/result files remain in the active run until reclamation; export them before starting the next run if needed.

Input errors and unknown combinations are rejected. Expected crowns are not synthesized by the example; provide independent expectations when validating a real replay. [Compatibility](docs/compatibility.md) describes the inherited limits. Matching crowns does not demonstrate process fidelity.

## Review state

See [review](docs/review.md) for the current build, test, device and distribution status. The repository must remain private until a separate public-release decision. Apache-2.0 permits commercial use of the covered source; it grants no rights to game materials or modified game clients.
