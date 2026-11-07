package me.skaffy.client.water;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.skaffy.client.vk.ComputeProgram;
import me.skaffy.client.vk.Desc;
import me.skaffy.client.vk.GraphicsProgram;
import me.skaffy.client.vk.Program;
import me.skaffy.client.vk.Sampler;
import me.skaffy.client.vk.Vk;
import me.skaffy.client.vk.VkBuf;
import me.skaffy.client.vk.VkTex;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageBlit;

/**
 * Performance fork: above 1440p the fluid G-buffer, thickness and foam passes render at half
 * resolution; the composite still runs at full resolution and samples them upscaled.
 */
public final class WaterRenderer implements AutoCloseable {
   private static final int RGBA32F = 109;
   private static final int D32F = 126;
   private static final int FEATURE_BLIT_SRC = 0x400;
   private static final int FEATURE_BLIT_DST = 0x800;
   /** Screens with more pixels than this render the fluid passes at half resolution. */
   private static final long HALF_RES_ABOVE = 2560L * 1440L;
   private static final Identifier SLIME_TEXTURE = Identifier.withDefaultNamespace("textures/block/slime_block.png");
   private final int thicknessFormat;
   private final boolean canDownscaleDepth;
   private final Passes fullPasses;
   private @Nullable Passes halfPasses;
   private final Map<Integer, GraphicsProgram> composites = new HashMap<>();
   private final ComputeProgram dropletCull;
   private final Sampler nearest = new Sampler(false);
   private final Sampler linear = new Sampler(true);
   private final VkBuf slimeTexture = SandRenderer.loadTexture(SLIME_TEXTURE, -8470674);
   private int width;
   private int height;
   private int scale = 1;
   private int targetWidth;
   private int targetHeight;
   private int colorFormat;
   private VkTex sceneDepth;
   private VkTex fluidZ;
   private VkTex sceneColor;
   private VkTex gbuffer;
   private VkTex materialId;
   private VkTex thickness;
   private VkTex foam;
   private VkTex foamSurface;

   /** The fluid raster programs, compiled for one render scale. */
   private static final class Passes implements AutoCloseable {
      final GraphicsProgram meshFront;
      final GraphicsProgram dropletFront;
      final GraphicsProgram meshThickness;
      final GraphicsProgram dropletThickness;
      final GraphicsProgram whitewater;

      Passes(int thicknessFormat, int scale) {
         int U = 6;
         int S = 7;
         int T = 1;
         Map<String, String> base = new HashMap<>();
         if (scale != 1) {
            base.put("RES_SCALE", scale + ".0");
         }

         Map<String, String> droplets = new HashMap<>(base);
         droplets.put("DROPLETS", "1");
         droplets.put("VIEW_BINDING", "3");
         Map<String, String> ww = new HashMap<>(base);
         ww.put("VIEW_BINDING", "4");
         GraphicsProgram.State front = new GraphicsProgram.State(RGBA32F, D32F, true, true, 4, GraphicsProgram.Blend.NONE, 97);
         GraphicsProgram.State add = new GraphicsProgram.State(thicknessFormat, 0, false, false, 7, GraphicsProgram.Blend.ADD);
         this.meshFront = new GraphicsProgram("mesh.vert", "mesh_front.frag", base, front, U, S, S, S, S, T);
         this.dropletFront = new GraphicsProgram("particle.vert", "droplet_front.frag", droplets, front, U, S, S, S, S);
         this.meshThickness = new GraphicsProgram("mesh.vert", "mesh_thickness.frag", base, add, U, S, S, S, T);
         this.dropletThickness = new GraphicsProgram("particle.vert", "droplet_thickness.frag", droplets, add, U, S, S, S, T);
         this.whitewater = new GraphicsProgram(
            "particle.vert",
            "whitewater.frag",
            ww,
            new GraphicsProgram.State(97, 0, false, false, 7, GraphicsProgram.Blend.ADD, thicknessFormat),
            U,
            S,
            T,
            T,
            S
         );
      }

      @Override
      public void close() {
         for (AutoCloseable c : new AutoCloseable[]{this.meshFront, this.dropletFront, this.meshThickness, this.dropletThickness, this.whitewater}) {
            try {
               c.close();
            } catch (Exception ignored) {
            }
         }
      }
   }

