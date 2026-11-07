"""Patch fabric.mod.json and the English lang file of a build directory for the performance fork.

Usage: python -I meta_patch.py <build dir> <preset name>
"""
import json
import os
import sys

root, preset = sys.argv[1], sys.argv[2]

mod_path = os.path.join(root, "fabric.mod.json")
with open(mod_path, encoding="utf8") as f:
    mod = json.load(f)

VERSION = "1.3-perf.3"
mod["version"] = VERSION if preset == "tsunami" else VERSION + "-" + preset
mod["name"] = "Realistic Water - OPTIMIZED"
mod["description"] = (
    "MODIFIED, unofficial performance fork of Realistic Water by Skaffy "
    "(original: https://github.com/sskaffy/Realistic-Water). "
    "Adds faster presets, half-resolution water rendering above 1440p, an ocean sink, "
    "a tabbed control panel (K, /waterpanel, /water gui), Auto FPS (/autofps) and optional world "
    "conversion game rules. Not the original mod."
)
mod["authors"] = ["Skaffy (original author)", "performance fork"]
mod.setdefault("contact", {})["sources"] = "https://github.com/sskaffy/Realistic-Water"
client = mod["entrypoints"]["client"]
if "me.skaffy.perf.PerfClient" not in client:
    client.append("me.skaffy.perf.PerfClient")
main = mod["entrypoints"]["main"]
if "me.skaffy.perf.WorldConversion" not in main:
    main.append("me.skaffy.perf.WorldConversion")
with open(mod_path, "w", encoding="utf8") as f:
    json.dump(mod, f, indent="\t")

lang_path = os.path.join(root, "assets", "realistic-water", "lang", "en_us.json")
with open(lang_path, encoding="utf8") as f:
    lang = json.load(f)
lang["key.realistic-water.panel"] = "Open water control panel"
lang["key.category.realistic-water.main"] = "Realistic Water"
lang["gamerule.category.realistic-water.conversion"] = "Realistic Water"
lang["gamerule.realistic-water.convert_water"] = "Turn water into realistic water"
lang["gamerule.realistic-water.convert_water.description"] = "Lakes, ponds and waterfalls near players become realistic water, one whole body at a time. Bodies bigger than the max body size (oceans) stay vanilla. Heavy."
lang["gamerule.realistic-water.convert_lava"] = "Turn lava into realistic lava"
lang["gamerule.realistic-water.convert_lava.description"] = "Lava pools and lava falls near players become realistic lava, one whole body at a time."
lang["gamerule.realistic-water.convert_sand"] = "Turn falling sand into realistic sand"
lang["gamerule.realistic-water.convert_sand.description"] = "Sand near players with nothing solid under it becomes realistic sand."
lang["gamerule.realistic-water.convert_radius"] = "Conversion radius"
lang["gamerule.realistic-water.convert_radius.description"] = "How far around each player lakes and sand are looked for (4-48). A lake reaching into the radius is converted whole."
lang["gamerule.realistic-water.convert_per_second"] = "Blocks converted per second"
lang["gamerule.realistic-water.convert_per_second.description"] = "Average blocks converted per second. A lake waits until enough has built up, then converts at once."
lang["gamerule.realistic-water.convert_max_body"] = "Biggest lake converted"
lang["gamerule.realistic-water.convert_max_body.description"] = "Water or lava bodies with more blocks than this stay vanilla (64-16384). Oceans are always bigger."
with open(lang_path, "w", encoding="utf8") as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)

print("meta patched:", mod["version"])
