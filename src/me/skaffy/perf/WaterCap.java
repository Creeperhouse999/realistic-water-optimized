package me.skaffy.perf;

/**
 * Auto FPS never sets water detail above the player's own setting. Climbing above it while the water was calm made
 * the next flood far too heavy (resolution 4 with foam), and the frame rate crashed to the lowest level.
 * No Minecraft classes here, so tools/test can check it.
 */
final class WaterCap {
   private WaterCap() {
   }

   /**
    * View distance Auto FPS should use: the level's, at least {@code minView}, and not above the player's own
    * ({@code own}, 0 if unknown) unless {@code aboveOwn}.
    */
   static int view(int levelView, int own, int minView, boolean aboveOwn) {
      int v = levelView;
      if (!aboveOwn && own > 0) {
         v = Math.min(v, own);
      }

      return Math.max(v, minView);
   }

   /** The value Auto FPS should use for {@code name}: its level's value, but never above {@code own}. */
   static String cap(String name, String levelValue, String own) {
      if (own == null || own.isEmpty()) {
         return levelValue;
      }

      if ("whitewater".equals(name)) {
         return String.valueOf(Boolean.parseBoolean(levelValue) && Boolean.parseBoolean(own));
      }

      try {
         return String.valueOf(Math.min(Integer.parseInt(levelValue), Integer.parseInt(own)));
      } catch (NumberFormatException e) {
         return levelValue;
      }
   }
}
