package me.skaffy.perf;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import me.skaffy.ModContent;
import me.skaffy.block.RealisticWaterBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * Water and lava are converted one whole connected body at a time (a pond, a lake, a waterfall with its pool), in a
 * single tick. Converting only part of a body does not work: the fork's ocean sink treats vanilla water as open
 * space that deletes realistic water, so a half-converted lake would drain into its own vanilla half forever. Bodies
 * bigger than {@link #MAX_BODY} (oceans, big rivers) or reaching into unloaded chunks are left vanilla.
 *
 * Inside a body: source blocks become realistic water (sources that feed flowing water, like the top of a waterfall,
 * become springs so the fall keeps running), flowing water becomes air, underwater plants (kelp, seagrass, bubble
 * columns) become water and waterlogged blocks are drained. Lava works the same way.
 *
 * The work is limited by a token bucket: {@link #PER_SECOND} blocks per second on average, bodies are converted
 * whole once enough tokens have built up.
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
   public static final GameRule<Integer> MAX_BODY = GameRuleBuilder.forInteger(4096)
      .range(64, 16384)
      .category(CATEGORY)
      .buildAndRegister(Identifier.fromNamespaceAndPath("realistic-water", "convert_max_body"));

   /** Blocks of a body that was too big (or not affordable yet) are not searched again until this game time. */
   private static final long RETRY_TOO_BIG_TICKS = 1200;
   private static final Direction[] FEEDS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
   private static final Direction[] ALL = Direction.values();
   private static final int MAX_SKIPPED = 400_000;

   private static final Map<ServerLevel, Long2LongOpenHashMap> skip = new WeakHashMap<>();
   private static int lastServerTick = -1;
   private static double tokens;
   private static int searchesThisTick;
   /** Tokens kept for a body that was found but couldn't be afforded yet, so small finds can't starve it. */
   private static int reserved;
   private static int reservedUntil;

   @Override
   public void onInitialize() {
      ServerTickEvents.END_LEVEL_TICK.register(WorldConversion::tick);
   }

   private enum Liquid {
      WATER,
      LAVA;

      boolean matches(FluidState fluid) {
         return this == WATER
            ? fluid.getType() == Fluids.WATER || fluid.getType() == Fluids.FLOWING_WATER
            : fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA;
      }

      Block realistic() {
         return this == WATER ? ModContent.REALISTIC_WATER_BLOCK : ModContent.REALISTIC_LAVA_BLOCK;
      }
   }

   private static void tick(ServerLevel level) {
      GameRules rules = level.getGameRules();
      boolean water = rules.get(CONVERT_WATER);
      boolean lava = rules.get(CONVERT_LAVA);
      boolean sand = rules.get(CONVERT_SAND);
      if (!water && !lava && !sand) {
         return;
      }

      int maxBody = rules.get(MAX_BODY);
      int perSecond = rules.get(PER_SECOND);

      // One token bucket for the whole server, refilled once per server tick (all dimensions share it).
      int serverTick = level.getServer().getTickCount();
      if (serverTick != lastServerTick) {
         lastServerTick = serverTick;
         searchesThisTick = 0;
         tokens = Math.min(Math.max(maxBody, perSecond), tokens + perSecond / 20.0);
         if (reserved > 0 && serverTick > reservedUntil) {
            reserved = 0; // the waiting body wasn't found again (player left): stop holding tokens for it
         }
      }

      if (tokens < 1) {
         return;
      }

      Long2LongOpenHashMap skipped = skip.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
      long now = level.getGameTime();
      if (now % 200 == 0) {
         skipped.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
      }

      if (skipped.size() > MAX_SKIPPED) {
         skipped.clear(); // bounded memory; at worst a big body is searched once more
      }

      int radius = rules.get(RADIUS);
      int span = radius * 2 + 1;
      // Searching a body costs up to maxBody block reads; allow fewer searches per tick when bodies may be huge.
      int maxSearches = maxBody <= 8192 ? 2 : 1;
      for (ServerPlayer player : level.players()) {
         if (player.isSpectator()) {
            continue;
         }

         int dy = (int)(now % span) - radius;
         if (!scanLayer(level, player.blockPosition(), dy, radius, water, lava, sand, maxBody, maxSearches, skipped, now)) {
            return;
         }
      }
   }

   /** Converts what it finds in one horizontal layer of the sphere around {@code center}. False when out of budget. */
   private static boolean scanLayer(
      ServerLevel level, BlockPos center, int dy, int radius, boolean water, boolean lava, boolean sand,
      int maxBody, int maxSearches, Long2LongOpenHashMap skipped, long now
   ) {
      int y = center.getY() + dy;
      if (y < level.getMinY() || y > level.getMaxY()) {
         return true;
      }

      int r2 = radius * radius;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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
            FluidState fluid = state.getFluidState();
            Liquid liquid = fluid.isEmpty() ? null : water && Liquid.WATER.matches(fluid) ? Liquid.WATER : lava && Liquid.LAVA.matches(fluid) ? Liquid.LAVA : null;
            if (liquid != null && !(state.getBlock() instanceof RealisticWaterBlock)) {
               if (skipped.get(pos.asLong()) > now) {
                  continue;
               }

               if (searchesThisTick >= maxSearches) {
                  return false;
               }

               searchesThisTick++;
               convertBody(level, pos.immutable(), liquid, maxBody, skipped, now);
               if (tokens < 1) {
                  return false;
               }
            } else if (sand && state.is(Blocks.SAND) && affordable(1) && canFall(level, pos)) {
               level.setBlock(pos, ModContent.REALISTIC_SAND.defaultBlockState(), Block.UPDATE_ALL);
               if (--tokens < 1) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   /** Only sand that could actually fall or slide: nothing solid underneath. Converting whole beaches would create millions of grains. */
   private static boolean canFall(ServerLevel level, BlockPos pos) {
      BlockState under = level.getBlockState(pos.below());
      return under.isAir() || !under.getFluidState().isEmpty();
   }

   /**
    * Finds the connected body of {@code liquid} containing {@code start} and converts all of it, or remembers its
    * blocks as "not now" if it is too big, not fully loaded, or more than the tokens available.
    */
   private static void convertBody(ServerLevel level, BlockPos start, Liquid liquid, int maxBody, Long2LongOpenHashMap skipped, long now) {
      LongOpenHashSet body = new LongOpenHashSet();
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      body.add(start.asLong());
      queue.enqueue(start.asLong());
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
      boolean complete = true;
      while (!queue.isEmpty()) {
         p.set(queue.dequeueLong());
         for (Direction d : ALL) {
            n.setWithOffset(p, d);
            long key = n.asLong();
            if (body.contains(key)) {
               continue;
            }

            if (!level.isLoaded(n)) {
               complete = false; // the body continues into unloaded chunks: can't convert all of it
               break;
            }

            BlockState s = level.getBlockState(n);
            if (s.getFluidState().isEmpty() || !liquid.matches(s.getFluidState())) {
               continue;
            }

            if (body.size() >= maxBody) {
               complete = false;
               break;
            }

            body.add(key);
            queue.enqueue(key);
         }

         if (!complete) {
            break;
         }
      }

      if (!complete || !affordable(body.size())) {
         long wait = RETRY_TOO_BIG_TICKS;
         if (complete) {
            wait = Math.min(RETRY_TOO_BIG_TICKS, 20 + (long)Math.ceil((body.size() + reservedFor(body.size()) - tokens) / Math.max(0.5, tokensPerTick(level))));
            if (body.size() >= reserved) {
               reserved = body.size();
               reservedUntil = level.getServer().getTickCount() + (int)wait + 400;
            }
         }

         // Every block found so far. Per block, not per chunk: an ocean must not block a pond next to it.
         // Scanning finds the ocean again from blocks not yet found, but each search adds up to maxBody more blocks,
         // so a few searches cover everything within the radius.
         long until = now + wait;
         for (long key : body) {
            skipped.put(key, until);
         }

         return;
      }

      // Decide every block first (springs depend on the original flowing neighbours), then write.
      LongArrayList positions = new LongArrayList(body.size());
      java.util.List<BlockState> targets = new java.util.ArrayList<>(body.size());
      for (long key : body) {
         p.set(key);
         BlockState s = level.getBlockState(p);
         positions.add(key);
         targets.add(target(level, p, n, s, liquid));
      }

      for (int i = 0; i < positions.size(); i++) {
         p.set(positions.getLong(i));
         level.setBlock(p, targets.get(i), Block.UPDATE_ALL);
      }

      tokens -= body.size();
      if (body.size() >= reserved) {
         reserved = 0;
      }
   }

   /** Tokens that must stay in the bucket after spending {@code cost}: the reservation, unless this is at least as big. */
   private static int reservedFor(int cost) {
      return cost >= reserved ? 0 : reserved;
   }

   private static boolean affordable(int cost) {
      return tokens >= cost + reservedFor(cost);
   }

   private static double tokensPerTick(ServerLevel level) {
      return level.getGameRules().get(PER_SECOND) / 20.0;
   }

   private static BlockState target(ServerLevel level, BlockPos pos, BlockPos.MutableBlockPos n, BlockState state, Liquid liquid) {
      FluidState fluid = state.getFluidState();
      if (state.getBlock() instanceof LiquidBlock) {
         if (!fluid.isSource()) {
            return Blocks.AIR.defaultBlockState();
         }

         BlockState realistic = liquid.realistic().defaultBlockState();
         return feedsFlow(level, pos, n, liquid) ? realistic.setValue(RealisticWaterBlock.INFINITE, true) : realistic;
      }

      if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
         // Stairs, slabs, coral fans...: keep the block, drain the water.
         return state.setValue(BlockStateProperties.WATERLOGGED, false);
      }

      // Kelp, seagrass, bubble columns: water with a plant in it. The plant goes, the water stays.
      return fluid.isSource() ? liquid.realistic().defaultBlockState() : Blocks.AIR.defaultBlockState();
   }

   /**
    * A source that feeds a fall (the top of a waterfall, a lake spilling over a ledge) keeps something running in
    * vanilla: make it a spring. Sources that only feed a flat sideways trickle on a shore stay normal water, or every
    * lake would be simulated forever.
    */
   private static boolean feedsFlow(ServerLevel level, BlockPos pos, BlockPos.MutableBlockPos n, Liquid liquid) {
      for (Direction d : FEEDS) {
         n.setWithOffset(pos, d);
         if (!isFlowing(level.getBlockState(n).getFluidState(), liquid)) {
            continue;
         }

         if (d == Direction.DOWN) {
            return true; // flowing straight down from the source
         }

         n.move(Direction.DOWN);
         if (isFlowing(level.getBlockState(n).getFluidState(), liquid)) {
            return true; // one step sideways, then down
         }
      }

      return false;
   }

   private static boolean isFlowing(FluidState f, Liquid liquid) {
      return !f.isEmpty() && !f.isSource() && liquid.matches(f);
   }
}
