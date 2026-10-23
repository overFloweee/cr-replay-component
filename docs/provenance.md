# Source provenance and distribution boundary

This repository starts a new Git history. The extraction baseline is **cr-battle-history main `d815abb34bbb3847ca2fec95df3096059ea7dda0`**. Later research-branch code was not imported. `source-map.json` lists each copied file or selected-fragment source and the SHA-256 of the complete original file at that commit. These hashes identify origins; modified standalone files intentionally have different hashes.

## The three deferred scripts

| Script | Traceable source | What the code does | Standalone change and boundary |
| --- | --- | --- | --- |
| `tools/official_core.py` | `tools/single-app/official_core.py`, first recorded in `26971968508d35826616654f8f319bf8a8d80144` (2026-10-10, overFloweee); source SHA in source-map | Reconstructs one registered ELF from the supplied APK library, RX snapshot, dynamic symbols and export metadata; validates all inputs and the final exact digest/size | Adds rejection of Python optimization so required assertions cannot disappear. No runtime dump or recovered library is embedded. The file is tool source; its binary inputs/outputs are excluded. |
| `tools/official_engine.py` | `tools/single-app/official_engine.py`, first recorded in `26971968508d35826616654f8f319bf8a8d80144`; later main revisions are part of the fixed baseline | Invokes Apktool, restores the core, combines registered snapshots, and inserts the offline native/Java bootstrap into a locally decoded game host | Changes metadata/input paths and removes the Camp application initialization. Decoded or modified game smali and the produced APK remain ignored build outputs. It does not grant a right to distribute a modified client. |
| `tools/replay_visuals.py` | `tools/single-app/replay_visuals.py`, first recorded in `2fe5251c5558430648d25ae31ab1ccbec147ec62` (2026-10-19, overFloweee) | Verifies a registered animation input, baseline SCDB and dependent GLBs; changes precisely one SCDB asset row and checks the other tables | Reads the animation from an explicit external path. The official animation JSON, SCDB, GLBs and generated overlay stay out of Git. |

The main Git records and complete script bodies were inspected. Git author records establish where this project obtained the code; they do not prove all underlying reverse-engineering knowledge or material acquisition rights. No additional third-party license header was found in these three main files. Their traceable project source is licensed here by the owner's Apache-2.0 instruction; this statement does not license the game or claim a legal review of client modification or runtime extraction.

## FirstLight lineage

The source project records upstream [FirstLight_CR](https://github.com/Jaasssoooonnnnn/FirstLight_CR) commit `3ade158c52bf33de3ffcea600ce380946b7de0d8`. Its fixed Git tree, `native_runner/offline_build.py`, `native_runner/probe/cr_replay_probe.cpp` and LICENSE were inspected for this extraction. The fetched LICENSE is byte-identical to this repository's Apache-2.0 LICENSE. The upstream tree does not contain files named `official_core.py`, `official_engine.py` or `replay_visuals.py`; that fact alone would not establish independent authorship.

The offline loader/bootstrap approach and native replay research have FirstLight lineage. Applicable attribution is retained conservatively in `NOTICE`, `licenses/FirstLight-NOTICE` and `licenses/FirstLight-ORIGIN.json`. The large upstream probe, its training/telemetry modules and old standalone build system are not copied into this component. We do not claim that compiling only this bridge removes derivation obligations.

The native bridge's runtime logic is unchanged from the fixed main file, including its terminal tower fix; only an attribution header was added. Its calibration targets one exact restored core. Preserving its source or Apache attribution does not establish compatibility with other binary versions.

The proposed source release includes reverse-engineering implementation details: fixed core addresses, structure offsets, short instruction signatures used to validate hook targets, inline-hook machinery, offline event handling and generated smali patches. Excluding complete game binaries does not mean that all source is generic platform glue or that no game-derived byte sequences appear in it. Attribution and the source map record the traceable lineage; they are not a determination of rights in every signature, generated fragment or underlying technique.

## Extraction changes

- Java integration keeps the original JNI class names to preserve native linkage. Camp-specific resources, account/network services and diagnostics are replaced by `LocalResources`, a local example activity, and local outcome retention. Run UUIDs, process ownership checks, launch tickets, bounds and path/digest validation remain.
- `tools/host_patch.py` contains selected host-isolation patch fragments from main `tools/single-app/build-proof.py`. It generates modifications in the local decoded output; no full game class is distributed.
- `tools/build.py`, `LocalResources.java`, `ExampleActivity.java` and `tests/check.py` are standalone glue. The build uses the actual newly generated pack identity. It does not stamp a historical pack's digest onto different bytes.
- Existing compatibility/rule/resource/native checks are extracted or adapted. `tests/check_archive.py` removes the source project's COS upload client and keeps the local archive checks. Fixtures contain synthetic bytes and generated JSON, not real game captures.
- The source map records metadata and tests as well as implementation. Files changed during extraction carry a modification notice. The initial standalone commit is reviewable without the private source repository's Git history.

## Dependencies and release scope

| Dependency | Use | Included here |
| --- | --- | --- |
| FirstLight CR | Research lineage / applicable source attribution, Apache-2.0 | LICENSE, NOTICE and origin record; independently adapted bridge/integration |
| Python/JDK/Android SDK and NDK | Local build and platform APIs | References only; user installs licensed toolchains |
| Apktool 3.0.3 | External local command to decode/build the supplied APK; upstream GPL-3.0 | No jar or source copied; not linked into the component |
| `org.json:json:20180813` | JVM test runtime, pinned SHA-256 in test driver | Downloaded only into ignored build output; its upstream JSON license includes the good-not-evil clause, so this is not represented as Apache-licensed component source |
| Game APK/core/rules/visual data | Local playback inputs and derivatives | None in source Git or Git history |

The Apache-2.0 grant covers source that can be licensed by its contributors and applicable Apache third-party code. It permits commercial use under its terms; there is no added noncommercial restriction. Game materials remain subject to their owners' rights. A public source release still needs a separate decision; no public APK, materials download service, public CI artifact or modified-client release is approved by this private review.
