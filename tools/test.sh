#!/bin/sh
# Offline checks (no Minecraft, no GPU). Run from the project root after tools/build.sh.
# Usage: sh tools/test.sh [build-folder]   (default build-tsunami, so the patched defaults are checked)
set -e
J="${JDK25:-/c/Program Files/Java/jdk-25.0.3}/bin"
B="${1:-build-tsunami}"
GAMECP="$(cat tools/cp.txt)"
EXTRACP="$(cat tools/cp_extra.txt)"
rm -rf tools/testout
"$J/javac" --release 25 -nowarn -cp "$B;$GAMECP;$EXTRACP" -d tools/testout tools/test/PerfTests.java
# The built folder goes first so its patched classes win over the original jar in cp.txt.
"$J/java" -cp "tools/testout;$B;$GAMECP;$EXTRACP" me.skaffy.perf.PerfTests
