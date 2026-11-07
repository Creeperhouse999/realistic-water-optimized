package me.skaffy.perf;

/**
 * Keeps the surface mesher's GPU buffers at a size the graphics card can actually allocate.
 *
 * SurfaceMesher allocates three float buffers of (cells x subdivision^3). A big flood at a high resolution and
 * subdivision 2 asked for a single 1.2 GB buffer, Vulkan refused (VK_ERROR_OUT_OF_DEVICE_MEMORY) and the whole
 * simulation switched itself off. The build patches the start of the SurfaceMesher constructor to call
 * {@link #subdivision}, which lowers the subdivision for that one region until each buffer fits.
 *
 * The limit scales with the card's video memory: the three buffers of one region may use about 1/14 of it, at most
 * 576 MB (8 GB cards and up) and at least 96 MB.
 */
public final class MemoryGuard {
   /** Largest fine grid per buffer: 48M floats = 192 MB, three of them = 576 MB. */
   static final long MAX_FINE_CELLS = 48L * 1024 * 1024;
   /** Smallest limit, for cards that report very little dedicated memory: 8M floats = 32 MB per buffer. */
   static final long MIN_FINE_CELLS = 8L * 1024 * 1024;
   /** Three float buffers per fine cell. */
   private static final long BYTES_PER_FINE_CELL = 3 * 4;
   private static final long VRAM_SHARE = 14;

   private static long lastWarn;
   private static long limit = -1;

   private MemoryGuard() {
   }

   public static int subdivision(int nx, int ny, int nz, int requested) {
      return subdivision(nx, ny, nz, requested, limit());
   }

   static int subdivision(int nx, int ny, int nz, int requested, long maxFineCells) {
      int s = Math.max(1, Math.min(8, requested));
      long cells = (long)nx * ny * nz;
      while (s > 1 && cells * s * s * s > maxFineCells) {
         s--;
      }

      if (s != requested) {
         long now = System.currentTimeMillis();
         if (now - lastWarn > 10000) {
            lastWarn = now;
            me.skaffy.RealisticWater.LOGGER.info(
               "Realistic Water: water region {}x{}x{} is very large, using surface detail {} instead of {} to fit in GPU memory", nx, ny, nz, s, requested
            );
         }
      }

      return s;
   }

   /** Fine-cell limit for a card with {@code deviceLocalBytes} of video memory (0 or less: unknown). */
   static long limitFor(long deviceLocalBytes) {
      if (deviceLocalBytes <= 0) {
         return MAX_FINE_CELLS;
      }

      return Math.clamp(deviceLocalBytes / VRAM_SHARE / BYTES_PER_FINE_CELL, MIN_FINE_CELLS, MAX_FINE_CELLS);
   }

   private static long limit() {
      if (limit < 0) {
         long vram = deviceLocalBytes();
         if (vram <= 0) {
            return MAX_FINE_CELLS; // Vulkan not ready: ask again next time
         }

         limit = limitFor(vram);
         me.skaffy.RealisticWater.LOGGER.info(
            "Realistic Water: {} MB video memory, surface buffers limited to {} MB per water region", vram >> 20, limit * BYTES_PER_FINE_CELL >> 20
         );
      }

      return limit;
   }

   /** Size of the largest device-local memory heap, or 0 if Vulkan can't be asked. */
   private static long deviceLocalBytes() {
      try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
         org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties props = org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties.calloc(stack);
         org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties(me.skaffy.client.vk.Vk.vk().getPhysicalDevice(), props);
         long best = 0;
         for (int i = 0; i < props.memoryHeapCount(); i++) {
            if ((props.memoryHeaps(i).flags() & org.lwjgl.vulkan.VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
               best = Math.max(best, props.memoryHeaps(i).size());
            }
         }

         return best;
      } catch (RuntimeException | LinkageError e) {
         return 0;
      }
   }
}
