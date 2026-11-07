---
title: "/water gui shows the old screen for one frame"
labels: [control-panel]
---
The original command opens its own screen; the fork replaces it on the next client tick, so it can flash for a frame. A small mixin into WaterCommands would open the panel directly.
