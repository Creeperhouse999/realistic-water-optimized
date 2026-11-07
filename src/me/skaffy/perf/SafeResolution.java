package me.skaffy.perf;

import java.util.LinkedHashMap;
import java.util.Map;
import me.skaffy.client.water.WaterWorld;

/**
 * Changing a simulation resolution makes the mod throw away every simulation and rebuild it from the blocks in the
 * world (WaterWorld.reload). That rebuild is destructive:
 * - fluid that is mid-flow (a wave, a waterfall, a flood) is not stored in blocks yet, so it vanishes;
 * - a full, settled tank rebuilt at the new resolution can burst through its walls (seen in game on 2026-11-07:
 *   a 13-deep glass tank leaked through intact glass right after resolution 2 -> 1 and 1 -> 2).
 *
 * So Auto FPS only changes a resolution when there is no realistic fluid simulated at all ("Nothing simulated
 * right now"), and never forces it. While water exists it uses its other levers (view distance, physics steps,
 * surface detail, foam), which don't rebuild anything.
 */
public final class SafeResolution {
   /** Settings whose change triggers a full rebuild in WaterWorld.tick. */
   static final java.util.Set<String> REBUILDING = java.util.Set.of("resolution", "lavaResolution", "slimeResolution", "sandResolution", "leafResolution");

   private static final Map<String, String> pending = new LinkedHashMap<>();
   private static long lastCheck;
   private static boolean lastIdle = true;

   private SafeResolution() {
   }

   public static boolean rebuilds(String name) {
      return REBUILDING.contains(name);
   }

   /** Sets a setting now if it does not rebuild or nothing is simulated; otherwise queues it. Returns true if applied now. */
   public static boolean set(String name, String value) {
      if (!rebuilds(name) || idle()) {
         cancel(name);
         WaterPresets.setRaw(name, value);
         return true;
      }

      pending.put(name, value);
      return false;
   }

   /** The value the setting will have once queued changes are applied. */
   public static String effective(String name) {
      String queued = pending.get(name);
      return queued != null ? queued : WaterPresets.get(name);
   }

   /** A manual change of the same setting wins over a queued Auto FPS change. */
   public static void cancel(String name) {
      pending.remove(name);
   }

   public static boolean hasPending() {
      return !pending.isEmpty();
   }

   /** Called once a second from the client tick. Applies queued changes once nothing is simulated. */
   public static void tick() {
      if (pending.isEmpty() || !idle()) {
         return;
      }

      for (Map.Entry<String, String> e : java.util.List.copyOf(pending.entrySet())) {
         WaterPresets.setRaw(e.getKey(), e.getValue());
      }

      pending.clear();
      me.skaffy.client.water.WaterSettings.save();
   }

   /** Applies all queued changes right away (game closing, so a rebuild does not matter). */
   public static void flush() {
      for (Map.Entry<String, String> e : java.util.List.copyOf(pending.entrySet())) {
         WaterPresets.setRaw(e.getKey(), e.getValue());
      }

      pending.clear();
   }

   /** True when no realistic water, lava, slime, sand or leaves are simulated at all. Cached for half a second. */
   public static boolean idle() {
      long now = System.currentTimeMillis();
      if (now - lastCheck < 500) {
         return lastIdle;
      }

      lastCheck = now;
      lastIdle = countBodies() == 0; // -1 (unknown) counts as busy
      return lastIdle;
   }

   static long countSimulated() {
      return WaterStats.simulated(statsLine());
   }

   static long countBodies() {
      return WaterStats.bodies(statsLine());
   }

   private static String statsLine() {
      try {
         return WaterWorld.get().statsLine();
      } catch (RuntimeException e) {
         return null;
      }
   }
}
