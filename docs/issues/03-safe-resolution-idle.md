---
title: "Auto FPS: water detail changes near springs and pumps"
labels: [auto-fps, untested]
---
Auto FPS queues water-detail (resolution) changes until `/water stats` reports no moving fluid, sand or leaves, because a resolution change rebuilds every simulation and makes flowing water vanish. Settled water drops to `fluid 0`, but springs and running pumps never do.

Current behaviour:
- lowering water detail is forced after 45 s (`SafeResolution.MAX_WAIT_MS`): a low frame rate is worse than losing some moving water
- raising it back is forced after 3 min (`MAX_RAISE_WAIT_MS`)
- Auto FPS never sets water detail above the player's own settings (`WaterCap`)

Seen in game (2026-11-07): next to a converted waterfall the panel showed "water detail waits for calm water", as expected.

To check: does the forced change cause a visible pop next to a spring? Better long-term options: only count fluid near the camera, or migrate particles between resolutions instead of rebuilding.
