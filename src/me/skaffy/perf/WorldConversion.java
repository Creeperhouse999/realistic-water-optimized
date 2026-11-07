package me.skaffy.perf;

import me.skaffy.ModContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Optional world conversion: turns vanilla water, lava and sand near players into the realistic versions.
 *
 * Controlled by game rules, so it can be switched on in Create World, More, Game Rules (category
 * "Realistic Water"), with /gamerule, or from the control panel in singleplayer. Everything is off by default.
 *
 * Only blocks within a radius of each player are converted, and only a limited number per second, so a whole
 * ocean is never turned into one giant simulation at once. The scan is spread over ticks: each tick handles one
 * horizontal layer of the cube around each player.
 */
public final class WorldConversion implements ModInitializer {
   public static final GameRuleCategory CATEGORY = GameRuleCategory.register(Identifier.fromNamespaceAndPath("realistic-water", "conversion"));

   public static final GameRule<Boolean> CONVERT_WATER = GameRuleBuilder.forBoolean(false)
      .category(CATEGORY)
      .buildAndRegister(Identifier.fromNamespaceAndPath("realistic-water", "convert_water"));
   public static final GameRule<Boolean> CONVERT_LAVA = GameRuleBuilder.forBoolean(false)
      .category(CATEGORY)
      .buildAndRegister(Identifier.fromNamespaceAndPath("realistic-water", "convert_lava"));
   public static final GameRule<Boolean> CONVERT_SAND = GameRuleBuilder.forBoolean(false)
      .category(CATEGORY)
      .buildAndRegister(Identifier.fromNamespaceAndPath("realistic-water", "convert_sand"));
   public static final GameRule<Integer> RADIUS = GameRuleBuilder.forInteger(12)
      .range(4, 48)
      .category(CATEGORY)
      .buildAndRegister(Identifier.fromNamespaceAndPath("realistic-water", "convert_radius"));
   public static final GameRule<Integer> PER_SECOND = GameRuleBuilder.forInteger(200)
      .range(10, 5000)
      .category(CATEGORY)
      .buildAndRegister(Identifier.fromNamespaceAndPath("realistic-water", "convert_per_second"));

   private static int lastServerTick = -1;
   private static int usedThisTick;

   @Override
   public void onInitialize() {
      ServerTickEvents.END_LEVEL_TICK.register(WorldConversion::tick);
   }

   private static void tick(ServerLevel level) {
      GameRules rules = level.getGameRules();
      boolean water = rules.get(CONVERT_WATER);
      boolean lava = rules.get(CONVERT_LAVA);
      boolean sand = rules.get(CONVERT_SAND);
      if (!water && !lava && !sand) {
         return;
      }

      // One budget per server tick, shared by all dimensions.
      int serverTick = level.getServer().getTickCount();
      if (serverTick != lastServerTick) {
         lastServerTick = serverTick;
         usedThisTick = 0;
      }

      int radius = rules.get(RADIUS);
      int budget = Math.max(1, rules.get(PER_SECOND) / 20) - usedThisTick;
      if (budget <= 0) {
         return;
      }

      int span = radius * 2 + 1;
      long t = level.getGameTime();
      for (ServerPlayer player : level.players()) {
         if (player.isSpectator()) {
            continue;
         }

         BlockPos center = player.blockPosition();
         int dy = (int)(t % span) - radius;
         int done = convertLayer(level, center, dy, radius, water, lava, sand, budget);
         usedThisTick += done;
         budget -= done;
         if (budget <= 0) {
            return;
         }
      }
   }

   /** Converts matching blocks in one horizontal layer of the sphere around {@code center}; returns how many were converted. */
   private static int convertLayer(ServerLevel level, BlockPos center, int dy, int radius, boolean water, boolean lava, boolean sand, int budget) {
      int y = center.getY() + dy;
      if (y < level.getMinY() || y > level.getMaxY()) {
         return 0;
      }

      int r2 = radius * radius;
      int converted = 0;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dy * dy + dz * dz > r2) {
               continue;
            }

            pos.set(center.getX() + dx, y, center.getZ() + dz);
            if (!level.isLoaded(pos)) {
               continue;
            }

            BlockState state = level.getBlockState(pos);
            Block target = target(level, pos, below, state, water, lava, sand);
            if (target != null) {
               level.setBlock(pos, target.defaultBlockState(), Block.UPDATE_ALL);
               if (++converted >= budget) {
                  return converted;
               }
            }
         }
      }

      return converted;
   }

   private static Block target(ServerLevel level, BlockPos pos, BlockPos.MutableBlockPos below, BlockState state, boolean water, boolean lava, boolean sand) {
      FluidState fluid = state.getFluidState();
      if (!fluid.isEmpty() && fluid.isSource() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
         if (water && fluid.getType() == Fluids.WATER) {
            return ModContent.REALISTIC_WATER_BLOCK;
         }

         if (lava && fluid.getType() == Fluids.LAVA) {
            return ModContent.REALISTIC_LAVA_BLOCK;
         }

         return null;
      }

      if (sand && state.is(BlockTags.SAND) && state.is(Blocks.SAND)) {
         // Only sand that could actually fall or slide: nothing solid underneath. Converting whole beaches would
         // create millions of grains.
         below.set(pos.getX(), pos.getY() - 1, pos.getZ());
         BlockState under = level.getBlockState(below);
         if (under.isAir() || !under.getFluidState().isEmpty()) {
            return ModContent.REALISTIC_SAND;
         }
      }

      return null;
   }
}
