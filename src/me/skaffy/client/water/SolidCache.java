package me.skaffy.client.water;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import me.skaffy.block.RealisticSandBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

final class SolidCache {
   static final byte EMPTY = 0;
   static final byte FULL = 1;
   static final byte PARTIAL = 2;
   /** Performance fork: vanilla water. Fluid sims see it as open space but delete particles that enter it. */
   static final byte SINK = 3;
   /** Value written into the GPU solid grid for sink cells (solid = 1, empty = 0). */
   static final int SINK_VALUE = 2;
   final int res;
   final boolean ignoreSand;
   private final int bitsPerBlock;
   private final Long2ObjectOpenHashMap<SolidCache.Section> sections = new Long2ObjectOpenHashMap();
   private int minSectionY = Integer.MAX_VALUE;
   private int maxSectionY = Integer.MIN_VALUE;

   SolidCache(int res, boolean ignoreSand) {
      this.res = res;
      this.ignoreSand = ignoreSand;
      this.bitsPerBlock = res * res * res;
   }

   private static int local(int x, int y, int z) {
      return x & 15 | (y & 15) << 4 | (z & 15) << 8;
   }

   void clear() {
      this.sections.clear();
      this.minSectionY = Integer.MAX_VALUE;
      this.maxSectionY = Integer.MIN_VALUE;
   }

   void invalidateChunk(int chunkX, int chunkZ) {
      for (int sy = this.minSectionY; sy <= this.maxSectionY; sy++) {
         this.sections.remove(SectionPos.asLong(chunkX, sy, chunkZ));
      }
   }

   boolean update(ClientLevel level, BlockPos pos) {
      SolidCache.Section s = (SolidCache.Section)this.sections.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
      if (s == null) {
         return true;
      } else {
         int i = local(pos.getX(), pos.getY(), pos.getZ());
         byte oldKind = s.kind[i];
         long[] oldBits = s.partial[i];
         this.voxelizeBlock(level, pos, level.getBlockState(pos), s, i);
         return oldKind != s.kind[i] || s.kind[i] == 2 && !Arrays.equals(oldBits, s.partial[i]);
      }
   }

   @Nullable
   private Section section(ClientLevel level, int sx, int sy, int sz) {
      long key = SectionPos.asLong(sx, sy, sz);
      SolidCache.Section s = (SolidCache.Section)this.sections.get(key);
      if (s != null) {
         return s;
      } else {
         int sectionIndex = level.getSectionIndexFromSectionY(sy);
         if (sectionIndex >= level.getSectionsCount()) {
            s = new SolidCache.Section();
            this.put(key, sy, s);
            return s;
         } else if (sectionIndex >= 0 && level.hasChunk(sx, sz)) {
            LevelChunk chunk = level.getChunk(sx, sz);
            LevelChunkSection chunkSection = chunk.getSection(sectionIndex);
            s = new SolidCache.Section();
            if (!chunkSection.hasOnlyAir()) {
               MutableBlockPos pos = new MutableBlockPos();

               for (int i = 0; i < 4096; i++) {
                  int x = i & 15;
                  int y = i >> 4 & 15;
                  int z = i >> 8;
                  BlockState state = chunkSection.getBlockState(x, y, z);
                  if (!state.isAir()) {
                     pos.set((sx << 4) + x, (sy << 4) + y, (sz << 4) + z);
                     this.voxelizeBlock(level, pos, state, s, i);
                  }
               }
            }

            this.put(key, sy, s);
            return s;
         } else {
            return null;
         }
      }
   }

   private void put(long key, int sy, SolidCache.Section section) {
      this.sections.put(key, section);
      this.minSectionY = Math.min(this.minSectionY, sy);
      this.maxSectionY = Math.max(this.maxSectionY, sy);
   }

   private void voxelizeBlock(ClientLevel level, BlockPos pos, BlockState state, SolidCache.Section s, int i) {
      s.partial[i] = null;
      boolean skip = state.isAir() || this.ignoreSand && state.getBlock() instanceof RealisticSandBlock;
      VoxelShape shape = skip ? null : state.getCollisionShape(level, pos);
      if (!this.ignoreSand && (shape == null || shape.isEmpty()) && isVanillaWater(state.getFluidState())) {
         s.kind[i] = 3;
      } else if (shape != null && !shape.isEmpty()) {
         if (Block.isShapeFullBlock(shape)) {
            s.kind[i] = 1;
         } else {
            int r = this.res;
            List<AABB> boxes = shape.toAabbs().stream().map(boxx -> atLeastOneCell(boxx, r)).toList();
            long[] bits = new long[(this.bitsPerBlock + 63) / 64];
            boolean any = false;

            for (int k = 0; k < r; k++) {
               for (int j = 0; j < r; j++) {
                  for (int c = 0; c < r; c++) {
                     double cx = (c + 0.5) / r;
                     double cy = (j + 0.5) / r;
                     double cz = (k + 0.5) / r;

                     for (AABB box : boxes) {
                        if (cx >= box.minX - 1.0E-4
                           && cx <= box.maxX + 1.0E-4
                           && cy >= box.minY - 1.0E-4
                           && cy <= box.maxY + 1.0E-4
                           && cz >= box.minZ - 1.0E-4
                           && cz <= box.maxZ + 1.0E-4) {
                           int bit = c + r * (j + r * k);
                           bits[bit >> 6] = bits[bit >> 6] | 1L << (bit & 63);
                           any = true;
                           break;
                        }
                     }
                  }
               }
            }

            s.kind[i] = (byte)(any ? 2 : 0);
            s.partial[i] = any ? bits : null;
         }
      } else {
         s.kind[i] = 0;
      }
   }

   private static boolean isVanillaWater(FluidState fluid) {
      return !fluid.isEmpty() && (fluid.getType() == Fluids.WATER || fluid.getType() == Fluids.FLOWING_WATER);
   }

   private static AABB atLeastOneCell(AABB box, int r) {
      double[] lo = new double[]{box.minX, box.minY, box.minZ};
      double[] hi = new double[]{box.maxX, box.maxY, box.maxZ};

      for (int a = 0; a < 3; a++) {
         if (hi[a] - lo[a] < 1.0 / r) {
            int cell = Mth.clamp((int)Math.floor((lo[a] + hi[a]) * 0.5 * r), 0, r - 1);
            lo[a] = (double)cell / r;
            hi[a] = (double)(cell + 1) / r;
         }
      }

      return new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
   }

   void writeBlock(ClientLevel level, int bx, int by, int bz, int[] out, int cx, int cy, int cz, int nx, int ny) {
      SolidCache.Section s = this.section(level, bx >> 4, by >> 4, bz >> 4);
      long[] bits = null;
      byte kind;
      if (s == null) {
         kind = 1;
      } else {
         int i = local(bx, by, bz);
         kind = s.kind[i];
         bits = s.partial[i];
      }

      int r = this.res;

      for (int k = 0; k < r; k++) {
         for (int j = 0; j < r; j++) {
            int row = cx + nx * (cy + j + ny * (cz + k));

            for (int c = 0; c < r; c++) {
               int v;
               if (kind == 0) {
                  v = 0;
               } else if (kind == 1) {
                  v = 1;
               } else if (kind == 3) {
                  v = 2;
               } else {
                  int bit = c + r * (j + r * k);
                  v = (int)(bits[bit >> 6] >>> (bit & 63) & 1L);
               }

               out[row + c] = v;
            }
         }
      }
   }

   private static final class Section {
      final byte[] kind = new byte[4096];
      final long[][] partial = new long[4096][];
   }
}
