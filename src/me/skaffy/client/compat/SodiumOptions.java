package me.skaffy.client.compat;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import me.skaffy.RealisticWater;
import me.skaffy.client.water.WaterSettings;
import me.skaffy.client.water.WaterSettingsInfo;
import me.skaffy.client.water.WaterSettingsScreen;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.StatefulOptionBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class SodiumOptions implements ConfigEntryPoint {
   private static final StorageEventHandler SAVE = WaterSettings::save;

   public void registerConfigLate(ConfigBuilder builder) {
      ModOptionsBuilder mod = builder.registerOwnModOptions();
      Set<String> ids = new HashSet<>();
      OptionPageBuilder first = builder.createOptionPage().setName(Component.translatable("realistic-water.settings.title"));
      first.addOption(
         builder.createExternalButtonOption(id("open_control_panel", ids))
            .setName(Component.literal("Control panel"))
            .setTooltip(Component.literal("Presets, Auto FPS, world conversion and every setting in tabs. Also opens with K, /waterpanel or /water gui."))
            .setScreenConsumer(screen -> Minecraft.getInstance().gui.setScreen(new me.skaffy.perf.ControlPanelScreen(screen)))
      );
      first.addOption(
         builder.createExternalButtonOption(id("open_water_gui", ids))
            .setName(Component.literal("All settings"))
            .setTooltip(Component.literal("Opens the control panel on the All settings tab: every setting of the mod by category."))
            .setScreenConsumer(screen -> Minecraft.getInstance().gui.setScreen(new me.skaffy.perf.ControlPanelScreen(screen, me.skaffy.perf.ControlPanelScreen.Tab.ALL, "")))
      );
      // The fork's control panel replaces the original 19 pages of raw options; Sodium keeps only the headline
      // settings, everything else is one click away in the panel.
      OptionGroupBuilder quick = builder.createOptionGroup().setName(Component.literal("Quick settings"));
      for (String name : new String[]{"resolution", "maxStepsPerFrame", "surfaceSubdivision", "whitewater", "lavaResolution", "sandResolution", "leafResolution"}) {
         WaterSettingsInfo.Info info = WaterSettingsInfo.get(name);
         OptionBuilder option = info == null ? null : option(builder, info, ids);
         if (option != null) {
            quick.addOption(option);
         }
      }

      first.addOptionGroup(quick);
      mod.addPage(first);
   }

   @Nullable
   private static OptionBuilder option(ConfigBuilder builder, WaterSettingsInfo.Info info, Set<String> ids) {
      String name = info.name();

      StatefulOptionBuilder<?> option = (StatefulOptionBuilder<?>)(switch (info.kind()) {
         case SWITCH -> builder.createBooleanOption(id(name, ids))
            .setDefaultValue(Boolean.parseBoolean(WaterSettings.defaultValue(name)))
            .setBinding(on -> set(name, on.toString()), () -> Boolean.parseBoolean(get(name)));
         case INTEGER -> {
            int min = (int)info.min();
            int max = Math.max((int)info.max(), min);
            yield builder.createIntegerOption(id(name, ids))
               .setRange(min, max, 1)
               .setValueFormatter(v -> Component.literal(String.valueOf(v)))
               .setDefaultValue(clamp(parseInt(WaterSettings.defaultValue(name), min), min, max))
               .setBinding(v -> set(name, String.valueOf(v)), () -> clamp(parseInt(get(name), min), min, max));
         }
         case NUMBER -> {
            double step = info.step() > 0.0 ? info.step() : (info.max() - info.min()) / 1000.0;
            int steps = step > 0.0 ? Math.max(1, (int)Math.round((info.max() - info.min()) / step)) : 1;
            yield builder.createIntegerOption(id(name, ids))
               .setRange(0, steps, 1)
               .setValueFormatter(i -> Component.literal(WaterSettingsScreen.format(info, info.min() + i * step)))
               .setDefaultValue(index(WaterSettings.defaultValue(name), info, step, steps))
               .setBinding(i -> set(name, WaterSettingsScreen.format(info, info.min() + i.intValue() * step)), () -> index(get(name), info, step, steps));
         }
         case TEXT -> null;
      });
      if (option == null) {
         return null;
      } else {
         option.setName(Component.literal(info.label())).setTooltip(WaterSettingsScreen.tooltip(info)).setStorageHandler(SAVE);

         OptionImpact impact = switch (info.cost()) {
            case NONE -> null;
            case LOW -> OptionImpact.LOW;
            case MEDIUM -> OptionImpact.MEDIUM;
            case HIGH, EXTREME -> OptionImpact.HIGH;
         };
         if (impact != null) {
            option.setImpact(impact);
         }

         return option;
      }
   }

   private static Identifier id(String name, Set<String> ids) {
      String path = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");

      while (!ids.add(path)) {
         path = path + "_";
      }

      return Identifier.fromNamespaceAndPath("realistic-water", path);
   }

   private static int index(String value, WaterSettingsInfo.Info info, double step, int steps) {
      try {
         return clamp((int)Math.round((Double.parseDouble(value) - info.min()) / step), 0, steps);
      } catch (NumberFormatException var6) {
         return 0;
      }
   }

   private static int parseInt(String value, int fallback) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException var3) {
         return fallback;
      }
   }

   private static int clamp(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private static String get(String name) {
      try {
         return WaterSettings.get(name);
      } catch (ReflectiveOperationException var2) {
         return "";
      }
   }

   private static void set(String name, String value) {
      try {
         WaterSettings.set(name, value);
      } catch (NumberFormatException | ReflectiveOperationException var3) {
         RealisticWater.LOGGER.warn("Couldn't set {} to {}", new Object[]{name, value, var3});
      }
   }
}
