import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Bytecode patcher for Realistic Water 1.3 (performance fork).
 * Usage: java Patcher <classesDir> <lowend|balanced|tsunami>
 *  - rewrites default values in WaterSettings.<clinit> to a low-end preset
 *  - lowers per-frame CPU light refresh budget in WaterRegion.updateLight
 */
public class Patcher {
    static final String SETTINGS = "me/skaffy/client/water/WaterSettings";
    static final String REGION = "me/skaffy/client/water/WaterRegion";
    static final String MESHER = "me/skaffy/client/water/SurfaceMesher";

    // field -> new default value
    static final Map<String, Object> PRESET = new LinkedHashMap<>();

    // Non-water cuts shared by every preset
    static void common() {
        PRESET.put("lavaResolution", 2);        // 6 -> 2
        PRESET.put("slimeResolution", 2);       // 4 -> 2
        PRESET.put("slimeViscosityIterations", 2); // 4 -> 2
        PRESET.put("sandResolution", 4);        // 12 -> 4 (~27x cheaper; 6 hit 50 ms/frame in a 5x5x5 test)
        PRESET.put("sandMaxStepsPerFrame", 4);  // 16 -> 4
        PRESET.put("sandGrainsPerParticle", 16); // 32 -> 16 grains drawn per particle (draw-only cost, 8 looked sparse)
        PRESET.put("sandGrainSize", 0.013f);    // 0.01 -> 0.013 slightly bigger grains fill the pile at res 4
        PRESET.put("sandSurfaceSmoothing", 0.75f); // 0.6 -> 0.75 softer pile surface at low res
        PRESET.put("leafResolution", 6);        // 8 -> 6
        PRESET.put("leavesPerBlock", 100);      // 200 -> 100
        PRESET.put("leafMaxStepsPerFrame", 4);  // 8 -> 4 (also steam)
        PRESET.put("steamParticleSize", 0.14f); // 0.08 -> 0.14 (~5x fewer steam points)
        PRESET.put("boilSteam", 0.6f);          // 1.0 -> 0.6
        PRESET.put("realisticRain", false);     // GPU rain sim off -> vanilla rain
        PRESET.put("packShadows", false);       // shader-pack shadow readbacks
        PRESET.put("packShadowPerLeaf", false);
        PRESET.put("packShadowDetail", 2);      // 4 -> 2
        PRESET.put("vitrailInsidePack", false); // avoids shading fluids twice per pixel
    }

    // Lowest: water at 1 cell per block, no whitewater
    static void lowend() {
        PRESET.put("resolution", 1);            // 6 -> 1 cells/block (~216x fewer cells & particles)
        PRESET.put("boxMargin", 2);             // 6 -> 2
        PRESET.put("cfl", 3.0f);                // 2.0 -> 3.0
        PRESET.put("maxDt", 0.04f);             // 0.025 -> 0.04
        PRESET.put("maxStepsPerFrame", 2);      // 4 -> 2 stops low-FPS substep death spiral
        PRESET.put("cgIterations", 3);          // 5 -> 3
        PRESET.put("whitewater", false);        // foam/spray/bubbles off
        PRESET.put("wallFill", false);          // extra surface field pass
        PRESET.put("surfaceSubdivision", 3);    // res 1 * 3 = 3 mesh voxels/block (default 12)
        PRESET.put("surfaceInteriorCount", 3);  // 4 -> 3 skips more interior work
        PRESET.put("strandedTime", 0.8f);       // 1.5 -> 0.8 stray drops die sooner
        PRESET.put("fluidRestSpeed", 0.3f);     // 0.15 -> 0.3 calm water sleeps sooner
        PRESET.put("fluidRestTime", 0.75f);     // 1.5 -> 0.75
        common();
    }

    // Tsunami: clean "normal" water, no particles, cheap enough for huge moving bodies
    static void tsunami() {
        PRESET.put("resolution", 2);            // 6 -> 2 cells/block: 27x fewer cells & particles
        PRESET.put("boxMargin", 3);             // 6 -> 3
        PRESET.put("cfl", 3.0f);                // 2.0 -> 3.0 fewer substeps for fast waves
        PRESET.put("maxDt", 0.04f);             // 0.025 -> 0.04
        PRESET.put("maxStepsPerFrame", 2);      // 4 -> 2 stops low-FPS substep death spiral
        PRESET.put("cgIterations", 3);          // 5 -> 3
        PRESET.put("whitewater", false);        // no foam/spray/bubble particles
        PRESET.put("dropletThreshold", 0.0f);   // 0.35 -> 0: no loose droplet sprites, all water is mesh
        PRESET.put("strandedTime", 1.0f);       // 1.5 -> 1.0 stray drops dry a bit sooner (0.5 dried small puddles)
        PRESET.put("surfaceSubdivision", 2);    // res 2 * 2 = 4 mesh voxels/block (default 12): 27x less
        PRESET.put("surfaceInteriorCount", 3);  // 4 -> 3 skips more interior work
        PRESET.put("fluidRestSpeed", 0.25f);    // 0.15 -> 0.25 calm water sleeps sooner
        PRESET.put("fluidRestTime", 1.0f);      // 1.5 -> 1.0
        PRESET.put("detailStrength", 0.5f);     // 0.4 -> 0.5 shader ripples hide low res (free)
        common();
    }

