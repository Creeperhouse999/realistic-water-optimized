---
title: "Auto FPS: water detail changes near springs and pumps"
labels: [auto-fps, untested]
---
Auto FPS queues water-detail (resolution) changes until `/water stats` reports no moving fluid, sand or leaves, because a resolution change rebuilds every simulation and makes flowing water vanish. Settled water drops to `fluid 0`, but springs and running pumps never do.

Current behaviour: raising detail waits for calm water forever; lowering it is forced after 45 s (`SafeResolution.MAX_WAIT_MS`), because a low frame rate is worse than losing some moving water.

To check in game: next to a converted waterfall, does the forced reduction cause a visible pop? Better long-term options: only count fluid near the camera, or migrate particles between resolutions instead of rebuilding.
