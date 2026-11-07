package me.skaffy.perf;

/**
 * Keeps the surface mesher's GPU buffers at a size the graphics card can actually allocate.
 *
 * SurfaceMesher allocates three float buffers of (cells x subdivision^3). A big flood at a high resolution and
 * subdivision 2 asked for a single 1.2 GB buffer, Vulkan refused (VK_ERROR_OUT_OF_DEVICE_MEMORY) and the whole
 * simulation switched itself off. The build patches the start of the SurfaceMesher constructor to call
 * {@link #subdivision}, which lowers the subdivision for that one region until each buffer fits.
 */
public final class MemoryGuard {
   /** Largest fine grid per buffer: 48M floats = 192 MB, three of them = 576 MB. */
   static final long MAX_FINE_CELLS = 48L * 1024 * 1024;

   private static long lastWarn;

   private MemoryGuard() {
   }

   public static int subdivision(int nx, int ny, int nz, int requested) {
      int s = Math.max(1, requested);
      long cells = (long)nx * ny * nz;
      while (s > 1 && cells * s * s * s > MAX_FINE_CELLS) {
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
}