    // Balanced: water settings from a YouTube tuning video (res 6, whitewater on)
    static void balanced() {
        PRESET.put("cfl", 2.5f);
        PRESET.put("maxDt", 0.03f);
        PRESET.put("maxStepsPerFrame", 3);
        PRESET.put("filmFriction", 70.0f);
        PRESET.put("filmDepth", 0.03f);
        PRESET.put("strandedTime", 0.8f);
        PRESET.put("fluidRestTime", 1.2f);
        PRESET.put("fluidRestWhitewater", 103);
        PRESET.put("sourceFill", 1.0f);
        PRESET.put("cgIterations", 3);
        PRESET.put("mgCoarseSweeps", 15);
        PRESET.put("wwWaveCrestRate", 35.0f);
        PRESET.put("wwFoamLifeMax", 3.5f);
        PRESET.put("wwBubbleBuoyancy", 3.2f);
        PRESET.put("wwMaxPerParticle", 3);
        PRESET.put("wwCellLimit", 9);
        PRESET.put("wwRadius", 0.015f);
        PRESET.put("foamSize", 3.1f);
        PRESET.put("surfaceSubdivision", 1);    // 2 -> 1: ~8x less surface mesh work
        PRESET.put("surfaceKernel", 2.3f);
        PRESET.put("surfaceRadius", 1.0f);
        PRESET.put("surfaceInteriorCount", 3);
        PRESET.put("particleRadius", 0.43f);
        PRESET.put("refraction", 0.25f);
        // video used maxSpeed 100; kept default 40 because faster water costs more substeps
        common();
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        switch (args[1]) {
            case "lowend" -> lowend();
            case "balanced" -> balanced();
            case "tsunami" -> tsunami();
            default -> throw new IllegalArgumentException("preset: lowend | balanced | tsunami");
        }
        patch(root.resolve(SETTINGS + ".class"), Patcher::patchSettings);
        patch(root.resolve(REGION + ".class"), Patcher::patchRegion);
        patch(root.resolve(MESHER + ".class"), Patcher::patchMesher);
        System.out.println("OK");
    }

    interface P { void apply(ClassNode cn); }

    static void patch(Path file, P p) throws Exception {
        ClassReader cr = new ClassReader(Files.readAllBytes(file));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        p.apply(cn);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(file, cw.toByteArray());
    }

    static void patchSettings(ClassNode cn) {
        MethodNode clinit = cn.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        Set<String> done = new HashSet<>();
        for (AbstractInsnNode in : clinit.instructions.toArray()) {
            if (in.getOpcode() != Opcodes.PUTSTATIC) continue;
            FieldInsnNode f = (FieldInsnNode) in;
            if (!f.owner.equals(SETTINGS) || !PRESET.containsKey(f.name)) continue;
            AbstractInsnNode prev = f.getPrevious();
            if (!isConst(prev)) throw new IllegalStateException("non-constant init for " + f.name + ": " + prev.getOpcode());
            Object v = PRESET.get(f.name);
            AbstractInsnNode repl;
            switch (f.desc) {
                case "I" -> repl = intConst((Integer) v);
                case "Z" -> repl = new InsnNode((Boolean) v ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                case "F" -> repl = floatConst((Float) v);
                default -> throw new IllegalStateException("bad desc " + f.desc + " for " + f.name);
            }
            clinit.instructions.set(prev, repl);
            System.out.println("  " + f.name + " = " + v);
            done.add(f.name);
        }
        Set<String> missing = new HashSet<>(PRESET.keySet());
        missing.removeAll(done);
        if (!missing.isEmpty()) throw new IllegalStateException("not patched: " + missing);
    }

    static void patchRegion(ClassNode cn) {
        MethodNode m = cn.methods.stream().filter(x -> x.name.equals("updateLight")).findFirst().orElseThrow();
        int n = 0;
        for (AbstractInsnNode in : m.instructions.toArray()) {
            if (in instanceof IntInsnNode i && i.getOpcode() == Opcodes.SIPUSH && i.operand == 4096) {
                i.operand = 1024; // light cells refreshed per region per frame
                n++;
            }
        }
        if (n != 1) throw new IllegalStateException("expected 1 light budget const, found " + n);
        System.out.println("  WaterRegion.updateLight budget 4096 -> 1024");
    }

    // SurfaceMesher(nx, ny, nz, subdivision): clamp subdivision so its GPU buffers fit (see perf/MemoryGuard)
    static void patchMesher(ClassNode cn) {
        MethodNode m = cn.methods.stream().filter(x -> x.name.equals("<init>") && x.desc.equals("(IIII)V")).findFirst().orElseThrow();
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(Opcodes.ILOAD, 1));
        pre.add(new VarInsnNode(Opcodes.ILOAD, 2));
        pre.add(new VarInsnNode(Opcodes.ILOAD, 3));
        pre.add(new VarInsnNode(Opcodes.ILOAD, 4));
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/skaffy/perf/MemoryGuard", "subdivision", "(IIII)I", false));
        pre.add(new VarInsnNode(Opcodes.ISTORE, 4));
        m.instructions.insert(pre);
        System.out.println("  SurfaceMesher subdivision guarded by MemoryGuard");
    }

    static boolean isConst(AbstractInsnNode in) {
        int op = in.getOpcode();
        return (op >= Opcodes.ICONST_M1 && op <= Opcodes.DCONST_1) || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH || op == Opcodes.LDC;
    }

    static AbstractInsnNode intConst(int v) {
        if (v >= -1 && v <= 5) return new InsnNode(Opcodes.ICONST_0 + v);
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, v);
        if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, v);
        return new LdcInsnNode(v);
    }

    static AbstractInsnNode floatConst(float v) {
        if (v == 0f) return new InsnNode(Opcodes.FCONST_0);
        if (v == 1f) return new InsnNode(Opcodes.FCONST_1);
        if (v == 2f) return new InsnNode(Opcodes.FCONST_2);
        return new LdcInsnNode(v);
    }
}
