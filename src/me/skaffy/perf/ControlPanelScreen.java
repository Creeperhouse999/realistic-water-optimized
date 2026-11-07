package me.skaffy.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import me.skaffy.client.water.Material;
import me.skaffy.client.water.WaterSettings;
import me.skaffy.client.water.WaterSettingsInfo;
import me.skaffy.client.water.WaterSettingsScreen;
import me.skaffy.client.water.WaterWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

/**
 * The fork's control panel. It replaces the original long settings list (/water gui, the key binding and the
 * Sodium page button all open it) with tabs:
 *
 *   Home      presets, the few settings that matter most, live water stats, clean-up buttons
 *   Auto FPS  the FPS governor
 *   World     turning vanilla water, lava and sand into realistic ones (game rules of the current world)
 *   Water     the water settings worth touching, with cost colours in the tooltips
 *   Materials lava, slime, sand, leaves, water pump
 *   All       every setting of the mod, by category (the original list, same tooltips)
 */
public class ControlPanelScreen extends OptionsSubScreen {
   public enum Tab {
      HOME("Home"),
      AUTO("Auto FPS"),
      WORLD("World"),
      WATER("Water"),
      MATERIALS("Materials"),
      ALL("All settings");

      final String label;

      Tab(String label) {
         this.label = label;
      }
   }

   private static boolean openPending;
   private static Tab lastTab = Tab.HOME;
   private static String lastCategory = "";

   private final Tab tab;
   private final String category;
   private StringWidget autoStatus;
   private StringWidget waterStatus;
   private int refresh;

   public ControlPanelScreen(@Nullable Screen lastScreen) {
      this(lastScreen, lastTab, lastCategory);
   }

   public ControlPanelScreen(@Nullable Screen lastScreen, Tab tab, String category) {
      super(lastScreen, Minecraft.getInstance().options, Component.literal("Realistic Water"));
      this.tab = tab;
      List<String> categories = WaterSettingsInfo.categories();
      this.category = categories.contains(category) ? category : (categories.isEmpty() ? "" : categories.getFirst());
      lastTab = tab;
      lastCategory = this.category;
   }

   public static void openNextTick() {
      openPending = true;
   }

   /** Client tick: opens the panel when asked to, and replaces the original settings screen with the panel. */
   public static void tickOpen(Minecraft mc) {
      if (openPending) {
         openPending = false;
         mc.gui.setScreen(new ControlPanelScreen(mc.gui.screen()));
      } else if (mc.gui.screen() instanceof WaterSettingsScreen && !(mc.gui.screen() instanceof Legacy)) {
         // /water gui and any other path into the original list land in the panel instead.
         mc.gui.setScreen(new ControlPanelScreen(null, Tab.HOME, lastCategory));
      }
   }

   // ---------------------------------------------------------------------------------------------------------------
   // Layout

