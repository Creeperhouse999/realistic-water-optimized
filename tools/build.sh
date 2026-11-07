#!/bin/sh
# Builds all presets from the extracted original jar. Run from the project root.
# See docs/BUILDING.md: needs extracted/ (the original jar unpacked), tools/cp.txt and tools/cp_extra.txt.
# Optional environment: JDK25 (JDK 25 home), SODIUM_JAR (path to the Sodium jar used for compiling).
set -e
J="${JDK25:-/c/Program Files/Java/jdk-25.0.3}/bin"
CP="tools/asm.jar;tools/asm-tree.jar"
GAMECP="$(cat tools/cp.txt)"
"$J/javac" -cp "$CP" -d tools/out tools/Patcher.java
# Half-resolution fluid renderer (4K)
rm -rf tools/rendout
"$J/javac" --release 25 -nowarn -cp "$GAMECP" -d tools/rendout src/me/skaffy/client/water/WaterRenderer.java src/me/skaffy/client/water/SolidCache.java
# Control panel + Auto FPS (new package) and the Sodium options page with the panel button
EXTRACP="$(cat tools/cp_extra.txt)"
SODIUM="${SODIUM_JAR:-$(cygpath -w "$APPDATA/ModrinthApp/profiles/Realistic Water (1)/mods/sodium-fabric-0.9.2+mc26.3.jar")}"
rm -rf tools/perfout tools/sodout
"$J/javac" --release 25 -nowarn -cp "$GAMECP;$EXTRACP" -d tools/perfout src/me/skaffy/perf/*.java
"$J/javac" --release 25 -nowarn -cp "$GAMECP;$EXTRACP;$SODIUM;tools/perfout" -d tools/sodout src/me/skaffy/client/compat/SodiumOptions.java
for preset in lowend balanced tsunami; do
  B=build-$preset
  rm -rf $B && cp -r extracted $B
  S=$B/assets/realistic-water/shaders
  # Cheaper screen-space reflections and sun-shadow march
  sed -i 's/for (int i = 0; i < 48; i++) {/for (int i = 0; i < 20; i++) {/; s/stepLen \*= 1\.1;/stepLen *= 1.3;/' $S/composite.frag
  sed -i 's/#define SHADOW_STEPS 64/#define SHADOW_STEPS 24/' $S/sun_shadow.glsl
  # Render-scale aware fluid passes (RES_SCALE defined by WaterRenderer for half-res passes)
  printf '#ifndef RES_SCALE\n#define RES_SCALE 1.0\n#endif\n' | cat - $S/common.glsl > $S/common.tmp && mv $S/common.tmp $S/common.glsl
  for f in mesh_thickness.frag droplet_thickness.frag whitewater.frag; do
    sed -i 's/gl_FragCoord\.xy \* G\.screen\.zw/gl_FragCoord.xy * RES_SCALE * G.screen.zw/' $S/$f
    grep -q "RES_SCALE" $S/$f
  done
  sed -i 's/0\.5 \* G\.screen\.y \/ dist;/0.5 * G.screen.y \/ (RES_SCALE * dist);/' $S/particle.vert
  grep -q "RES_SCALE \* dist" $S/particle.vert
  # Ocean sink: vanilla water cells are value 2 in the solid grid. Treat as open, delete particles entering them.
  python -I tools/sink_patch.py $S
  grep -q "i < 20" $S/composite.frag && grep -q "SHADOW_STEPS 24" $S/sun_shadow.glsl
  cp tools/rendout/me/skaffy/client/water/*.class $B/me/skaffy/client/water/
  mkdir -p $B/me/skaffy/perf && cp tools/perfout/me/skaffy/perf/*.class $B/me/skaffy/perf/
  cp tools/sodout/me/skaffy/client/compat/*.class $B/me/skaffy/client/compat/
  python -I tools/meta_patch.py $B $preset
  echo "== $preset"
  "$J/java" -cp "$CP;tools/out" Patcher $B $preset
  rm -f realistic-water-1.3-$preset.jar
  "$J/jar" cfM realistic-water-1.3-$preset.jar -C $B .
done
