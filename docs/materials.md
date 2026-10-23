# External materials and reproducibility

All game inputs remain outside Git. Supply a local input root with:

```text
materials/apks/cr-cn-38.4.2.apk
materials/game-assets/replay-engine-cn-38.4.2-38.4.5/
  runtime-rx.bin
  runtime-dynsym.bin
  runtime-exports.json
  content-update.tar
  historical-content-38.4.3.tar
  content-rules-38.4.6.tar
  content-rules-38.4.8.tar
  content-rules-38.4.9.tar
  content-rules-38.4.10.tar
  content-rules-38.4.11.tar
```

Exact SHA-256 values, restored-core length and identity are in [`metadata/supported_engine.json`](../metadata/supported_engine.json). The source APK SHA-256 is `504b3e9bcfc597b22c99c1aabf932cbeb50db654acd61dca2dc5cfb6dfd3de52`; restored core is 30,559,184 bytes with SHA-256 `fe89f0eaf708e7dc270f971d06bdf36d99ac11c2478d370a9ddfd6025e2fd93b`. A build verifies these values; it does not infer a replacement version.

The separate `--visual-source` input is the exact `asm_hero_electrowizard_battle.json` expected by `tools/replay_visuals.py`. The script verifies SHA-256 `addcc26eac62564cc3eef922ee886bc71d9c827a7f411faded8fc377b102a838` (`SOURCE_SHA256`). The animation JSON and SCDB are game materials and are not vendored as source. The builder fails if the required input is missing or differs; it does not silently disable the overlay.

The input files are not downloadable from this repository. The local verification machine already held them from earlier work. Their presence establishes local reproducibility with those exact bytes; it does not establish acquisition rights or reproduction from only a public game APK. The capture-time APK/process provenance for the runtime dumps remains incomplete. Independently supplied materials must match the full registration and must be obtained and used with the necessary rights.

The tools generate a restored core, modified game-host smali, rule/visual bundles, a resource pack and an APK under ignored `build/local`. These outputs contain or derive from game material. They are not covered by the source-code release approval and must not be committed, attached to a public release or distributed by a source CI job.
