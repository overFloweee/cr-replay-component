# Private review checkpoint — 2026-10-23

The component is extracted from fixed main `d815abb34bbb3847ca2fec95df3096059ea7dda0`, under Apache-2.0, with fresh history and no Camp backend dependency. It remains a private review candidate.

| Area | Evidence / status |
| --- | --- |
| Source origin | Complete original-file SHA-256 map in `source-map.json`; three deferred scripts traced and documented in `provenance.md`; FirstLight LICENSE/NOTICE/origin retained |
| Build | Clean local Java, arm64 native, D8, Apktool, zip alignment and signature verification completed; [build proof](evidence/build-proof.json) binds all 23 build source inputs |
| Exact materials | Registered APK and all ten engine inputs verified; recovered core length/digest matched; seven rule snapshots and visual overlay built with their existing checks |
| Compatibility | Host static/dynamic checks passed, with unknown identities, versions, engines, malformed catalogs and unsupported commands rejected |
| Rules | 29 host checks passed, including all registered versions in sequence, malformed archives, failure cleanup and visual separation |
| Resource boundaries | Real expanded streams at 2 GiB−1 and 2 GiB accepted; 2 GiB+1 rejected; traversal/executable paths rejected |
| Native terminal result | 12 synthetic checks passed against the actual bridge `result()` body |
| Archive builder | Synthetic content preservation, stable bytes across timestamps/order/permissions, compression preservation, code exclusion and changed-content identity passed |
| Python optimization | `-O` and `-OO` rejected before core recovery; all original integrity assertions retained |
| Device playback | Pending. No emulator was present; creating a temporary AVD requires the user's explicit approval under the machine's existing rule. The creation request is pending. No device pass is claimed. |
| Lifecycle and fidelity | New local-host launch/return/restart and real terminal playback remain pending device verification. Matching terminal crowns would not prove complete process fidelity. |
| Distribution | Source-only private repository. [Source audit](evidence/source-audit.json) records the staged source scan; the final object scan is retained locally. No generated APK/pack/game input/replay/key/old Git history is included. |

The local build demonstrates a complete build with the exact external materials already present on the verification machine. A fresh user cannot reproduce playback from this repository alone: runtime snapshots, historical materials and a registered animation input must be supplied with the necessary rights. Their capture-time APK/process provenance is not complete. This is an explicit remaining limit, not a downloadable dependency bundled into the source release.

The example retains main's bounds and process-isolation mechanics. It does not inherit the later research branch's all-material pinning work, authorization/download business cases or larger multi-version validation. No changes are being merged back to the product main or deployed.

The final local APK is 101,526,222 bytes, SHA-256 `21d68d4919c1c0a98a84b53fb71a535b7a3142b4d7925c0730e6091802d7bc67`. The generated pack is 1,446,719,022 bytes, SHA-256 `9d7566c1f601e3058cdb62ac64fc54f2ff8fdab62bc39712ce2d1774572fb776`, with 11,707 indexed files and 120,401,257 bytes in the nested update. Both remain ignored local outputs. [Host check output](evidence/host-checks.txt) contains the final passing results; [source comparison](evidence/source-comparison.json) identifies the exact upstream files examined.
