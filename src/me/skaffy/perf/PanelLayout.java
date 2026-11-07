package me.skaffy.perf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which mod settings the control panel and the Sodium page show, and in which groups.
 *
 * Kept apart from the screens (no Minecraft classes) so the offline tests in tools/test can check that every name
 * here is a real setting: the panel silently skips unknown names, so a typo would just make a slider disappear.
 */
public final class PanelLayout {
   /** Water tab: group heading to setting names. */
   public static final Map<String, List<String>> WATER = groups(
      "Simulation", List.of("resolution", "maxStepsPerFrame", "maxSpeed", "flipRatio", "fluidRestSpeed", "fluidRestTime"),
      "Surface and look", List.of("surfaceSubdivision", "dropletThreshold", "detailStrength", "detailScale", "refraction", "roughness", "specular", "underwaterFog"),
      "Foam", List.of("whitewater", "foamCoverage", "foamSize"),
      "Behaviour", List.of("sourceFill", "bucketMakesSource", "flowSounds", "flowSoundVolume")
   );

   /** Materials tab: group heading to setting names. */
   public static final Map<String, List<String>> MATERIALS = groups(
      "Lava", List.of("lavaResolution", "lavaIgniteFlammables", "lavaWaterMixing", "lavaFlowSounds"),
      "Slime", List.of("slimeResolution", "slimeViscosity", "slimeAdhesion", "slimeSounds"),
      "Sand", List.of("sandResolution", "sandMaxStepsPerFrame", "sandGrainsPerParticle", "sandGrainSize", "sandFrictionAngle", "sandSounds"),
      "Leaves", List.of("leafResolution", "leavesPerBlock", "leafWindStrength", "leafSounds"),
      "Water pump", List.of("waveMakerRate", "waveMakerSpeed")
   );

   /** Home tab quick settings (next to the vanilla view distance and frame rate options). */
   public static final List<String> HOME_QUICK = List.of("resolution", "whitewater");

   /** The headline settings on the Sodium video settings page. */
   public static final List<String> SODIUM_QUICK = List.of(
      "resolution", "maxStepsPerFrame", "surfaceSubdivision", "whitewater", "lavaResolution", "sandResolution", "leafResolution"
   );

   private PanelLayout() {
   }

   private static Map<String, List<String>> groups(Object... headingThenNames) {
      Map<String, List<String>> m = new LinkedHashMap<>();
      for (int i = 0; i + 1 < headingThenNames.length; i += 2) {
         @SuppressWarnings("unchecked")
         List<String> names = (List<String>)headingThenNames[i + 1];
         m.put((String)headingThenNames[i], names);
      }

      return java.util.Collections.unmodifiableMap(m);
   }
}
