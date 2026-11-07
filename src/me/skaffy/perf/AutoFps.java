package me.skaffy.perf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

/**
 * Keeps the game near a target FPS by moving up and down a ladder of quality levels, like a dynamic
 * settings governor. It steps down quickly when FPS drops, and probes upward slowly when FPS has been at
 * the target for a while. If a step up causes a drop, that level is blocked for a growing cooldown so it
 * does not keep oscillating.
 *
 * Only cheap-to-change options are touched (render distance, clouds, particles, entity shadows and
 * distance, weather radius, and the heavy water settings). Mipmaps, texture filtering and biome blend
 * are never changed because they reload textures or every chunk.
 */
public final class AutoFps {
   /** One rung of the quality ladder. */
   record Level(
      String name,
      int renderDistance,
      CloudStatus clouds,
      ParticleStatus particles,
      boolean entityShadows,
      double entityDistance,
      int weatherRadius,
      int waterResolution,
      int surfaceSubdivision,
      int waterSteps,
      boolean whitewater
   ) {
   }

   static final Level[] LEVELS = {
      new Level("Potato", 5, CloudStatus.OFF, ParticleStatus.MINIMAL, false, 0.5, 3, 1, 2, 1, false),
      new Level("Very low", 6, CloudStatus.OFF, ParticleStatus.DECREASED, false, 0.75, 3, 1, 3, 2, false),
      new Level("Low", 8, CloudStatus.OFF, ParticleStatus.DECREASED, false, 0.75, 5, 2, 2, 2, false),
      new Level("Medium", 10, CloudStatus.FAST, ParticleStatus.ALL, false, 1.0, 5, 2, 2, 2, false),
      new Level("High", 12, CloudStatus.FAST, ParticleStatus.ALL, true, 1.0, 7, 2, 2, 3, false),
      new Level("Very high", 16, CloudStatus.FANCY, ParticleStatus.ALL, true, 1.25, 10, 3, 2, 3, false),
      new Level("Ultra", 20, CloudStatus.FANCY, ParticleStatus.ALL, true, 1.5, 10, 4, 2, 4, true)
   };

   private static final long GRACE_MS = 4000;
   /** How long FPS must hold the target before trying one level higher (each try reloads chunks, so not too often). */
   private static final long UP_STABLE_MS = 20000;
   private static final long BASE_BLOCK_MS = 30000;
   private static final long MAX_BLOCK_MS = 300000;

   private static int level = -1;
   private static long lastChange;
   private static long goodSince;
   private static long lastSecond;
   private static int lastUpFrom = -1;
   private static final long[] blockedUntil = new long[LEVELS.length];
   private static final long[] blockLength = new long[LEVELS.length];
   private static final Deque<Integer> samples = new ArrayDeque<>();
   private static Baseline baseline;
   private static int lastFps;

   private AutoFps() {
   }

   public static int level() {
      return level;
   }

   public static String levelName() {
      return level < 0 ? "-" : LEVELS[level].name();
   }

   public static int fps() {
      return lastFps;
   }

   /** Called when the switch is turned on or off from the panel or a command. */
   public static void setEnabled(boolean on) {
      Minecraft mc = Minecraft.getInstance();
      if (on == PanelConfig.autoFps && (on ? level >= 0 : level < 0)) {
         return;
      }

      PanelConfig.autoFps = on;
      if (on) {
         // A baseline still on disk means the game stopped without restoring it (crash): the live options are
         // Auto FPS's old temporary values, so trust the saved baseline instead.
         Baseline saved = Baseline.parse(PanelConfig.autoFpsBaseline);
         baseline = saved != null ? saved : Baseline.capture(mc.options);
         PanelConfig.autoFpsBaseline = baseline.serialize();
         PanelConfig.save();
         level = startLevel(baseline.renderDistance());
         apply(mc, LEVELS[level]);
         resetTimers(System.currentTimeMillis());
      } else {
         if (baseline != null) {
            baseline.restore(mc.options);
            baseline = null;
         }

         PanelConfig.autoFpsBaseline = "";
         PanelConfig.save();
         mc.options.save();
         me.skaffy.client.water.WaterSettings.save();
         level = -1;
         samples.clear();
      }
   }

   /** The player changed a setting by hand. If Auto FPS manages it, that is the value to give back later. */
   static void playerChanged(String name, String value) {
      if (baseline != null && baseline.water().containsKey(name)) {
         baseline.water().put(name, value);
         PanelConfig.autoFpsBaseline = baseline.serialize();
      }
   }

