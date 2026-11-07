---
title: "Auto FPS: water detail waits forever next to springs or pumps"
labels: [auto-fps, untested]
---
Auto FPS queues water-detail (resolution) changes until `/water stats` reports no moving fluid, sand or leaves, because a resolution change rebuilds every simulation and makes flowing water vanish. Settled water drops to `fluid 0`, but springs and running pumps never do, so near them the change waits forever. Other levers (render distance, steps, foam) still work.

Options: apply after a few minutes anyway, only count fluid near the camera, or migrate particles between resolutions instead of rebuilding.