   public WaterRenderer() {
      this.thicknessFormat = formatHas(100, 256) ? 100 : 76;
      this.canDownscaleDepth = formatHas(D32F, FEATURE_BLIT_SRC | FEATURE_BLIT_DST);
      this.fullPasses = new Passes(this.thicknessFormat, 1);
      this.dropletCull = new ComputeProgram("droplet_cull.comp", 6, 7, 7, 7, 7, 7, 7);
   }

   private GraphicsProgram composite(int colorFormat, boolean layer) {
      return this.composites
         .computeIfAbsent(
            colorFormat * 2 + (layer ? 1 : 0),
            key -> new GraphicsProgram(
               "fullscreen.vert",
               "composite.frag",
               Map.of(),
               new GraphicsProgram.State(colorFormat, D32F, true, true, 7, layer ? GraphicsProgram.Blend.PREMULTIPLIED : GraphicsProgram.Blend.NONE),
               6,
               1,
               1,
               1,
               1,
               1,
               1,
               7,
               7,
               1,
               1
            )
         );
   }

   private static boolean formatHas(int format, int features) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkFormatProperties props = VkFormatProperties.calloc(stack);
         VK10.vkGetPhysicalDeviceFormatProperties(Vk.vk().getPhysicalDevice(), format, props);
         return (props.optimalTilingFeatures() & features) == features;
      }
   }

   private int scaleFor(int w, int h) {
      return this.canDownscaleDepth && (long)w * h > HALF_RES_ABOVE ? 2 : 1;
   }

   private Passes passes() {
      if (this.scale == 1) {
         return this.fullPasses;
      }

      if (this.halfPasses == null) {
         this.halfPasses = new Passes(this.thicknessFormat, this.scale);
      }

      return this.halfPasses;
   }

   private void ensureTargets(int w, int h, int colorFormat) {
      int s = this.scaleFor(w, h);
      if (w != this.width || h != this.height || s != this.scale || colorFormat != this.colorFormat || this.sceneDepth == null) {
         this.releaseTargets();
         this.width = w;
         this.height = h;
         this.scale = s;
         this.targetWidth = (w + s - 1) / s;
         this.targetHeight = (h + s - 1) / s;
         this.colorFormat = colorFormat;
         int tw = this.targetWidth;
         int th = this.targetHeight;
         this.sceneDepth = new VkTex(D32F, w, h, 6);
         this.sceneColor = new VkTex(colorFormat, w, h, 6);
         this.fluidZ = new VkTex(D32F, tw, th, 34);
         this.gbuffer = new VkTex(RGBA32F, tw, th, 20);
         this.materialId = new VkTex(97, tw, th, 20);
         this.thickness = new VkTex(this.thicknessFormat, tw, th, 20);
         this.foam = new VkTex(97, tw, th, 20);
         this.foamSurface = new VkTex(this.thicknessFormat, tw, th, 20);
      }
   }

   private void releaseTargets() {
      for (VkTex t : new VkTex[]{this.sceneDepth, this.fluidZ, this.sceneColor, this.gbuffer, this.materialId, this.thickness, this.foam, this.foamSurface}) {
         if (t != null) {
            Vk.destroyLater(t);
         }
      }

      this.sceneDepth = null;
   }

   public void prepare(int w, int h, int colorFormat) {
      this.ensureTargets(w, h, colorFormat);
   }

   /** Nearest-filter depth downscale of a full-resolution region into the half-resolution fluid depth. */
   private static void blitDepth(VkCommandBuffer cb, long src, long dst, int[] full, int[] scaled) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
         VkImageBlit r = region.get(0);
         r.srcSubresource().aspectMask(2).mipLevel(0).baseArrayLayer(0).layerCount(1);
         r.dstSubresource().aspectMask(2).mipLevel(0).baseArrayLayer(0).layerCount(1);
         r.srcOffsets(0).set(full[0], full[1], 0);
         r.srcOffsets(1).set(full[0] + full[2], full[1] + full[3], 1);
         r.dstOffsets(0).set(scaled[0], scaled[1], 0);
         r.dstOffsets(1).set(scaled[0] + scaled[2], scaled[1] + scaled[3], 1);
         VK10.vkCmdBlitImage(cb, src, 1, dst, 1, region, 0);
      }
   }

   public void record(
      VkCommandBuffer cb,
      List<WaterRenderer.Body> bodies,
      Desc view,
      long mainColorImage,
      long mainColorView,
      long mainDepthImage,
      long mainDepthView,
      long lightmap,
      boolean layer,
      @Nullable int[] area
   ) {
      int w = this.width;
      int h = this.height;
      int s = this.scale;
      int tw = this.targetWidth;
      int th = this.targetHeight;
      Passes p = this.passes();
      int[] a = area != null ? area : new int[]{0, 0, w, h};
      int hx = a[0] / s;
      int hy = a[1] / s;
      int[] ha = new int[]{hx, hy, Math.max(1, Math.min(tw, (a[0] + a[2] + s - 1) / s) - hx), Math.max(1, Math.min(th, (a[1] + a[3] + s - 1) / s) - hy)};
      float[] readable = area != null
         ? new float[]{(a[0] + 1.0F) / w, (a[1] + 1.0F) / h, (a[0] + a[2] - 1.0F) / w, (a[1] + a[3] - 1.0F) / h}
         : new float[]{-1.0F, -1.0F, 2.0F, 2.0F};
      List<Runnable> meshes = new ArrayList<>(bodies.size());

      for (WaterRenderer.Body body : bodies) {
         WaterRegion r = body.region();
         if (r.takeFieldStale()) {
            boolean sort = WaterSettings.surfaceGather && WaterSettings.sortParticles;
            meshes.add(
               () -> {
                  r.mesher
                     .build(
                        cb,
                        body.ubo(),
                        r.currentParticles(),
                        r.counters,
                        0 + r.cur,
                        r.args,
                        r.staticSolid,
                        r.cellFlags,
                        r.mesher.fineBox(r.box),
                        WaterSettings.sheetFill,
                        sort ? r.spareParticles() : null,
                        1 - r.cur
                     );
                  if (sort) {
                     r.useSpareParticles();
                  }
               }
            );
         }
      }

      Vk.interleave(cb, meshes);
      Vk.barrier(cb);

      for (WaterRenderer.Body body : bodies) {
         WaterRegion r = body.region();
         this.dropletCull
            .bind(
               cb,
               new Desc[]{
                  body.ubo(),
                  Desc.ssbo(r.currentParticles()),
                  Desc.ssbo(r.mesher.densA),
                  view,
                  Desc.ssbo(r.args),
                  Desc.ssbo(r.counters),
                  Desc.ssbo(r.dropletIds)
               }
            );
         this.dropletCull.push(cb, new int[]{0 + r.cur});
         this.dropletCull.dispatchIndirect(cb, r.args, 0L);
      }

      VkTex.copy(cb, mainDepthImage, this.sceneDepth.image, 2, w, h);
      if (s == 1) {
         VkTex.copy(cb, mainDepthImage, this.fluidZ.image, 2, a[0], a[1], a[2], a[3]);
      } else {
         blitDepth(cb, mainDepthImage, this.fluidZ.image, a, ha);
      }

      VkTex.copy(cb, mainColorImage, this.sceneColor.image, 1, w, h);
      Vk.barrier(cb);
      Desc depthTex = Desc.tex(this.sceneDepth, this.nearest.handle);
      GraphicsProgram.setArea(ha[0], ha[1], ha[2], ha[3]);
      GraphicsProgram.begin(
         cb, tw, th, this.gbuffer.view, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, this.materialId.view, new float[]{-1.0F, 0.0F, 0.0F, 0.0F}, this.fluidZ.view
      );

      for (WaterRenderer.Body body : bodies) {
         WaterRegion r = body.region();
         float radius = WaterSettings.particleRadius / r.res;
         p.meshFront
            .bind(
               cb,
               new Desc[]{
                  body.ubo(),
                  Desc.ssbo(r.mesher.verts),
                  Desc.ssbo(r.mesher.quads),
                  view,
                  Desc.ssbo(r.light()),
                  Desc.tex(WaterWorld.packShadowView != 0L ? WaterWorld.packShadowView : lightmap, this.nearest.handle)
               }
            );
         p.meshFront.drawIndirect(cb, r.mesher.meshCounters, 16L);
         p.dropletFront.bind(cb, new Desc[]{body.ubo(), Desc.ssbo(r.currentParticles()), Desc.ssbo(r.dropletIds), view, Desc.ssbo(r.light())});
         p.dropletFront.push(cb, new int[]{Program.f(radius), Program.f(0.0F), Program.f(0.15F)});
         p.dropletFront.drawIndirect(cb, r.args, 96L);
      }

      GraphicsProgram.end(cb);
      GraphicsProgram.begin(cb, tw, th, this.thickness.view, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, 0L);

      for (WaterRenderer.Body body : bodies) {
         WaterRegion r = body.region();
         float radius = WaterSettings.particleRadius / r.res;
         p.meshThickness.bind(cb, new Desc[]{body.ubo(), Desc.ssbo(r.mesher.verts), Desc.ssbo(r.mesher.quads), view, depthTex});
         p.meshThickness.drawIndirect(cb, r.mesher.meshCounters, 16L);
         p.dropletThickness.bind(cb, new Desc[]{body.ubo(), Desc.ssbo(r.currentParticles()), Desc.ssbo(r.dropletIds), view, depthTex});
         p.dropletThickness.push(cb, new int[]{Program.f(radius), Program.f(0.0F), Program.f(0.15F)});
         p.dropletThickness.drawIndirect(cb, r.args, 96L);
      }

      GraphicsProgram.end(cb);
      Vk.barrier(cb);
      GraphicsProgram.begin(cb, tw, th, this.foam.view, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, this.foamSurface.view, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, 0L);
      if (WaterSettings.whitewater) {
         for (WaterRenderer.Body body : bodies) {
            WaterRegion r = body.region();
            if (r.material.usesWhitewater()) {
               p.whitewater.bind(cb, new Desc[]{body.ubo(), Desc.ssbo(r.currentWhitewater()), depthTex, Desc.tex(this.gbuffer, this.nearest.handle), view});
               p.whitewater
                  .push(
                     cb,
                     new int[]{
                        Program.f(WaterSettings.wwRadius),
                        Program.f(0.5F),
                        Program.f(0.3F),
                        Program.f(WaterSettings.wwMaxPixels),
                        Program.f(WaterSettings.foamSize)
                     }
                  );
               p.whitewater.drawIndirect(cb, r.args, 64L);
            }
         }
      }

      GraphicsProgram.end(cb);
      Vk.barrier(cb);
      GraphicsProgram.setArea(a[0], a[1], a[2], a[3]);
      GraphicsProgram composite = this.composite(this.colorFormat, layer);
      GraphicsProgram.begin(cb, w, h, mainColorView, null, mainDepthView);
      Desc matTex = Desc.tex(this.materialId, this.nearest.handle);

      for (Material material : new Material[]{Material.WATER, Material.LAVA, Material.SLIME}) {
         Desc ubo = null;

         for (WaterRenderer.Body body : bodies) {
            if (body.region().material == material) {
               ubo = body.ubo();
               break;
            }
         }

         if (ubo != null) {
            composite.bind(
               cb,
               new Desc[]{
                  ubo,
                  Desc.tex(this.gbuffer, this.nearest.handle),
                  Desc.tex(this.thickness, this.linear.handle),
                  Desc.tex(this.sceneColor, this.linear.handle),
                  depthTex,
                  Desc.tex(this.foam, this.linear.handle),
                  matTex,
                  view,
                  Desc.ssbo(this.slimeTexture),
                  Desc.tex(lightmap, this.linear.handle),
                  Desc.tex(this.foamSurface, this.linear.handle)
               }
            );
            composite.push(
               cb, new int[]{Program.f(readable[0]), Program.f(readable[1]), Program.f(readable[2]), Program.f(readable[3]), Program.f(material.shaderId())}
            );
            composite.draw(cb, 3, 1);
         }
      }

      GraphicsProgram.end(cb);
      GraphicsProgram.clearArea();
   }

   @Override
   public void close() {
      this.releaseTargets();
      this.composites.values().forEach(Program::close);
      this.fullPasses.close();
      if (this.halfPasses != null) {
         this.halfPasses.close();
      }

      for (AutoCloseable c : new AutoCloseable[]{this.dropletCull, this.nearest, this.linear, this.slimeTexture}) {
         try {
            c.close();
         } catch (Exception ignored) {
         }
      }
   }

   public record Body(WaterRegion region, Desc ubo) {
   }
}