   /** Auto FPS is off but a baseline is still saved (crash, then Auto FPS switched off in the file): put it back once. */
   private static void recoverOrphanedBaseline(Minecraft mc) {
      if (PanelConfig.autoFpsBaseline.isEmpty()) {
         return;
      }

      Baseline saved = Baseline.parse(PanelConfig.autoFpsBaseline);
      if (saved != null) {
         saved.restore(mc.options);
         mc.options.save();
         me.skaffy.client.water.WaterSettings.save();
      }

      PanelConfig.autoFpsBaseline = "";
      PanelConfig.save();
   }

   /**
    * Game is closing: put the player's own settings back and save them, so the files on disk never hold
    * Auto FPS's temporary values. Auto FPS stays switched on and takes over again next launch.
    */
   public static void onStopping(Minecraft mc) {
      if (baseline != null) {
         baseline.restore(mc.options);
         baseline = null;
         level = -1;
         SafeResolution.flush();
         mc.options.save();
         me.skaffy.client.water.WaterSettings.save();
         PanelConfig.autoFpsBaseline = ""; // restored cleanly; Auto FPS captures a fresh one next launch
         PanelConfig.save();
      } else if (SafeResolution.hasPending()) {
         SafeResolution.flush();
         me.skaffy.client.water.WaterSettings.save();
      }
   }

   private static int startLevel(int rd) {
      int best = 0;
      for (int i = 0; i < LEVELS.length; i++) {
         if (LEVELS[i].renderDistance() <= rd) {
            best = i;
         }
      }

      return best;
   }

   private static void resetTimers(long now) {
      lastChange = now;
      goodSince = now;
      samples.clear();
      for (int i = 0; i < blockedUntil.length; i++) {
         blockedUntil[i] = 0;
         blockLength[i] = 0;
      }
   }

   /** Effective target: never above the frame limit the player set. */
   static int effectiveTarget(Options options) {
      int target = PanelConfig.targetFps;
      int limit = options.framerateLimit().get();
      if (limit > 0 && limit < 260) {
         target = Math.min(target, limit);
      }

      return Math.max(20, target);
   }

   /** Client tick (20 per second). */
   public static void tick(Minecraft mc) {
      lastFps = mc.getFps();
      if (!PanelConfig.autoFps) {
         recoverOrphanedBaseline(mc);
         return;
      }

      if (level < 0) {
         setEnabled(true);
         return;
      }

      long now = System.currentTimeMillis();
      if (mc.level == null || mc.player == null || mc.isPaused() || !mc.isWindowActive() || mc.gui.screen() != null) {
         goodSince = now;
         samples.clear();
         return;
      }

      long second = now / 1000;
      if (second == lastSecond) {
         return;
      }

      lastSecond = second;
      if (now - lastChange < GRACE_MS) {
         goodSince = now;
         samples.clear();
         return;
      }

      int fps = mc.getFps();
      samples.addLast(fps);
      while (samples.size() > 3) {
         samples.removeFirst();
      }

      int target = effectiveTarget(mc.options);
      boolean lowNow = fps < target * 0.9;
      boolean twoLow = samples.size() >= 2 && samples.stream().skip(samples.size() - 2).allMatch(f -> f < target * 0.9);
      boolean veryLow = samples.size() >= 2 && samples.stream().skip(samples.size() - 2).allMatch(f -> f < target * 0.45);
      if (veryLow && level > 0) {
         step(mc, Math.max(0, level - 2), now, "FPS very low");
         return;
      }

      if (twoLow && level > 0) {
         step(mc, level - 1, now, "FPS below target");
         return;
      }

      if (lowNow || fps < target - 2) {
         goodSince = now;
         return;
      }

      int up = level + 1;
      if (up < LEVELS.length && now - goodSince >= UP_STABLE_MS && now >= blockedUntil[up]) {
         lastUpFrom = level;
         step(mc, up, now, "FPS steady");
      }
   }

   private static void step(Minecraft mc, int next, long now, String reason) {
      if (next < level && lastUpFrom >= 0 && level == lastUpFrom + 1 && now - lastChange < 20000) {
         // The last step up made things worse: block that level for a while, doubling each time.
         int bad = level;
         blockLength[bad] = blockLength[bad] == 0 ? BASE_BLOCK_MS : Math.min(MAX_BLOCK_MS, blockLength[bad] * 2);
         blockedUntil[bad] = now + blockLength[bad];
      }

      if (next >= level) {
         lastUpFrom = level;
      } else {
         lastUpFrom = -1;
      }

      boolean up = next > level;
      level = next;
      apply(mc, LEVELS[level]);
      lastChange = now;
      goodSince = now;
      samples.clear();
      if (PanelConfig.notify && mc.player != null) {
         mc.player.sendOverlayMessage(
            Component.literal("Auto FPS: " + LEVELS[level].name() + " (" + (level + 1) + "/" + LEVELS.length + ") " + (up ? "↑" : "↓") + "  " + reason)
         );
      }
   }

