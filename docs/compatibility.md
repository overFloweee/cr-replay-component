# Inherited compatibility boundary

Baseline: cr-battle-history main `d815abb34bbb3847ca2fec95df3096059ea7dda0`, not the later research branch. The native core is fixed to the registered restored 38.4.2 core. Input payload validation, supported commands and generated static mappings remain those of that baseline.

The bundled rule snapshots are 38.4.3, 38.4.5, 38.4.6, 38.4.8, 38.4.9, 38.4.10 and 38.4.11. See `metadata/rules_registry.json`. Two inherited aliases map `(38.4.7, 7)` and `(38.4.7, 8)` to 38.4.8. These are preserved decisions from main; no new device evidence in this repository expands them. The source-map records the exact imported registry and alias file hashes.

Presence in a registry or passing a synthetic mapping test is not evidence that every battle or mode replays accurately. In particular the main metadata records 38.4.11 candidate compatibility as not ready. No blanket compatibility claim or public admission is made for it. Unknown versions, engine conflicts, unsupported commands and malformed identities are rejected.

This example uses bundled rules only. It has no authenticated remote catalog, resource downloader, account authorization, archive service or compatibility approval service. `ReplayCompatibility` is retained and host-tested as a component, while the example passes no dynamic catalog. The later research trust/signature and material-pinning changes are outside this extraction.

The inherited bridge includes the main crown-tower classification fix and its 12 synthetic result checks. Crown agreement is one terminal observation; it is not an independent verification of combat timing, object trajectories or complete visual fidelity.

Local resource preparation validates the complete archive and per-entry digests. It caches a process-local ready decision and reuses the extracted private update tree. This is not the later research implementation that seals materials against mutation during playback. Constructor reads, non-PLT reads, all-resource immutability and exhaustive gameplay equivalence have not been established here.
