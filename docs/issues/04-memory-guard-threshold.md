---
title: "Tune the GPU memory guard for 4-6 GB cards"
labels: [performance, untested]
---
`MemoryGuard` now scales its limit with the largest device-local Vulkan heap: the three surface buffers of one region may use 1/14 of it, between 96 MB and 576 MB (8 GB cards and up keep the old 576 MB). The chosen limit is logged once: `Realistic Water: N MB video memory, surface buffers limited to M MB per water region`.

To check:
- [ ] The log line appears with the right VRAM size
- [ ] On a 4 GB card, a big flood at Quality no longer fails to allocate
- [ ] Integrated GPUs (small device-local heap): is 96 MB too strict? Maybe also count host-visible shared memory
