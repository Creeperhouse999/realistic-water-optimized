<div align="center">

# Realistic Water - OPTIMIZED

**Skaffy's Realistic Water, made playable.**
Big floods and tsunamis at 60 FPS on a gaming laptop, a control panel instead of a 100-setting list,
Auto FPS that keeps your frame rate, and an option to turn the lakes of your world realistic.

[![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=flat-square)](https://minecraft.wiki/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)](https://fabricmc.net/use/installer/)
[![Requires Fabric API](https://img.shields.io/badge/Requires-Fabric%20API-3B3B3B?style=flat-square)](https://modrinth.com/mod/fabric-api)
[![Based on Realistic Water 1.3](https://img.shields.io/badge/Based%20on-Realistic%20Water%201.3-2E86C1?style=flat-square)](https://github.com/sskaffy/Realistic-Water)
[![Release 1.3-perf.3](https://img.shields.io/badge/Release-1.3--perf.3-5865F2?style=flat-square)](../../releases)
[![Modrinth](https://img.shields.io/badge/Modrinth-Realistic%20Water%20OPTIMIZED-1BD96A?style=flat-square&logo=modrinth&logoColor=white)](https://modrinth.com/mod/realistic-water-optimized)
[![Made with Claude](https://img.shields.io/badge/Made%20with-Claude-D97757?style=flat-square)](https://claude.com/claude-code)

[Download](https://modrinth.com/mod/realistic-water-optimized) ·
[Install](#install) ·
[How to use](#how-to-use) ·
[Roadmap](#roadmap) ·
[Build from source](#build-from-source) ·
[Report a bug](../../issues/new/choose)

</div>

> [!IMPORTANT]
> **This is a modified, unofficial version of [Realistic Water](https://github.com/sskaffy/Realistic-Water) by Skaffy.**
> All credit for the simulation, the rendering and the mod itself goes to **Skaffy**. Please don't report
> problems with this fork to the original project — use this repo's [issues](../../issues) instead.
> Distributed under the original mod's license ([LICENSE](LICENSE)): free, never sold, never paywalled.

> [!NOTE]
> **Made with AI.** The code is written with [Claude](https://claude.com/claude-code) (Anthropic's AI).
> Every change is still reviewed, built, checked by the offline tests (`sh tools/test.sh`) and tested in
> game by the maintainer before it goes into a release. Anything not yet tested in game is marked 🧪 below
> and labelled `untested` in the issues.

---

## What is it?

[Realistic Water](https://github.com/sskaffy/Realistic-Water) simulates water, lava, slime, sand and leaves
as real fluids on the GPU. It looks incredible, but at its default settings one glass box of water can
bring a gaming laptop to 1 FPS.

**Realistic Water - OPTIMIZED replaces the original jar** and keeps the look while making it playable:

| With the original 1.3 | With OPTIMIZED (Tsunami preset) |
|---|---|
| 29-spring tsunami into a village, 4K, RTX 4070 laptop: crash or ~1 FPS | **60 FPS** |
| 5×5×5 sand drop: ~0 FPS even at half sand resolution | **60 FPS** |
| Water reaching the ocean spreads the simulation across the sea floor | The ocean absorbs it |
| Over 100 settings in one long list | A tabbed control panel with presets |
| You tune everything by hand | Auto FPS adjusts quality to hold your target |
| Huge floods could switch the simulation off (1.2 GB GPU buffer) | Surface detail drops for that flood instead |
| Only water you place is realistic | Optional: lakes, waterfalls and lava pools of your world turn realistic |

## Features

Legend: ✅ tested in game · 🧪 new, passes the offline tests but not yet tested in game · 🗓️ planned

### Control panel ✅
Press **K**, type **`/waterpanel`** or **`/water gui`**, or use the button on the Realistic Water page in
Sodium's video settings.

- **Home:** water preset, view and simulation distance, frame limit, water detail, foam, the Auto FPS switch,
  live "what's moving" stats, *Remove water* and *Remove everything*. ✅
- **Auto FPS:** on/off, target FPS, what it may change, messages, limits and the 7 quality levels. ✅
- **World:** the realistic world rules of the current world (singleplayer). ✅
- **Water** and **Materials:** the settings worth touching, with the performance cost in every tooltip. ✅
- **All settings:** every setting of the mod by category. It replaces the original long list. ✅
- Long help text wraps onto extra lines instead of being cut off. 🧪

### Presets ✅

| Preset | For |
|---|---|
| **Lowest** | Blocky water at 1 cell per block, when nothing else helps |
| **Ocean** | Big bodies of water and converted lakes: very cheap simulation, smooth surface |
| **Tsunami** *(default)* | Smooth floods and waves, no foam particles |
| **Balanced** | Finer water with foam and spray |
| **Quality** | Close to the original look. Heavy: keep floods small |

### Auto FPS ✅
Like the dynamic settings in flight simulators: pick a target (say 60 FPS) and it adjusts view distance,
clouds, particles, shadows and water detail in the background.

- Steps down fast when FPS drops below 90% of the target, two steps if it falls under half. ✅
- Tries one step up after 20 s steady at the target. A step that causes a drop is blocked for 30 s,
  doubling up to 5 min. ✅
- Only changes water detail when no realistic water exists nearby. Changing it rebuilds all water, which made
  floods vanish and full tanks burst through their walls; now it uses its other levers instead. ✅
- Never sets water detail above your own water settings. 🧪
- **Min view** (never below N chunks) and **Above my view** (allow or forbid going above your own view
  distance). 🧪
- Turning it off, or quitting, restores your own settings. ✅ Also after a crash. 🧪
- Never touches mipmaps, texture filtering or biome blend (they reload everything).

### Realistic world ✅ *(experimental)*
Game rules under **Create World → More → Game Rules → Realistic Water**, in the panel's World tab, or `/gamerule`:

| Game rule | Default | |
|---|---|---|
| `realistic-water:convert_water` | false | Lakes, ponds and waterfalls near players become realistic water |
| `realistic-water:convert_lava` | false | Lava pools and lava falls near players become realistic lava |
| `realistic-water:convert_sand` | false | Sand with nothing solid under it becomes realistic sand 🧪 |
| `realistic-water:convert_radius` | 12 | How far around each player to look (4–48) |
| `realistic-water:convert_per_second` | 200 | Average blocks converted per second |
| `realistic-water:convert_max_body` | 4096 | Biggest water or lava body converted (64–16384) |

- Each lake is converted **whole, in one tick**: a 2,000-block lake converts with no visible hitch. ✅
- Bodies bigger than `convert_max_body`, or reaching into unloaded chunks, stay vanilla, so oceans and most
  rivers stay as they are. ✅
- The top of a waterfall becomes a spring, so the fall keeps running. ✅
- Kelp, seagrass and bubble columns turn into water; waterlogged blocks are drained.
- Converted blocks stay converted. Pair it with the **Ocean** preset.

### Under the hood
- **Ocean sink:** realistic fluid that reaches vanilla water is absorbed instead of spreading the simulation
  across the sea floor. Lava still turns water into cobblestone. ✅
- **Half-resolution fluid passes above 1440p:** 4K keeps full detail on screen; only the water's internal
  images are half size. ✅
- **GPU memory guard:** huge floods lower the surface detail for that region instead of failing to allocate
  GPU memory. ✅ The limit now scales with your card's video memory (96–576 MB per flood). 🧪
- **Cheaper defaults:** water resolution 2, at most 2 physics steps per frame, no foam, lighter reflections
  (20 steps instead of 48) and shadows (24 instead of 64), lighter sand, leaves, steam, lava and slime. ✅

## Requirements

| | Version |
|---|---|
| Minecraft | **26.3** (Java Edition) |
| Mod loader | [Fabric Loader](https://fabricmc.net/use/installer/) 0.19 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | **Required** |
| Original Realistic Water | **Not needed**, this jar replaces it. Don't install both. |
| [Sodium](https://modrinth.com/mod/sodium) | Optional: adds a Realistic Water page to the video settings |
| Graphics | A GPU with Vulkan support (the simulation runs on the GPU) |

### Tested on

| | |
|---|---|
| Laptop | MSI Katana 15 B13VGK |
| CPU | Intel Core i7-13620H (10 cores, 16 threads) |
| GPU | NVIDIA GeForce RTX 4070 Laptop GPU, 8 GB |
| RAM | 16 GB |
| Display | 3840×2160, 240 Hz |
| OS | Windows 11 Pro |

All FPS numbers in this README are from this machine. Results on other hardware are welcome in the
[performance issue form](../../issues/new/choose).

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **26.3** (or create a Fabric
   26.3 profile in your launcher, e.g. Modrinth App or Prism).
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3.
3. Download `realistic-water-optimized-x.y.jar` from [Modrinth](https://modrinth.com/mod/realistic-water-optimized)
   or [GitHub Releases](../../releases).
4. Put both in your `mods` folder. **Remove the original Realistic Water jar** if you had it.
5. Start the game, join a world and press **K**.

Your `mods` folder should look like this:

```
mods/
├── fabric-api-x.y.z+26.3.jar
├── realistic-water-optimized-x.y.jar
└── sodium-fabric-x.y.z+mc26.3.jar   (optional)
```

> [!TIP]
> The jar starts on the **Tsunami** preset. If your PC struggles, open the panel and pick **Lowest**, or turn
> on **Auto FPS** and let it find the right level.

## How to use

### Keys and commands

| Key / command | What it does |
|---|---|
| **K** | Open / close the control panel (change it in *Options → Controls → Key Binds*) |
| `/waterpanel`, `/water gui` | Open the control panel |
| `/water stats` | What's simulated right now and what it costs |
| `/water remove [material]` | Remove simulated realistic fluid (all, or one material like `sand`) |
| `/autofps on` · `off` · `target 60` | Control Auto FPS; `/autofps` alone shows its status |

### Testing big floods
Build a glass box, fill it with `realistic-water:realistic_water` (`/fill`) and break a wall. For a waterfall
that never stops, place `realistic-water:realistic_water[infinite=true]`. Keep `/water stats` open to see
the cost.

### Settings files
- `config/realistic-water.properties`: the mod's settings (only values that differ from the defaults).
- `config/realistic-water-panel.properties`: panel and Auto FPS settings.

## Roadmap

| Version | Focus | Status |
|---|---|---|
| **1.3-perf.1** | Cheaper defaults, half-resolution passes, ocean sink, first control panel and Auto FPS | ✅ Released |
| **1.3-perf.2** | Tabbed panel, presets, Ocean preset, realistic world rules, memory guard, Auto FPS fixes | ✅ Released |
| **1.3-perf.3** | Auto FPS no longer bursts full tanks when changing water detail | ✅ Released |
| **next** | In-game checks of the 🧪 items, sand simulations that grow too big ([#5](../../issues/5)), taller underground fountains ([#6](../../issues/6)) | 🗓️ Planned |
| **later** | Memory-guard tuning for 4–6 GB cards ([#4](../../issues/4)), Quality preset testing on a stronger PC | 🗓️ Planned |

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Build from source

You need **JDK 25**, a POSIX shell (Git Bash on Windows), Python 3 and the original `realistic-water-1.3.jar`.
The fork is built by patching the original jar, so the original isn't stored in this repo.

```sh
sh tools/build.sh   # builds the jars into the project root
sh tools/test.sh    # offline checks, no game needed
```

Full setup: [docs/BUILDING.md](docs/BUILDING.md).

## Reporting problems

Use the [issue templates](../../issues/new/choose). Please include `/water stats` output and your GPU.
Problems that also happen with the **original** mod belong in
[the original project](https://github.com/sskaffy/Realistic-Water).

## Credits

- **Skaffy**: [Realistic Water](https://github.com/sskaffy/Realistic-Water), the original mod and everything
  that makes the water real.
- **Creeperhouse999**: this fork, maintenance and in-game testing.
- **Claude** (Anthropic): code.

## License

Same license as the original mod, notice kept intact: [LICENSE](LICENSE). In short: free to use and include
in modpacks; forks must credit Skaffy, link the original and say they're modified; nothing may be sold or
paywalled.
