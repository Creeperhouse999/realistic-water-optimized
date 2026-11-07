import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

/** Compiles the mod's shaders the same way Shaders.module does. Usage: ShaderCheck <shaderDir> */
public class ShaderCheck {
    static Path dir;

    static String preprocess(String src, Set<String> seen) throws Exception {
        StringBuilder out = new StringBuilder();
        for (String line : src.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#include")) {
                String f = t.substring(t.indexOf('"') + 1, t.lastIndexOf('"'));
                if (seen.add(f)) out.append(preprocess(Files.readString(dir.resolve(f)), seen)).append('\n');
            } else out.append(line).append('\n');
        }
        return out.toString();
    }

    static String compile(String name, Map<String, String> defines) throws Exception {
        String source = Files.readString(dir.resolve(name));
        int nl = source.indexOf('\n');
        StringBuilder head = new StringBuilder(source.substring(0, nl + 1));
        defines.forEach((k, v) -> head.append("#define ").append(k).append(' ').append(v).append('\n'));
        String full = preprocess(head + source.substring(nl + 1), new HashSet<>());
        int kind = name.endsWith(".vert") ? 0 : name.endsWith(".frag") ? 1 : 2;
        long c = Shaderc.shaderc_compiler_initialize();
        long o = Shaderc.shaderc_compile_options_initialize();
        ByteBuffer src = MemoryUtil.memUTF8(full, false);
        try (MemoryStack s = MemoryStack.stackPush()) {
            Shaderc.shaderc_compile_options_set_target_env(o, 0, 4202496);
            Shaderc.shaderc_compile_options_set_optimization_level(o, 2);
            long r = Shaderc.shaderc_compile_into_spv(c, src, kind, s.UTF8(name), s.UTF8("main"), o);
            try {
                return Shaderc.shaderc_result_get_compilation_status(r) == 0 ? null : Shaderc.shaderc_result_get_error_message(r);
            } finally { Shaderc.shaderc_result_release(r); }
        } finally {
            MemoryUtil.memFree(src); Shaderc.shaderc_compile_options_release(o); Shaderc.shaderc_compiler_release(c);
        }
    }

    public static void main(String[] a) throws Exception {
        dir = Paths.get(a[0]);
        int fail = 0;
        List<Object[]> jobs = new ArrayList<>();
        try (var st = Files.list(dir)) {
            for (Path p : st.sorted().toList()) {
                String n = p.getFileName().toString();
                if (n.endsWith(".vert") || n.endsWith(".frag") || n.endsWith(".comp")) jobs.add(new Object[]{n, Map.of()});
            }
        }
        // exact variants the renderer builds
        for (String scale : new String[]{null, "2.0"}) {
            Map<String, String> base = new HashMap<>(); if (scale != null) base.put("RES_SCALE", scale);
            Map<String, String> dr = new HashMap<>(base); dr.put("DROPLETS", "1"); dr.put("VIEW_BINDING", "3");
            Map<String, String> ww = new HashMap<>(base); ww.put("VIEW_BINDING", "4");
            jobs.add(new Object[]{"mesh.vert", base}); jobs.add(new Object[]{"mesh_front.frag", base});
            jobs.add(new Object[]{"mesh_thickness.frag", base});
            jobs.add(new Object[]{"particle.vert", dr}); jobs.add(new Object[]{"droplet_front.frag", dr});
            jobs.add(new Object[]{"droplet_thickness.frag", dr});
            jobs.add(new Object[]{"particle.vert", ww}); jobs.add(new Object[]{"whitewater.frag", ww});
        }
        for (Object[] j : jobs) {
            @SuppressWarnings("unchecked") String err = compile((String) j[0], (Map<String, String>) j[1]);
            if (err != null) { fail++; System.out.println("FAIL " + j[0] + " " + j[1] + "\n" + err.lines().limit(4).reduce("", (x, y) -> x + "   " + y + "\n")); }
        }
        System.out.println(jobs.size() + " compiles, " + fail + " failed");
    }
}
