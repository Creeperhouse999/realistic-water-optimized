package me.skaffy.perf;

import java.util.LinkedHashMap;
import java.util.Map;
import me.skaffy.client.water.WaterWorld;

/**
 * Changing a simulation resolution makes the mod rebuild every simulation from the blocks in the world. Fluid
 * that is mid-flow (a wave, a waterfall, a flood) is not stored in blocks yet, so a rebuild makes it vanish.
 *
 * Auto FPS therefore never changes a resolution while any realistic fluid is moving. The change is queued
 * and applied on the first check where everything is calm (or there is no fluid at all).
 *
 * Springs (converted waterfalls, infinite sources) keep water moving forever. So a change that LOWERS a resolution
 * is applied anyway after {@link #MAX_WAIT_MS}: when the frame rate is too low, losing some moving water is better
 * than staying slow. Raising quality always waits for calm water.
 */
public final class SafeResolution {
   /** Settings whose change triggers a full rebuild in WaterWorld.tick. */
   static final java.util.Set<String> REBUILDING = java.util.Set.of("resolution", "lavaResolution", "slimeResolution", "sandResolution", "leafResolution");

   static final long MAX_WAIT_MS = 45_000;

   private static final Map<String, String> pending = new LinkedHashMap<>();
   private static final Map<String, Long> queuedAt = new java.util.HashMap<>();
   private static long lastCheck;
   private static boolean lastIdle = true;

   private SafeResolution() {
   }

   public static boolean rebuilds(String name) {
      return REBUILDING.contains(name);
   }

   /** Sets a setting now if it does not rebuild or the fluid is calm; otherwise queues it. Returns true if applied now. */
   public static boolean set(String name, String value) {
      if (!rebuilds(name) || idle()) {
         cancel(name);
         WaterPresets.set(name, value);
         return true;
      }

      String current = WaterPresets.get(name);
      String previous = pending.put(name, value);
      // The wait for a forced reduction starts when the queue first asks for a reduction, not when an earlier
      // raise was queued. Re-queuing the same kind of change (Auto FPS does every check) keeps the timer.
      boolean reduction = isReduction(value, current);
      boolean wasReduction = previous != null && isReduction(previous, current);
      if (!queuedAt.containsKey(name) || reduction && !wasReduction) {
         queuedAt.put(name, System.currentTimeMillis());
      }

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
      queuedAt.remove(name);
   }

   public static boolean hasPending() {
      return !pending.isEmpty();
   }

   /** Called once a second from the client tick. Applies queued changes when nothing is flowing, or overdue reductions. */
   public static void tick() {
      if (pending.isEmpty()) {
         return;
      }

      boolean calm = idle();
      long now = System.currentTimeMillis();
      boolean changed = false;
      for (Map.Entry<String, String> e : java.util.List.copyOf(pending.entrySet())) {
         String name = e.getKey();
         if (calm || overdueReduction(name, e.getValue(), WaterPresets.get(name), queuedAt.getOrDefault(name, now), now)) {
            WaterPresets.set(name, e.getValue()); // also removes it from pending
            changed = true;
         }
      }

      if (changed) {
         me.skaffy.client.water.WaterSettings.save();
      }
   }

   /** A queued change that lowers a resolution and has waited longer than {@link #MAX_WAIT_MS}. */
   static boolean overdueReduction(String name, String queued, String current, long since, long now) {
      if (!rebuilds(name) || now - since < MAX_WAIT_MS) {
         return false;
      }

      return isReduction(queued, current);
   }

   static boolean isReduction(String queued, String current) {
      try {
         return Integer.parseInt(queued) < Integer.parseInt(current);
      } catch (NumberFormatException e) {
         return false;
      }
   }

   /** Applies all queued changes right away (game closing, so a rebuild does not matter). */
   public static void flush() {
      for (Map.Entry<String, String> e : java.util.List.copyOf(pending.entrySet())) {
         WaterPresets.set(e.getKey(), e.getValue());
      }

      pending.clear();
      queuedAt.clear();
   }

   /** True when no realistic fluid, sand or leaves are being simulated. Cached for half a second. */
   public static boolean idle() {
      long now = System.currentTimeMillis();
      if (now - lastCheck < 500) {
         return lastIdle;
      }

      lastCheck = now;
      lastIdle = countSimulated() == 0; // -1 (unknown) counts as busy
      return lastIdle;
   }

   static long countSimulated() {
      String line;
      try {
         line = WaterWorld.get().statsLine();
      } catch (RuntimeException e) {
         return -1;
      }

      return WaterStats.simulated(line);
   }
}
