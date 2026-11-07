# Contributing

Thanks for helping! A few rules that come from the original mod's license:

- Keep the credit to **Skaffy** and the link to <https://github.com/sskaffy/Realistic-Water> everywhere they appear (README, `fabric.mod.json`, mod description).
- Keep the "modified, unofficial" notice.
- Nothing in this repository may be sold or put behind a paywall.

## Workflow
1. Set up the build once: [docs/BUILDING.md](docs/BUILDING.md).
2. Change code in `src/` or the patch scripts in `tools/`.
3. `sh tools/build.sh`, then test in a **new** world: glass boxes of `realistic-water:realistic_water` and `/water stats` are the quickest benchmarks.
4. Add a line to `CHANGELOG.md` and open a pull request.

## Code layout
- `src/me/skaffy/perf/`: everything new in the fork (panel, presets, Auto FPS, world conversion, memory guard).
- `src/me/skaffy/client/`: original classes with fork changes (renderer scaling, ocean sink, Sodium page).
- `tools/Patcher.java`: bytecode patches and new default values.
