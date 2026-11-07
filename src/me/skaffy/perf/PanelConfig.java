package me.skaffy.perf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import me.skaffy.RealisticWater;
import net.fabricmc.loader.api.FabricLoader;

/** Settings of the performance fork's control panel and Auto FPS, stored in config/realistic-water-panel.properties. */
public final class PanelConfig {
   public enum Adjust {
      BOTH("Water + world"),
      WATER("Water only"),
      WORLD("World only");

      public final String label;

      Adjust(String label) {
         this.label = label;
      }

      public boolean water() {
         return this != WORLD;
      }

      public boolean world() {
         return this != WATER;
      }
   }

   public static boolean autoFps = false;
   public static int targetFps = 60;
   public static Adjust adjust = Adjust.BOTH;
   public static boolean notify = true;
   public static String preset = WaterPresets.Preset.TSUNAMI.name();
   /** Set once the old Page Up key binding has been moved to K, so a deliberate Page Up binding is kept. */
   public static boolean keyMigrated = false;
   /**
    * The player's own settings while Auto FPS holds temporary ones (AutoFps.Baseline, serialized), empty when Auto
    * FPS is off. Written as soon as Auto FPS starts, so after a crash the real settings can still be put back.
    */
   public static String autoFpsBaseline = "";

   private PanelConfig() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("realistic-water-panel.properties");
   }

   public static void load() {
      Path file = file();
      if (!Files.exists(file)) {
         return;
      }

      Properties p = new Properties();
      try (Reader in = Files.newBufferedReader(file)) {
         p.load(in);
      } catch (IOException e) {
         RealisticWater.LOGGER.warn("Couldn't read {}", file, e);
         return;
      }

      autoFps = Boolean.parseBoolean(p.getProperty("autoFps", String.valueOf(autoFps)));
      notify = Boolean.parseBoolean(p.getProperty("notify", String.valueOf(notify)));
      preset = p.getProperty("preset", preset);
      keyMigrated = Boolean.parseBoolean(p.getProperty("keyMigrated", "false"));
      autoFpsBaseline = p.getProperty("autoFpsBaseline", "");
      try {
         targetFps = clamp(Integer.parseInt(p.getProperty("targetFps", String.valueOf(targetFps))), 20, 240);
      } catch (NumberFormatException ignored) {
      }

      try {
         adjust = Adjust.valueOf(p.getProperty("adjust", adjust.name()));
      } catch (IllegalArgumentException ignored) {
      }
   }

   public static void save() {
      Properties p = new Properties();
      p.setProperty("autoFps", String.valueOf(autoFps));
      p.setProperty("targetFps", String.valueOf(targetFps));
      p.setProperty("adjust", adjust.name());
      p.setProperty("notify", String.valueOf(notify));
      p.setProperty("preset", preset);
      p.setProperty("keyMigrated", String.valueOf(keyMigrated));
      p.setProperty("autoFpsBaseline", autoFpsBaseline);
      Path file = file();
      try {
         Files.createDirectories(file.getParent());
         try (Writer out = Files.newBufferedWriter(file)) {
            p.store(out, "Realistic Water performance fork: control panel and Auto FPS");
         }
      } catch (IOException e) {
         RealisticWater.LOGGER.warn("Couldn't save {}", file, e);
      }
   }

   static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }
}
