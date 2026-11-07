**Realistic Water - OPTIMIZED** is an unofficial, modified version of
[Realistic Water](https://github.com/sskaffy/Realistic-Water) by **Skaffy**. All credit for the fluid
simulation, the rendering and the mod itself goes to Skaffy. This fork keeps the look and makes it
playable on ordinary gaming hardware.

> This is **not** the original mod and not made by Skaffy. Please report problems with this fork
> [here](https://github.com/Creeperhouse999/realistic-water-optimized/issues), not to the original project.
> Icon based on the original mod's icon.

> **Made with AI.** The code is written with Claude (Anthropic's AI). Every change is reviewed, checked by
> offline tests and tested in game before it goes into a release.

## Why

Realistic Water simulates water, lava, slime, sand and leaves as real fluids on the GPU. It looks incredible,
but at its default settings one glass box of water can bring a gaming laptop to 1 FPS.

| With the original 1.3 | With OPTIMIZED |
|---|---|
| 29-spring tsunami into a village at 4K: crash or ~1 FPS | **60 FPS** |
| 5×5×5 sand drop: ~0 FPS | **60 FPS** |
| Water reaching the ocean spreads the simulation across the sea floor | The ocean absorbs it |
| Over 100 settings in one long list | A tabbed control panel with presets |
| Huge floods could switch the simulation off | Surface detail drops for that flood instead |

Measured on an MSI Katana 15 (i7-13620H, RTX 4070 Laptop 8 GB, 16 GB RAM) at 3840×2160.

## You also need

- Minecraft **26.3** with **Fabric Loader 0.19+**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- A GPU with Vulkan support (the simulation runs on the GPU)
- Optional: [Sodium](https://modrinth.com/mod/sodium) adds a Realistic Water page to the video settings

**Don't install the original Realistic Water next to it.** This jar replaces it.

## What it adds

- **Control panel** (press **K**, or `/waterpanel`): presets, view distance, water detail, foam, live stats,
  *Remove water*, and every setting of the mod in tabs, with the performance cost in every tooltip.
- **Presets**: Lowest, Ocean, Tsunami (default), Balanced, Quality. One click to switch.
- **Auto FPS**: pick a target frame rate and it adjusts view distance, clouds, particles and water detail in
  the background, like the dynamic settings in flight simulators. It never changes water detail mid-flood,
  never goes above your own water settings, and puts your settings back when you turn it off.
- **Realistic world** (experimental game rules): lakes, ponds, waterfalls and lava pools near you turn into
  realistic ones, one whole lake at a time. Oceans stay vanilla. The top of a waterfall becomes a spring that
  keeps flowing. Off by default; switch it on in *Create World → More → Game Rules* or the panel.
- **Ocean sink**: realistic water that flows into vanilla water is absorbed, so floods don't spread the
  simulation across the whole sea.
- **Under the hood**: half-resolution water passes above 1440p, lighter reflections and shadows, cheaper
  defaults for water, sand, leaves, lava and slime, and a GPU memory guard that scales with your card.

## Commands

| | |
|---|---|
| **K**, `/waterpanel` | Open the control panel |
| `/water stats` | What's simulated right now and what it costs |
| `/water remove` | Remove simulated fluid |
| `/autofps on` · `off` · `target 60` | Control Auto FPS |

## Links

- Source, changelog and building: [GitHub](https://github.com/Creeperhouse999/realistic-water-optimized)
- Bugs and ideas: [issues](https://github.com/Creeperhouse999/realistic-water-optimized/issues)
- The original mod by Skaffy: [Realistic Water](https://github.com/sskaffy/Realistic-Water)

Same license as the original mod: free, never sold, never paywalled. Forks must credit Skaffy.
