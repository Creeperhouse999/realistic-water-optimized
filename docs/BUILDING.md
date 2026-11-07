# Building

The fork is built by patching the original Realistic Water 1.3 jar: new and replaced classes are compiled against
Minecraft and the original jar, shaders and defaults are patched, and three jars are packed (one per starting preset).

## Requirements
- JDK 25 (`javac`, `jar`). Set `JDK25` to its home if it isn't at `C:\Program Files\Java\jdk-25.0.3`.
- A POSIX shell (Git Bash on Windows) and Python 3.
- Minecraft 26.3 + Fabric Loader 0.19.5 installed once (any launcher), so its jars exist locally.
- The original `realistic-water-1.3.jar` ([Modrinth / GitHub of the original](https://github.com/sskaffy/Realistic-Water)).
- `tools/asm.jar` and `tools/asm-tree.jar`: [ASM 9.9](https://asm.ow2.io/) (`org.ow2.asm:asm`, `org.ow2.asm:asm-tree`).
- A Sodium 0.9.2 jar for Minecraft 26.3 (only to compile the Sodium settings page). Set `SODIUM_JAR` to its path.

## One-time setup
```sh
# 1. Original jar: keep a copy in the project root and unpack it
cp /path/to/realistic-water-1.3.jar realistic-water-1.3-original.jar
mkdir extracted && (cd extracted && jar xf ../realistic-water-1.3-original.jar)

# 2. Fabric API modules used at compile time (nested in the Fabric API jar)
mkdir -p tools/fapi/x && cd tools/fapi/x
unzip -j /path/to/fabric-api-0.165.0+26.3.jar \
  'META-INF/jars/fabric-key-mapping-api-v1-*' 'META-INF/jars/fabric-lifecycle-events-v1-*' \
  'META-INF/jars/fabric-command-api-v2-*' 'META-INF/jars/fabric-api-base-*' 'META-INF/jars/fabric-game-rule-api-v1-*'
cd ../../..

# 3. Classpaths: copy the examples and replace <MODRINTH_META> with your launcher's meta folder
cp tools/cp.txt.example tools/cp.txt
cp tools/cp_extra.txt.example tools/cp_extra.txt
```
Each classpath file is one line, entries separated by `;`, Windows paths.

## Build
```sh
sh tools/build.sh
```
Produces `realistic-water-1.3-lowend.jar`, `-balanced.jar` and `-tsunami.jar` in the project root.

## What the build does
| Step | File |
|---|---|
| Compile replaced renderer classes (half-res fluid passes, ocean-sink solid cache) | `src/me/skaffy/client/water/` |
| Compile the fork's package: panel, presets, Auto FPS, world conversion, memory guard | `src/me/skaffy/perf/` |
| Compile the Sodium options page with the panel buttons | `src/me/skaffy/client/compat/SodiumOptions.java` |
| Cheaper reflections and shadows, render-scale defines | `sed` lines in `tools/build.sh` |
| Ocean sink in the compute shaders | `tools/sink_patch.py` |
| Mod metadata, entrypoints, translations | `tools/meta_patch.py` |
| New default values, light budget, mesher memory guard (bytecode) | `tools/Patcher.java` |

## Checking shaders without the game
`tools/ShaderCheck.java` compiles every shader with shaderc (LWJGL). The only expected failure is `particle.vert`
compiled without defines, which fails on the original too.
