package me.skaffy.perf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.skaffy.client.water.WaterWorld;

/**
 * Changing a simulation resolution makes the mod rebuild every simulation from the blocks in the world. Fluid
 * that is mid-flow (a wave, a waterfall, a flood) is not stored in blocks yet, so a rebuild makes it vanish.
 *
 * Auto FPS therefore never changes a resolution while any realistic fluid is moving. The change is queued
 * and applied on the first check where everything is calm (or there is no fluid at all).
 */
public final class SafeResolution {
   /** Settings whose change triggers a full rebuild in WaterWorld.tick. */
   static final java.util.Set<String> REBUILDING = java.util.Set.of("resolution", "lavaResolution", "slimeResolution", "sandResolution", "leafResolution");

   private static final Pattern NUMBER = Pattern.compile("(?:fluid|particles|leaves) ([\\d,]+)");
   private static final Map<String, String> pending = new LinkedHashMap<>();
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
         pending.remove(name);
         WaterPresets.set(name, value);
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

   public static boolean hasPending() {
      return !pending.isEmpty();
   }

   /** Called once a second from the client tick. Applies queued changes when nothing is flowing. */
   public static void tick() {
      if (pending.isEmpty() || !idle()) {
         return;
      }

      for (Map.Entry<String, String> e : pending.entrySet()) {
         WaterPresets.set(e.getKey(), e.getValue());
      }

      pending.clear();
      me.skaffy.client.water.WaterSettings.save();
   }

   /** Applies all queued changes right away (game closing, so a rebuild does not matter). */
   public static void flush() {
      for (Map.Entry<String, String> e : pending.entrySet()) {
         WaterPresets.set(e.getKey(), e.getValue());
      }

      pending.clear();
   }

   /** True when no realistic fluid, sand or leaves are being simulated. Cached for half a second. */
   public static boolean idle() {
      long now = System.currentTimeMillis();
      if (now - lastCheck < 500) {
         return lastIdle;
      }

      lastCheck = now;
      lastIdle = countSimulated() == 0;
      return lastIdle;
   }

   static long countSimulated() {
      String line;
      try {
         line = WaterWorld.get().statsLine();
      } catch (RuntimeException e) {
         return 0;
      }

      long total = 0;
      Matcher m = NUMBER.matcher(line);
      while (m.find()) {
         try {
            total += Long.parseLong(m.group(1).replace(",", ""));
         } catch (NumberFormatException ignored) {
         }
      }

      return total;
   }
}
