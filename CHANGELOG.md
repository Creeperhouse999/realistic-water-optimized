# Changelog

All versions are forks of Realistic Water **1.3** by Skaffy for Minecraft 26.3.

## 1.3-perf.2 (unreleased)
### Added
- Tabbed control panel (Home, Auto FPS, World, Water, Materials, All settings). It replaces the original settings list: `K`, `/waterpanel`, `/water gui` and both Sodium buttons open it.
- **Ocean** preset for huge water bodies.
- Realistic world game rules (`realistic-water:convert_water`, `convert_lava`, `convert_sand`, `convert_radius`, `convert_per_second`, `convert_max_body`), visible in Create World → Game Rules and in the panel's World tab. Lakes are converted whole (a half-converted lake would drain into its vanilla half); oceans stay vanilla; waterfall tops become springs.
- Auto FPS options: **Min view** (never below N chunks) and **Above my view** (allow or forbid view distance above your own).
- GPU memory guard: very large floods lower surface detail for that region instead of failing to allocate GPU memory. The limit scales with video memory (1/14 of it, 96–576 MB per region).
- Offline tests (`sh tools/test.sh`): presets, panel setting names, patched defaults, stats parsing, memory guard.

### Fixed
- Water vanished while Auto FPS changed quality: water detail now only changes while no water is moving. Lowering it is forced after 45 s (springs never stop moving).
- Auto FPS could leave its temporary settings in options.txt and the water settings file (closing the panel saved them; a crash kept them). Your own settings are now also stored in the panel config while Auto FPS runs and come back after a crash. Water settings you change while Auto FPS is on are kept for when it is off.
- Auto FPS climbed above your own water settings while the water was calm (up to resolution 4 with foam), so the next flood crashed it to the lowest level. It now never goes above your own water settings, and near springs water detail comes back after 3 minutes.
- An ocean next to a lake could block that lake from converting (the skip list was per chunk, now per block).
- Long help text in the panel was cut off after two lines.
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
