package me.skaffy.perf;

import java.util.LinkedHashMap;
import java.util.Map;
import me.skaffy.RealisticWater;
import me.skaffy.client.water.WaterSettings;

/** One-click water presets for the control panel. Each preset sets the heavy settings and leaves looks-only ones alone. */
public final class WaterPresets {
   public enum Preset {
      LOWEST("Lowest", "Blocky water at 1 cell per block. Use when nothing else helps."),
      OCEAN("Ocean", "For huge bodies of water: oceans, lakes and converted worlds. Very cheap simulation, smooth surface, strong ripples."),
      TSUNAMI("Tsunami", "Default. Smooth big floods and waves, no foam particles."),
      BALANCED("Balanced", "Finer water with foam and spray. Medium cost."),
      QUALITY("Quality", "Close to the original mod's look: fine water, full foam. Heavy: keep floods small.");

      public final String label;
      public final String description;

      Preset(String label, String description) {
         this.label = label;
         this.description = description;
      }

      public Map<String, String> settings() {
         Map<String, String> v = new LinkedHashMap<>();
         switch (this) {
            case LOWEST -> {
               v.put("fluidRestSpeed", "0.3");
               v.put("fluidRestTime", "0.75");
               v.put("detailStrength", "0.5");
               v.put("detailScale", "6");
               v.put("resolution", "1");
               v.put("surfaceSubdivision", "3");
               v.put("maxStepsPerFrame", "2");
               v.put("whitewater", "false");
               v.put("dropletThreshold", "0");
               v.put("lavaResolution", "1");
               v.put("slimeResolution", "1");
               v.put("sandResolution", "3");
               v.put("sandMaxStepsPerFrame", "3");
               v.put("sandGrainsPerParticle", "6");
               v.put("leafResolution", "4");
               v.put("leavesPerBlock", "60");
            }
            case OCEAN -> {
               v.put("resolution", "1");
               v.put("surfaceSubdivision", "4");
               v.put("maxStepsPerFrame", "2");
               v.put("whitewater", "false");
               v.put("dropletThreshold", "0");
               v.put("fluidRestSpeed", "0.3");
               v.put("fluidRestTime", "0.8");
               v.put("detailStrength", "0.7");
               v.put("detailScale", "4");
               v.put("lavaResolution", "1");
               v.put("slimeResolution", "2");
               v.put("sandResolution", "4");
               v.put("sandMaxStepsPerFrame", "4");
               v.put("sandGrainsPerParticle", "12");
               v.put("leafResolution", "4");
               v.put("leavesPerBlock", "60");
            }
            case TSUNAMI -> {
               v.put("fluidRestSpeed", "0.25");
               v.put("fluidRestTime", "1.0");
               v.put("detailStrength", "0.5");
               v.put("detailScale", "6");
               v.put("resolution", "2");
               v.put("surfaceSubdivision", "2");
               v.put("maxStepsPerFrame", "2");
               v.put("whitewater", "false");
               v.put("dropletThreshold", "0");
               v.put("lavaResolution", "2");
               v.put("slimeResolution", "2");
               v.put("sandResolution", "4");
               v.put("sandMaxStepsPerFrame", "4");
               v.put("sandGrainsPerParticle", "16");
               v.put("leafResolution", "6");
               v.put("leavesPerBlock", "100");
            }
            case BALANCED -> {
               v.put("fluidRestSpeed", "0.15");
               v.put("fluidRestTime", "1.5");
               v.put("detailStrength", "0.4");
               v.put("detailScale", "6");
               v.put("resolution", "4");
               v.put("surfaceSubdivision", "1");
               v.put("maxStepsPerFrame", "3");
               v.put("whitewater", "true");
               v.put("dropletThreshold", "0.35");
               v.put("lavaResolution", "3");
               v.put("slimeResolution", "3");
               v.put("sandResolution", "6");
               v.put("sandMaxStepsPerFrame", "6");
               v.put("sandGrainsPerParticle", "16");
               v.put("leafResolution", "8");
               v.put("leavesPerBlock", "150");
            }
            case QUALITY -> {
               v.put("fluidRestSpeed", "0.15");
               v.put("fluidRestTime", "1.5");
               v.put("detailStrength", "0.4");
               v.put("detailScale", "6");
               v.put("resolution", "5");
               v.put("surfaceSubdivision", "2");
               v.put("maxStepsPerFrame", "4");
               v.put("whitewater", "true");
               v.put("dropletThreshold", "0.35");
               v.put("lavaResolution", "4");
               v.put("slimeResolution", "4");
               v.put("sandResolution", "8");
               v.put("sandMaxStepsPerFrame", "8");
               v.put("sandGrainsPerParticle", "24");
               v.put("leafResolution", "8");
               v.put("leavesPerBlock", "200");
            }
         }

         return v;
      }
   }

   private WaterPresets() {
   }

   public static Preset current() {
      try {
         return Preset.valueOf(PanelConfig.preset);
      } catch (IllegalArgumentException e) {
         return Preset.TSUNAMI;
      }
   }

   public static void apply(Preset preset) {
      for (Map.Entry<String, String> e : preset.settings().entrySet()) {
         set(e.getKey(), e.getValue());
      }

      PanelConfig.preset = preset.name();
      PanelConfig.save();
      WaterSettings.save();
   }

   static void set(String name, String value) {
      SafeResolution.cancel(name);
      try {
         WaterSettings.set(name, value);
      } catch (NumberFormatException | ReflectiveOperationException e) {
         RealisticWater.LOGGER.warn("Couldn't set {} to {}", name, value, e);
      }
   }

   static String get(String name) {
      try {
         return WaterSettings.get(name);
      } catch (ReflectiveOperationException e) {
         return "";
      }
   }
}
