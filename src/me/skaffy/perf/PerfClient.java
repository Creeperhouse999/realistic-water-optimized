package me.skaffy.perf;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Performance fork additions: the control panel (key K or /waterpanel) and Auto FPS (/autofps).
 * Registered as an extra client entrypoint so the original mod's classes stay untouched.
 */
public final class PerfClient implements ClientModInitializer {
   private static KeyMapping openPanel;
   private static boolean keyChecked;
   private static long lastSecond;

   @Override
   public void onInitializeClient() {
      PanelConfig.load();
      KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("realistic-water", "main"));
      openPanel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.realistic-water.panel", com.mojang.blaze3d.platform.InputConstants.KEY_K, category));
      ClientTickEvents.END_CLIENT_TICK.register(PerfClient::tick);
      ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
         try {
            AutoFps.onStopping(mc);
         } catch (RuntimeException e) {
            me.skaffy.RealisticWater.LOGGER.warn("Couldn't restore settings on exit", e);
         }
      });
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
         dispatcher.register(ClientCommands.literal("waterpanel").executes(c -> {
            ControlPanelScreen.openNextTick();
            return 1;
         }));
         dispatcher.register(
            ClientCommands.literal("autofps")
               .executes(c -> {
                  c.getSource().sendFeedback(Component.literal(status()));
                  return 1;
               })
               .then(ClientCommands.literal("on").executes(c -> {
                  AutoFps.setEnabled(true);
                  c.getSource().sendFeedback(Component.literal("Auto FPS on, target " + PanelConfig.targetFps));
                  return 1;
               }))
               .then(ClientCommands.literal("off").executes(c -> {
                  AutoFps.setEnabled(false);
                  c.getSource().sendFeedback(Component.literal("Auto FPS off, your settings are back"));
                  return 1;
               }))
               .then(ClientCommands.literal("target").then(ClientCommands.argument("fps", IntegerArgumentType.integer(20, 240)).executes(c -> {
                  PanelConfig.targetFps = IntegerArgumentType.getInteger(c, "fps");
                  PanelConfig.save();
                  c.getSource().sendFeedback(Component.literal("Auto FPS target: " + PanelConfig.targetFps));
                  return 1;
               })))
         );
      });
   }

   private static String status() {
      if (!PanelConfig.autoFps) {
         return "Auto FPS is off (target " + PanelConfig.targetFps + "). Use /autofps on";
      }

      return "Auto FPS: " + AutoFps.levelName() + " (" + (AutoFps.level() + 1) + "/" + AutoFps.LEVELS.length + "), " + AutoFps.fps() + " FPS, target "
         + PanelConfig.targetFps;
   }

   private static void tick(Minecraft mc) {
      if (!keyChecked) {
         // Version 1 of the fork registered the key with a GLFW code, which Minecraft 26.3 reads as Page Up.
         // Move players who never rebound it to K.
         keyChecked = true;
         if (!PanelConfig.keyMigrated) {
            PanelConfig.keyMigrated = true;
            PanelConfig.save();
            if ("key.keyboard.page.up".equals(openPanel.saveString())) {
               openPanel.setKey(openPanel.getDefaultKey());
               KeyMapping.resetMapping();
               mc.options.save();
            }
         }
      }

      long second = System.currentTimeMillis() / 1000;
      if (second != lastSecond) {
         lastSecond = second;
         try {
            SafeResolution.tick();
         } catch (RuntimeException e) {
            me.skaffy.RealisticWater.LOGGER.warn("Couldn't apply a queued water setting", e);
         }
      }

      while (openPanel.consumeClick()) {
         if (mc.gui.screen() == null) {
            mc.gui.setScreen(new ControlPanelScreen(null));
         }
      }

      ControlPanelScreen.tickOpen(mc);
      try {
         AutoFps.tick(mc);
      } catch (RuntimeException e) {
         // Never let the governor take the game down; switch it off instead.
         me.skaffy.RealisticWater.LOGGER.error("Auto FPS failed, turning it off", e);
         try {
            AutoFps.setEnabled(false); // restores the player's own settings
         } catch (RuntimeException e2) {
            PanelConfig.autoFps = false;
            PanelConfig.save();
         }
      }
   }
}
