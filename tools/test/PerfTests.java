package me.skaffy.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import me.skaffy.client.water.WaterSettings;
import me.skaffy.client.water.WaterSettingsInfo;

/**
 * Offline checks for the fork's own logic. No Minecraft instance, no GPU: run by tools/test.sh against a built
 * preset folder (default build-tsunami), so the bytecode-patched defaults are checked too.
 *
 * Lives in package me.skaffy.perf to reach package-private members.
 */
public final class PerfTests {
   private static final List<String> failures = new ArrayList<>();
   private static int checks;

   public static void main(String[] args) {
      statsParsing();
      memoryGuard();
      presets();
      panelLayout();
      patchedDefaults();

      System.out.println(checks + " checks, " + failures.size() + " failed");
      for (String f : failures) {
         System.out.println("FAIL " + f);
      }

      System.exit(failures.isEmpty() ? 0 : 1);
   }

   private static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         failures.add(what);
      }
   }

   private static void equal(Object expected, Object actual, String what) {
      check(expected.equals(actual), what + ": expected " + expected + ", got " + actual);
   }

   // -----------------------------------------------------------------------------------------------------------------

   /** Same format string as WaterWorld.statsLine (up to the frame time). */
   private static String statsLine(Locale locale, int fluid, int whitewater, int lava, int slime, int sand, int leaves, double frameMs) {
      return String.format(
         locale,
         "%d water bodies  fluid %,d  whitewater %,d  |  %d lava bodies  lava fluid %,d  |  %d slime bodies  slime fluid %,d  |  %d sand bodies  sand particles %,d  |  %d leaf piles  leaves %,d  |  active %,d cells of %,d allocated  |  frame %.1f ms  water steps %d x %.1f ms",
         3, fluid, whitewater, 1, lava, 1, slime, 2, sand, 1, leaves, 5_000_000, 9_000_000, frameMs, 2, 17.4
      );
   }

   private static void statsParsing() {
      long expected = 1_234_567L + 2_000 + 30 + 45_000 + 7;
      for (Locale l : new Locale[]{Locale.ROOT, Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("pl-PL"), Locale.forLanguageTag("de-CH"), Locale.forLanguageTag("hi-IN")}) {
         String line = statsLine(l, 1_234_567, 999_999, 2_000, 30, 45_000, 7, 12.5);
         equal(expected, WaterStats.simulated(line), "simulated count, locale " + l + " (" + line.substring(0, 40) + ")");
         check(WaterStats.frameTime(line).startsWith("frame 12") && WaterStats.frameTime(line).endsWith(" ms"), "frame time, locale " + l + ": " + WaterStats.frameTime(line));
      }

      equal(0L, WaterStats.simulated(statsLine(Locale.US, 0, 500, 0, 0, 0, 0, 3.0)), "settled water with spray counts as idle");
      equal(0L, WaterStats.simulated("Nothing simulated right now (place a Realistic Water Bucket or Realistic Sand, or use /fill)"), "empty world");
      equal(-1L, WaterStats.simulated(null), "missing line");
      equal("", WaterStats.frameTime("Nothing simulated right now"), "no frame time");
   }

   private static void memoryGuard() {
      equal(2, MemoryGuard.subdivision(40, 30, 40, 2), "small region keeps its subdivision");
      equal(8, MemoryGuard.subdivision(4, 4, 4, 12), "subdivision is capped at 8");
      equal(1, MemoryGuard.subdivision(4, 4, 4, 0), "subdivision is at least 1");
      equal(1, MemoryGuard.subdivision(400, 200, 400, 2), "huge region drops to 1");
      equal(2, MemoryGuard.subdivision(200, 100, 200, 4), "4M cells at 4 drops to 2");

      // The 1.2 GB failure: three buffers must stay under the cap for any region the mod can make.
      int[][] regions = {{512, 256, 512}, {300, 120, 300}, {96, 64, 96}, {1, 1, 1}};
      for (int[] r : regions) {
         for (int req = 1; req <= 8; req++) {
            int s = MemoryGuard.subdivision(r[0], r[1], r[2], req);
            long fine = (long)r[0] * r[1] * r[2] * s * s * s;
            check(s >= 1 && s <= req, "subdivision in [1, requested] for " + r[0] + "x" + r[1] + "x" + r[2] + " req " + req + ": " + s);
            check(s == 1 || fine <= MemoryGuard.MAX_FINE_CELLS, "fine grid fits for " + r[0] + "x" + r[1] + "x" + r[2] + " req " + req);
         }
      }
   }

   /** A value as the panel would show it: within the setting's range, and of the right kind. */
   private static void validValue(String where, String name, String value) {
      WaterSettingsInfo.Info info = WaterSettingsInfo.get(name);
      check(info != null, where + ": unknown setting " + name);
      if (info == null) {
         return;
      }

      switch (info.kind()) {
         case SWITCH -> check("true".equals(value) || "false".equals(value), where + ": " + name + " must be true/false, is " + value);
         case INTEGER, NUMBER -> {
            double v;
            try {
               v = info.kind() == WaterSettingsInfo.Kind.INTEGER ? Integer.parseInt(value) : Double.parseDouble(value);
            } catch (NumberFormatException e) {
               check(false, where + ": " + name + " is not a " + info.kind() + ": " + value);
               return;
            }

            check(v >= info.min() - 1e-9 && v <= info.max() + 1e-9, where + ": " + name + " = " + value + " outside " + info.min() + ".." + info.max());
         }
         case TEXT -> {
         }
      }
   }

   private static void presets() {
      Set<String> first = null;
      for (WaterPresets.Preset p : WaterPresets.Preset.values()) {
         Map<String, String> s = p.settings();
         for (Map.Entry<String, String> e : s.entrySet()) {
            validValue("preset " + p, e.getKey(), e.getValue());
            try {
               WaterSettings.set(e.getKey(), e.getValue());
               String back = WaterSettings.get(e.getKey());
               check(back.equals(e.getValue()) || Double.parseDouble(back) == Double.parseDouble(e.getValue()),
                  "preset " + p + ": " + e.getKey() + " reads back as " + back);
            } catch (ReflectiveOperationException | NumberFormatException ex) {
               check(false, "preset " + p + ": can't set " + e.getKey() + ": " + ex);
            }
         }

         // Switching presets must overwrite everything the previous one set, so all presets set the same keys.
         Set<String> keys = new TreeSet<>(s.keySet());
         if (first == null) {
            first = keys;
         } else {
            equal(first, keys, "preset " + p + " sets the same settings as " + WaterPresets.Preset.values()[0]);
         }

         for (String rebuild : SafeResolution.REBUILDING) {
            check(s.containsKey(rebuild), "preset " + p + " sets rebuild setting " + rebuild);
         }

         check(!p.label.isBlank() && !p.description.isBlank(), "preset " + p + " has a label and description");
      }
   }

   private static void panelLayout() {
      for (Map<String, List<String>> tab : List.of(PanelLayout.WATER, PanelLayout.MATERIALS)) {
         for (Map.Entry<String, List<String>> g : tab.entrySet()) {
            check(!g.getValue().isEmpty(), "panel group " + g.getKey() + " is empty");
            for (String name : g.getValue()) {
               check(WaterSettingsInfo.get(name) != null, "panel group " + g.getKey() + ": unknown setting " + name);
            }
         }
      }

      for (String name : PanelLayout.HOME_QUICK) {
         check(WaterSettingsInfo.get(name) != null, "home quick settings: unknown setting " + name);
      }

      check(PanelLayout.HOME_QUICK.size() == 2, "home quick settings has two entries (one row)");
      for (String name : PanelLayout.SODIUM_QUICK) {
         check(WaterSettingsInfo.get(name) != null, "Sodium page: unknown setting " + name);
      }

      for (String name : SafeResolution.REBUILDING) {
         check(WaterSettingsInfo.get(name) != null, "rebuild list: unknown setting " + name);
      }
   }

   /** The build patches WaterSettings' defaults; each must still be a value the setting accepts. */
   private static void patchedDefaults() {
      for (String category : WaterSettingsInfo.categories()) {
         for (String name : WaterSettingsInfo.names(category)) {
            String def = WaterSettings.defaultValue(name);
            if (def.isEmpty()) {
               continue;
            }

            validValue("default", name, def);
         }
      }
   }
}
