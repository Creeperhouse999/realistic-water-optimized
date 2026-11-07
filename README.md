# Realistic Water - OPTIMIZED

> **This is a modified, unofficial version of [Realistic Water](https://github.com/sskaffy/Realistic-Water) by Skaffy. It is not the original mod.**
> All credit for the simulation, rendering and the mod itself goes to **Skaffy**. This fork only changes defaults, adds a control panel and makes big floods run on ordinary gaming hardware.
> Distributed under the original mod's license (see [LICENSE](LICENSE)). Free, never sold, never paywalled.

Realistic Water simulates water, lava, slime, sand and leaves as real fluids on the GPU. It looks incredible, but at default settings one glass box of water can bring a gaming laptop to 1 FPS.
This fork keeps the look and makes it playable:

| | Original 1.3 | This fork (Tsunami preset) |
|---|---|---|
| 29-spring tsunami into a village, 4K, RTX 4070 laptop | crash / ~1 FPS | **60 FPS** |
| 5×5×5 sand drop | ~0 FPS already at half the original sand resolution | **60 FPS** |
| Water reaching the ocean | simulation spreads across the sea floor | absorbed by the ocean |

**Minecraft 26.3, Fabric.** Requires Fabric API. Sodium is optional (adds a settings page).

---

## Features

### Control panel
Press **K**, type **`/waterpanel`** or **`/water gui`**, or use the button on the Realistic Water page in Sodium's video settings.

| Tab | What's there |
|---|---|
| **Home** | Water preset, render and simulation distance, frame limit, water detail, foam, Auto FPS switch, live "what's moving" stats, *Remove water* / *Remove everything* |
| **Auto FPS** | The FPS governor: on/off, target FPS, what it may change, messages, the 7 quality levels |
| **World** | Turn vanilla water, lava and falling sand into realistic ones (game rules of the current world) |
| **Water** | The water settings worth touching, with performance cost in every tooltip |
| **Materials** | Lava, slime, sand, leaves and the water pump |
| **All settings** | Every setting of the mod by category (replaces the original long list) |

### Presets
| Preset | For |
|---|---|
| **Lowest** | Blocky water at 1 cell per block when nothing else helps |
| **Ocean** | Huge bodies of water and converted worlds: very cheap simulation, smooth surface |
| **Tsunami** *(default)* | Smooth floods and waves, no foam particles |
| **Balanced** | Finer water with foam and spray |
| **Quality** | Close to the original look. Heavy: keep floods small |

### Auto FPS
Like the dynamic settings in flight simulators: pick a target (say 60 FPS) and it adjusts render distance, clouds, particles, shadows and water quality in the background.

- Steps down fast when FPS drops below 90% of the target, two steps if it falls under half.
- Tries one step up after 20 s steady at the target. A step that causes a drop is blocked for 30 s, doubling up to 5 min.
- Never changes water detail while water is moving, so floods don't vanish mid-wave.
- Never touches mipmaps, texture filtering or biome blend (they reload everything).
- Water detail never goes above your own water settings. View distance can: set **Above my view: Never** to stop that, and **Min view** to keep it from dropping too low.
- Turning it off, or quitting, restores your own settings (also after a crash).

`/autofps on`, `/autofps off`, `/autofps target 60`, `/autofps` (status).

### Realistic world (experimental)
Game rules under **Create World → More → Game Rules → Realistic Water**, in the panel's World tab, or `/gamerule`:

| Game rule | Default | |
|---|---|---|
| `realistic-water:convert_water` | false | Lakes, ponds and waterfalls near players become realistic water |
| `realistic-water:convert_lava` | false | Lava pools and lava falls near players become realistic lava |
| `realistic-water:convert_sand` | false | Sand with nothing solid under it becomes realistic sand |
| `realistic-water:convert_radius` | 12 | How far around each player to look (4–48) |
| `realistic-water:convert_per_second` | 200 | Average blocks converted per second |
| `realistic-water:convert_max_body` | 4096 | Biggest water or lava body converted (64–16384) |

How it works:
- Each lake is converted **whole, in one tick**. If only part of a lake were converted, the realistic part would drain into the vanilla part (the ocean sink below absorbs it).
- Bodies bigger than `convert_max_body`, or reaching into unloaded chunks, stay vanilla. That includes oceans and most rivers.
- Sources that feed a fall, like the top of a waterfall, become springs, so the fall keeps running. Flowing vanilla water is removed.
- Kelp, seagrass and bubble columns turn into water. Waterlogged blocks are drained.
- Converted blocks stay converted. Pair it with the **Ocean** preset.

### Performance changes under the hood
- **Ocean sink:** realistic fluid that reaches vanilla water is absorbed instead of spreading the simulation box across the sea floor. Lava still turns water into cobblestone.
- **Half-resolution fluid passes above 1440p:** 4K keeps full detail on screen, only the water's internal images are half size.
- **GPU memory guard:** huge floods lower the surface mesh detail for that region instead of failing a 1.2 GB allocation and switching the simulation off. The limit scales with your card's video memory.
- **Cheaper defaults:** water resolution 2, at most 2 physics steps per frame, no foam, lighter reflections (20 SSR steps instead of 48) and shadows (24 instead of 64), lighter sand, leaves, steam, lava and slime.

---

## Install
1. Install [Fabric](https://fabricmc.net/) for Minecraft 26.3 and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download `realistic-water-optimized-1.3-perf.2.jar` from [Releases](../../releases).
3. Put it in `mods/`, **instead of** the original Realistic Water jar (not next to it).

It starts on the Tsunami preset; every other preset is one click away in the control panel (`K`).

## Building
See [docs/BUILDING.md](docs/BUILDING.md). Short version: unpack the original 1.3 jar into `extracted/`, point `tools/cp.txt` at your Minecraft and library jars, run `sh tools/build.sh`.

## Reporting problems
Use the [issue templates](../../issues/new/choose). Please include `/water stats` output and your GPU. Problems that also happen with the **original** mod belong in [the original project](https://github.com/sskaffy/Realistic-Water).

## Credits
- **Skaffy**: [Realistic Water](https://github.com/sskaffy/Realistic-Water), the original mod and everything that makes the water real.
- This fork: presets, control panel, Auto FPS, world conversion, ocean sink, render scaling.

## License
Same license as the original mod, notice kept intact: [LICENSE](LICENSE). In short: free to use and include in modpacks; forks must credit Skaffy, link the original and say they're modified; nothing may be sold or paywalled.