   static void apply(Minecraft mc, Level l) {
      if (PanelConfig.adjust.world()) {
         Options o = mc.options;
         setIfChanged(o.renderDistance(), l.renderDistance());
         setIfChanged(o.cloudStatus(), l.clouds());
         setIfChanged(o.particles(), l.particles());
         setIfChanged(o.entityShadows(), l.entityShadows());
         setIfChanged(o.entityDistanceScaling(), l.entityDistance());
         setIfChanged(o.weatherRadius(), l.weatherRadius());
      }

      if (PanelConfig.adjust.water()) {
         Map<String, String> water = new LinkedHashMap<>();
         water.put("resolution", String.valueOf(l.waterResolution()));
         water.put("surfaceSubdivision", String.valueOf(l.surfaceSubdivision()));
         water.put("maxStepsPerFrame", String.valueOf(l.waterSteps()));
         water.put("whitewater", String.valueOf(l.whitewater()));
         for (Map.Entry<String, String> e : water.entrySet()) {
            String own = baseline != null ? baseline.water().get(e.getKey()) : null;
            String value = WaterCap.cap(e.getKey(), e.getValue(), own);
            if (!value.equals(SafeResolution.effective(e.getKey()))) {
               // Resolution changes rebuild the simulation and would make flowing water vanish; SafeResolution
               // waits until the water is calm.
               SafeResolution.set(e.getKey(), value);
            }
         }
      }
   }

   private static <T> void setIfChanged(net.minecraft.client.OptionInstance<T> option, T value) {
      if (!value.equals(option.get())) {
         option.set(value);
      }
   }

   /** The player's own settings, restored when Auto FPS is turned off. */
   private record Baseline(
      int renderDistance,
      CloudStatus clouds,
      ParticleStatus particles,
      boolean entityShadows,
      double entityDistance,
      int weatherRadius,
      Map<String, String> water
   ) {
      static Baseline capture(Options o) {
         Map<String, String> water = new LinkedHashMap<>();
         for (String name : new String[]{"resolution", "surfaceSubdivision", "maxStepsPerFrame", "whitewater"}) {
            water.put(name, SafeResolution.effective(name));
         }

         return new Baseline(
            o.renderDistance().get(),
            o.cloudStatus().get(),
            o.particles().get(),
            o.entityShadows().get(),
            o.entityDistanceScaling().get(),
            o.weatherRadius().get(),
            water
         );
      }

      /** For PanelConfig (see {@link KeyValues}). */
      String serialize() {
         Map<String, String> kv = new LinkedHashMap<>();
         kv.put("rd", String.valueOf(this.renderDistance));
         kv.put("clouds", this.clouds.name());
         kv.put("particles", this.particles.name());
         kv.put("shadows", String.valueOf(this.entityShadows));
         kv.put("entity", String.valueOf(this.entityDistance));
         kv.put("weather", String.valueOf(this.weatherRadius));
         for (Map.Entry<String, String> e : this.water.entrySet()) {
            kv.put("w." + e.getKey(), e.getValue());
         }

         return KeyValues.encode(kv);
      }

      /** Null if empty or unreadable. */
      static Baseline parse(String s) {
         if (s == null || s.isBlank()) {
            return null;
         }

         try {
            Map<String, String> kv = KeyValues.decode(s);
            Map<String, String> water = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : kv.entrySet()) {
               if (e.getKey().startsWith("w.")) {
                  water.put(e.getKey().substring(2), e.getValue());
               }
            }

            return new Baseline(
               Integer.parseInt(kv.get("rd")),
               CloudStatus.valueOf(kv.get("clouds")),
               ParticleStatus.valueOf(kv.get("particles")),
               Boolean.parseBoolean(kv.get("shadows")),
               Double.parseDouble(kv.get("entity")),
               Integer.parseInt(kv.get("weather")),
               water
            );
         } catch (RuntimeException e) {
            return null;
         }
      }

      void restore(Options o) {
         setIfChanged(o.renderDistance(), this.renderDistance);
         setIfChanged(o.cloudStatus(), this.clouds);
         setIfChanged(o.particles(), this.particles);
         setIfChanged(o.entityShadows(), this.entityShadows);
         setIfChanged(o.entityDistanceScaling(), this.entityDistance);
         setIfChanged(o.weatherRadius(), this.weatherRadius);
         // The player asked for their settings back: apply them now, even if that rebuilds moving water.
         for (Map.Entry<String, String> e : this.water.entrySet()) {
            SafeResolution.cancel(e.getKey());
            if (!e.getValue().isEmpty() && !e.getValue().equals(WaterPresets.get(e.getKey()))) {
               WaterPresets.setRaw(e.getKey(), e.getValue());
            }
         }
      }
   }
}
