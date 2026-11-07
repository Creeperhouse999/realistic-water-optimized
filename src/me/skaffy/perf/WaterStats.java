package me.skaffy.perf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads numbers out of the mod's /water stats line (WaterWorld.statsLine()).
 *
 * The line is built with String.format and the default locale, so thousands separators can be "," "." or a
 * (non-breaking) space. No Minecraft classes here, so tools/test can check the parsing offline.
 */
public final class WaterStats {
   /** "fluid N" (water, lava fluid, slime fluid), "sand particles N", "leaves N". Not whitewater: spray settles by itself. */
   private static final Pattern MOVING = Pattern.compile("(?:fluid|particles|leaves) (\\S+)");

   /** "3 water bodies", "1 lava bodies", "1 slime bodies", "2 sand bodies", "1 leaf piles". */
   private static final Pattern BODIES = Pattern.compile("(\\S+) (?:water bodies|lava bodies|slime bodies|sand bodies|leaf piles)");

   private WaterStats() {
   }

   /** Total simulated water, lava, slime, sand and leaf particles, or -1 if the line is missing. */
   public static long simulated(String line) {
      if (line == null) {
         return -1;
      }

      long total = 0;
      Matcher m = MOVING.matcher(line);
      while (m.find()) {
         String digits = m.group(1).replaceAll("[^0-9]", "");
         if (digits.isEmpty()) {
            continue;
         }

         try {
            total += Long.parseLong(digits);
         } catch (NumberFormatException ignored) {
            // more than 19 digits: not a real count
         }
      }

      return total;
   }

   /** Number of simulated bodies of any material (settled ones count too), 0 for an empty world, -1 if the line is missing. */
   public static long bodies(String line) {
      if (line == null) {
         return -1;
      }

      long total = 0;
      Matcher m = BODIES.matcher(line);
      while (m.find()) {
         String digits = m.group(1).replaceAll("[^0-9]", "");
         if (!digits.isEmpty()) {
            try {
               total += Long.parseLong(digits);
            } catch (NumberFormatException ignored) {
            }
         }
      }

      return total;
   }

   /** The "frame 12.3 ms" part of the line, or "" if there is none. */
   public static String frameTime(String line) {
      if (line == null) {
         return "";
      }

      int cut = line.indexOf("frame ");
      if (cut < 0) {
         return "";
      }

      int end = line.indexOf(" ms", cut);
      return end > 0 ? line.substring(cut, end + 3) : "";
   }
}
