# Changelog

## 1.0.0 — First stable release

First stable user release of CAMP Checklist for Minecraft 1.21.1 on NeoForge 21.1.219.

- Includes the CAMP four-tab preset for Create, Mekanism, Applied Energistics 2, and PneumaticCraft.
- Provides datapack-defined, shared-world checklist progress with automatic and manual goals.
- Includes the native Condition Framework, addon condition registration API, persistent native node state, typed recursive presentation snapshots, and exact dependency invalidation API.
- Resolves Checklist layout clipping at the default 856×512 Auto GUI window and stabilizes opening after resource reload.

### Compatibility

- Storage format: 1
- Network protocol: 2
- Public API major: 1
- Requires Minecraft 1.21.1, NeoForge 21.1.219 or newer, and LDLib2 2.2.38.a or newer.

### Deferred and known limitations

- Phase D administrator commands and external condition-control API are optional framework work and are not part of 1.0.0.
- Card grab-drag remains separately unverified with automation; mouse-wheel and scrollbar behavior were manually verified.
