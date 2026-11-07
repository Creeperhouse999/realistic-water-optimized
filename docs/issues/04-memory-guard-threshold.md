---
title: "Tune the GPU memory guard for 4-6 GB cards"
labels: [performance]
---
`MemoryGuard.MAX_FINE_CELLS` allows 3 x 192 MB surface buffers per region. Fine for 8 GB+, probably too much on 4 GB cards. Consider scaling it with the VRAM Vulkan reports.
