"""Ocean sink shader patch.

Vanilla water cells are written as 2 in the static solid grid (solid = 1, empty = 0).
- Every solid test `staticSolid[x] != 0u` becomes `staticSolid[x] == 1u`, so sink cells act as open space.
- g2p.comp deletes fluid particles whose new position is inside a sink cell.
Usage: python -I sink_patch.py <shader dir>
"""
import os
import re
import sys

d = sys.argv[1]
FILES = ["classify.comp", "emit.comp", "g2p.comp", "inflow.comp", "leaf_step.comp", "nets_vertices.comp",
         "steam_step.comp", "wall_fill.comp", "ww_update.comp", "leaf.vert"]
pat = re.compile(r"staticSolid\[([^\]]*)\] != 0u")
total = 0
for name in FILES:
    p = os.path.join(d, name)
    s = open(p, encoding="utf8").read()
    s, n = pat.subn(r"staticSolid[\1] == 1u", s)
    assert n > 0, name
    assert "!= 0u" not in "".join(l for l in s.splitlines() if "staticSolid[" in l), name
    total += n
    open(p, "w", encoding="utf8").write(s)

p = os.path.join(d, "g2p.comp")
s = open(p, encoding="utf8").read()
anchor = "    float killMargin = G.killInfo.y;\n"
assert s.count(anchor) == 1
s = s.replace(anchor, """    ivec3 sinkCell = ivec3(floor(xn));
    if (inGrid(sinkCell) && staticSolid[cellIndex(sinkCell)] == 2u) {
        return;
    }
""" + anchor)
open(p, "w", encoding="utf8").write(s)
print(f"sink patch: {total} solid tests rewritten")
