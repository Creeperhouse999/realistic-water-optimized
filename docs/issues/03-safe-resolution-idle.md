---
title: "Auto FPS: water detail might never change if fluid never fully rests"
labels: [auto-fps, untested]
---
Auto FPS queues water-detail changes until `/water stats` reports no moving fluid, sand or leaves. If a pool never fully goes to sleep (springs, pumps), the change waits forever. Other levers (steps, foam, render distance) still work.

Options: apply after N minutes anyway, or only count fluid near the camera.