   @Override
   protected void addTitle() {
      this.layout.setHeaderHeight(58);
      LinearLayout column = this.layout.addToHeader(LinearLayout.vertical().spacing(6));
      column.addChild(
         new StringWidget(Component.literal("Realistic Water").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA)
            .append(Component.literal("  control panel").withStyle(ChatFormatting.GRAY)), this.font),
         s -> s.alignHorizontallyCenter()
      );
      LinearLayout tabs = column.addChild(LinearLayout.horizontal().spacing(3), s -> s.alignHorizontallyCenter());
      for (Tab t : Tab.values()) {
         int width = Math.max(44, this.font.width(t.label) + 14);
         Button b = Button.builder(Component.literal(t.label), button -> this.switchTo(t, this.category)).width(width).build();
         b.active = t != this.tab;
         tabs.addChild(b);
      }
   }

   private void switchTo(Tab t, String cat) {
      this.minecraft.gui.setScreen(new ControlPanelScreen(this.lastScreen, t, cat));
   }

   @Override
   protected void addOptions() {
      switch (this.tab) {
         case HOME -> this.home();
         case AUTO -> this.autoFps();
         case WORLD -> this.world();
         case WATER -> this.settings(PanelLayout.WATER);
         case MATERIALS -> this.settings(PanelLayout.MATERIALS);
         case ALL -> this.all();
      }
   }

   @Override
   protected void addFooter() {
      LinearLayout buttons = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
      buttons.addChild(Button.builder(Component.literal("Reset to preset"), b -> {
         WaterPresets.apply(WaterPresets.current());
         this.switchTo(this.tab, this.category);
      }).tooltip(Tooltip.create(Component.literal("Puts every setting the current preset controls back to the preset's value."))).build());
      buttons.addChild(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).build());
   }

   @Override
   public void removed() {
      super.removed();
      PanelConfig.save();
      WaterSettings.save();
      // While Auto FPS runs, the live options are its temporary choices; saving them would overwrite the
      // player's own settings, which Auto FPS restores when it is turned off.
      if (!PanelConfig.autoFps) {
         this.options.save();
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (++this.refresh % 10 == 0) {
         if (this.autoStatus != null) {
            this.autoStatus.setMessage(this.autoStatusText());
         }

         if (this.waterStatus != null) {
            this.waterStatus.setMessage(this.waterStatusText());
         }
      }
   }

   // ---------------------------------------------------------------------------------------------------------------
   // Tabs

   private void home() {
      this.heading("Water preset");
      CycleButton<WaterPresets.Preset> preset = CycleButton.<WaterPresets.Preset>builder(p -> Component.literal(p.label), WaterPresets.current())
         .withValues(Arrays.asList(WaterPresets.Preset.values()))
         .withTooltip(p -> Tooltip.create(Component.literal(p.description)))
         .create(Component.literal("Preset"), (button, value) -> {
            WaterPresets.apply(value);
            this.switchTo(Tab.HOME, this.category);
         });
      this.list.addBig(preset);
      this.note(WaterPresets.current().description);

      this.heading("Quick settings");
      if (PanelConfig.autoFps) {
         this.note("Auto FPS is on: it manages view distance and water detail, so changes here are temporary.");
      }

      this.list.addSmall(this.options.renderDistance(), this.options.simulationDistance());
      this.list.addSmall(this.options.framerateLimit(), this.options.enableVsync());
      this.list.addSmall(this.setting(PanelLayout.HOME_QUICK.get(0)), this.setting(PanelLayout.HOME_QUICK.get(1)));

      this.heading("Auto FPS");
      CycleButton<Boolean> auto = CycleButton.onOffBuilder(PanelConfig.autoFps)
         .create(Component.literal("Auto FPS"), (button, on) -> AutoFps.setEnabled(on));
      this.list.addSmall(auto, new TargetSlider());
      this.autoStatus = this.line(this.autoStatusText());

      this.heading("Water right now");
      this.waterStatus = this.line(this.waterStatusText());
      this.list.addSmall(
         Button.builder(Component.literal("Remove water"), b -> WaterWorld.get().removeAll(this.options.renderDistance().get() + 2, Material.WATER))
            .tooltip(Tooltip.create(Component.literal("Removes realistic water around you, like /water remove. Use it if a flood got out of hand.")))
            .build(),
         Button.builder(Component.literal("Remove everything"), b -> {
               int radius = this.options.renderDistance().get() + 2;
               for (Material m : List.of(Material.WATER, Material.LAVA, Material.SLIME, Material.SAND, Material.LEAVES)) {
                  WaterWorld.get().removeAll(radius, m);
               }
            })
            .tooltip(Tooltip.create(Component.literal("Removes realistic water, lava, slime, sand and leaves around you.")))
            .build()
      );
   }

   private void autoFps() {
      this.heading("Auto FPS");
      this.note("Keeps the game near your target FPS by changing render distance, clouds, particles and water quality in the background. Turning it off puts your own settings back.");
      CycleButton<Boolean> auto = CycleButton.onOffBuilder(PanelConfig.autoFps)
         .create(Component.literal("Auto FPS"), (button, on) -> AutoFps.setEnabled(on));
      this.list.addSmall(auto, new TargetSlider());
      CycleButton<PanelConfig.Adjust> adjust = CycleButton.<PanelConfig.Adjust>builder(a -> Component.literal(a.label), PanelConfig.adjust)
         .withValues(Arrays.asList(PanelConfig.Adjust.values()))
         .withTooltip(a -> Tooltip.create(Component.literal("What Auto FPS is allowed to change.")))
         .create(Component.literal("Adjusts"), (button, value) -> {
            PanelConfig.adjust = value;
            PanelConfig.save();
         });
      CycleButton<Boolean> notify = CycleButton.onOffBuilder(PanelConfig.notify)
         .withTooltip(v -> Tooltip.create(Component.literal("Show a short message above the hotbar when Auto FPS changes quality.")))
         .create(Component.literal("Messages"), (button, on) -> {
            PanelConfig.notify = on;
            PanelConfig.save();
         });
      this.list.addSmall(adjust, notify);
      this.autoStatus = this.line(this.autoStatusText());

      this.heading("Quality levels");
      for (int i = 0; i < AutoFps.LEVELS.length; i++) {
         AutoFps.Level l = AutoFps.LEVELS[i];
         this.line(Component.literal(String.format(Locale.ROOT, "%d. %-9s  view %2d  water detail %d%s", i + 1, l.name(), l.renderDistance(), l.waterResolution(),
               l.whitewater() ? "  foam" : ""))
            .withStyle(i == AutoFps.level() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
      }

      this.note("Water detail only changes while no water is moving, so floods never vanish mid-wave.");
   }

   private void world() {
      this.heading("Realistic world");
      this.note("Turns vanilla lakes, ponds, waterfalls, lava pools and falling sand near players into realistic ones. Each lake is converted whole.");
      IntegratedServer server = this.minecraft.getSingleplayerServer();
      if (server == null || server.overworld() == null) {
         this.note("Only in singleplayer from here. On a server use /gamerule realistic-water:convert_water true.");
         this.note("For a new world: Create World, More, Game Rules, Realistic Water.");
         return;
      }

      this.list.addSmall(this.ruleToggle(server, "Water", WorldConversion.CONVERT_WATER, "Lakes, ponds and waterfalls become realistic water, one whole body at a time. Bodies bigger than Max body (oceans, big rivers) stay vanilla: half an ocean would drain into the other half."),
         this.ruleToggle(server, "Lava", WorldConversion.CONVERT_LAVA, "Lava pools and lava falls become realistic lava, one whole body at a time."));
      this.list.addSmall(this.ruleToggle(server, "Falling sand", WorldConversion.CONVERT_SAND, "Sand with nothing solid under it becomes realistic sand."),
         new RuleSlider(server, "Radius", WorldConversion.RADIUS, 4, 48, 1, "How far around each player lakes and sand are looked for. A lake that reaches into the radius is converted whole."));
      this.list.addSmall(new RuleSlider(server, "Per second", WorldConversion.PER_SECOND, 10, 2000, 10,
            "Average blocks converted per second. A lake waits until enough has built up, then converts at once."),
         new RuleSlider(server, "Max body", WorldConversion.MAX_BODY, 256, 16384, 256,
            "Biggest lake (in blocks) that is converted at once. Bigger bodies stay vanilla. Bigger = heavier simulations and a longer hitch when it converts."));
      this.list.addBig(Button.builder(Component.literal("Ocean preset"), b -> {
               WaterPresets.apply(WaterPresets.Preset.OCEAN);
               this.switchTo(Tab.WORLD, this.category);
            })
            .tooltip(Tooltip.create(Component.literal("Switches to the Ocean water preset, made for huge converted water bodies.")))
            .build());
      this.note("Heavy! Start small. Converted blocks stay converted; the rules are saved with the world.");
   }

   private void settings(java.util.Map<String, List<String>> groups) {
      for (java.util.Map.Entry<String, List<String>> group : groups.entrySet()) {
         this.heading(group.getKey());
         List<AbstractWidget> widgets = new ArrayList<>();
         for (String name : group.getValue()) {
            AbstractWidget w = this.setting(name);
            if (w != null) {
               widgets.add(w);
            }
         }

         this.list.addSmall(widgets);
      }

      this.note("Hover a setting for what it does and how much it costs.");
   }

   private void all() {
      CycleButton<String> categoryButton = CycleButton.builder(Component::literal, this.category)
         .withValues(WaterSettingsInfo.categories())
         .create(Component.literal("Category"), (button, value) -> this.switchTo(Tab.ALL, value));
      categoryButton.setTooltip(Tooltip.create(Component.literal("Every setting of the mod, grouped like /water set. Hover a setting for details.")));
      this.list.addBig(categoryButton);
      List<AbstractWidget> widgets = new ArrayList<>();
      for (String name : WaterSettingsInfo.names(this.category)) {
         AbstractWidget w = this.setting(name);
         if (w != null) {
            widgets.add(w);
         }
      }

      this.list.addSmall(widgets);
   }

   // ---------------------------------------------------------------------------------------------------------------
   // Widgets

   private void heading(String text) {
      this.list.addBig(new StringWidget(310, 20, Component.literal(text).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA), this.font));
   }

   private StringWidget line(Component text) {
      StringWidget w = new StringWidget(310, 12, text, this.font);
      this.list.addBig(w);
      return w;
   }

   private void note(String text) {
      this.list.addBig(new MultiLineTextWidget(Component.literal(text).withStyle(ChatFormatting.GRAY), this.font).setMaxWidth(310).setMaxRows(2));
   }

   /** A widget for one mod setting, built from the mod's own setting descriptions. Null if the setting doesn't exist. */
   private @Nullable AbstractWidget setting(String name) {
      WaterSettingsInfo.Info info = WaterSettingsInfo.get(name);
      if (info == null) {
         return null;
      }

      AbstractWidget widget = switch (info.kind()) {
         case SWITCH -> CycleButton.onOffBuilder(Boolean.parseBoolean(WaterPresets.get(name)))
            .create(Component.literal(info.label()), (button, on) -> WaterPresets.set(name, on.toString()));
         case TEXT -> {
            EditBox box = new EditBox(this.font, 150, 20, Component.literal(info.label()));
            box.setMaxLength(1024);
            box.setValue(WaterPresets.get(name));
            box.setHint(Component.literal(info.label()).withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(text -> WaterPresets.set(name, text.isEmpty() ? "-" : text));
            yield box;
         }
         default -> new SettingSlider(info);
      };
      widget.setTooltip(Tooltip.create(WaterSettingsScreen.tooltip(info)));
      return widget;
   }

   private CycleButton<Boolean> ruleToggle(IntegratedServer server, String label, GameRule<Boolean> rule, String tooltip) {
      boolean value = server.overworld().getGameRules().get(rule);
      return CycleButton.onOffBuilder(value)
         .withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
         .create(Component.literal(label), (button, on) -> server.execute(() -> server.overworld().getGameRules().set(rule, on, server)));
   }

   private Component autoStatusText() {
      if (!PanelConfig.autoFps) {
         return Component.literal("Off: using your own settings").withStyle(ChatFormatting.GRAY);
      }

      int target = AutoFps.effectiveTarget(this.options);
      String waiting = SafeResolution.hasPending() ? "  (water detail waits for calm water)" : "";
      return Component.literal("Now: " + AutoFps.levelName() + " (" + (AutoFps.level() + 1) + "/" + AutoFps.LEVELS.length + ")  " + AutoFps.fps() + " / " + target
            + " FPS" + waiting)
         .withStyle(ChatFormatting.GREEN);
   }

   private Component waterStatusText() {
      String s;
      try {
         s = WaterWorld.get().statsLine();
      } catch (RuntimeException e) {
         return Component.literal("No water data").withStyle(ChatFormatting.GRAY);
      }

      long simulated = WaterStats.simulated(s);
      String frame = WaterStats.frameTime(s);
      if (!frame.isEmpty()) {
         frame = "  |  " + frame;
      }

      if (simulated < 0) {
         return Component.literal("No water data").withStyle(ChatFormatting.GRAY);
      }

      if (simulated == 0) {
         return Component.literal("Nothing moving" + frame).withStyle(ChatFormatting.GRAY);
      }

      return Component.literal(String.format(Locale.ROOT, "%,d particles moving", simulated) + frame).withStyle(ChatFormatting.GRAY);
   }

   /** Slider for one numeric mod setting. Settings that rebuild the simulation apply when the mouse is released. */
   private static final class SettingSlider extends AbstractSliderButton {
      private final WaterSettingsInfo.Info info;
      private final boolean onRelease;

      SettingSlider(WaterSettingsInfo.Info info) {
         super(0, 0, 150, 20, Component.empty(), position(info));
         this.info = info;
         this.onRelease = SafeResolution.rebuilds(info.name());
         this.updateMessage();
      }

      private static double position(WaterSettingsInfo.Info info) {
         double span = info.max() - info.min();
         if (span <= 0.0) {
            return 0.0;
         }

         try {
            return Math.clamp((Double.parseDouble(WaterPresets.get(info.name())) - info.min()) / span, 0.0, 1.0);
         } catch (NumberFormatException e) {
            return 0.0;
         }
      }

      private double picked() {
         double v = this.info.min() + this.value * (this.info.max() - this.info.min());
         if (this.info.step() > 0.0) {
            v = this.info.min() + Math.round((v - this.info.min()) / this.info.step()) * this.info.step();
         }

         return Math.clamp(v, this.info.min(), this.info.max());
      }

      @Override
      protected void updateMessage() {
         this.setMessage(Component.literal(this.info.label() + ": " + WaterSettingsScreen.format(this.info, this.picked())));
      }

      @Override
      protected void applyValue() {
         if (!this.onRelease) {
            WaterPresets.set(this.info.name(), WaterSettingsScreen.format(this.info, this.picked()));
         }
      }

      @Override
      public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
         super.onRelease(event);
         if (this.onRelease) {
            String v = WaterSettingsScreen.format(this.info, this.picked());
            if (!v.equals(WaterPresets.get(this.info.name()))) {
               WaterPresets.set(this.info.name(), v);
            }
         }
      }
   }

   private static final class TargetSlider extends AbstractSliderButton {
      private static final int MIN = 30;
      private static final int MAX = 144;

      TargetSlider() {
         super(0, 0, 150, 20, Component.empty(), (PanelConfig.targetFps - MIN) / (double)(MAX - MIN));
         this.setTooltip(Tooltip.create(Component.literal("The FPS Auto FPS aims for. It never goes above your Max Framerate setting.")));
         this.updateMessage();
      }

      private int picked() {
         int v = (int)Math.round(MIN + this.value * (MAX - MIN));
         return Math.round(v / 5.0F) * 5;
      }

      @Override
      protected void updateMessage() {
         this.setMessage(Component.literal("Target: " + this.picked() + " FPS"));
      }

      @Override
      protected void applyValue() {
         PanelConfig.targetFps = this.picked();
      }

      @Override
      public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
         super.onRelease(event);
         PanelConfig.save();
      }
   }

   /** Slider for an integer game rule of the singleplayer world, applied on the server thread when released. */
   private static final class RuleSlider extends AbstractSliderButton {
      private final IntegratedServer server;
      private final String label;
      private final GameRule<Integer> rule;
      private final int min;
      private final int max;

      private final int step;

      RuleSlider(IntegratedServer server, String label, GameRule<Integer> rule, int min, int max, int step, String tooltip) {
         super(0, 0, 150, 20, Component.empty(), (Math.clamp(server.overworld().getGameRules().get(rule), min, max) - min) / (double)(max - min));
         this.server = server;
         this.label = label;
         this.rule = rule;
         this.min = min;
         this.max = max;
         this.step = step;
         this.setTooltip(Tooltip.create(Component.literal(tooltip)));
         this.updateMessage();
      }

      private int picked() {
         int v = (int)Math.round(this.min + this.value * (this.max - this.min));
         return Math.clamp((long)Math.round(v / (double)this.step) * this.step, this.min, this.max);
      }

      @Override
      protected void updateMessage() {
         this.setMessage(Component.literal(this.label + ": " + this.picked()));
      }

      @Override
      protected void applyValue() {
      }

      @Override
      public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
         super.onRelease(event);
         int v = this.picked();
         this.server.execute(() -> this.server.overworld().getGameRules().set(this.rule, v, this.server));
      }
   }

   /** Marker for code that deliberately wants the original screen (none at the moment). */
   interface Legacy {
   }
}
