# Changelog

All versions are forks of Realistic Water **1.3** by Skaffy for Minecraft 26.3.

## 1.3-perf.2 (unreleased)
### Added
- Tabbed control panel (Home, Auto FPS, World, Water, Materials, All settings). It replaces the original settings list: `K`, `/waterpanel`, `/water gui` and both Sodium buttons open it.
- **Ocean** preset for huge water bodies.
- Realistic world game rules (`realistic-water:convert_water`, `convert_lava`, `convert_sand`, `convert_radius`, `convert_per_second`, `convert_max_body`), visible in Create World → Game Rules and in the panel's World tab. Lakes are converted whole (a half-converted lake would drain into its vanilla half); oceans stay vanilla; waterfall tops become springs.
- GPU memory guard: very large floods lower surface detail for that region instead of failing to allocate GPU memory.
- Offline tests (`sh tools/test.sh`): presets, panel setting names, patched defaults, stats parsing, memory guard.

### Fixed
- Water vanished while Auto FPS changed quality: water detail now only changes while no water is moving. Lowering it is forced after 45 s (springs never stop moving).
- The panel key was Page Up instead of K on Minecraft 26.3 (new key codes). Existing Page Up bindings are moved to K once.
- The Quality preset could switch the simulation off on big floods (1.2 GB GPU buffer). Quality now uses resolution 5 and the memory guard.

### Changed
- Sand looks fuller for a small drawing cost: 16 grains drawn per particle (was 8), slightly bigger grains, smoother pile surface.
- Shorter panel button labels.
- The Sodium page shows the panel buttons and 7 headline settings instead of 19 pages of raw options.

## 1.3-perf.1
### Added
- Presets Lowest / Tsunami / Balanced / Quality, control panel (K, `/waterpanel`, Sodium page button), Auto FPS (`/autofps`).
- Ocean sink: realistic fluid entering vanilla water is absorbed.
- Half-resolution fluid passes above 1440p.

### Changed
- Cheaper defaults: water resolution 2, 2 steps per frame, no foam, 20 SSR steps, 24 shadow steps, lighter sand, leaves, steam, lava and slime; light refresh budget 4096 → 1024 cells per frame.
