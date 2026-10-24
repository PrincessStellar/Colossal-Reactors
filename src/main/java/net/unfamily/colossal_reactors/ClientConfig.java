package net.unfamily.colossal_reactors;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only rendering settings (turbine rotor animation, reactor rod fill visuals).
 */
public final class ClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Reactor multiblock visuals (rod FILL blockstate updates on server in SP/integrated)")
                .push("reactor");
    }

    public static final ModConfigSpec.BooleanValue REACTOR_DISABLE_ROD_FILL_VISUAL_UPDATE = BUILDER
            .comment("When true, reactor rod FILL blockstates are not updated each tick (less lag, no fuel-level animation).",
                    "Read from client config in singleplayer/integrated; dedicated servers always update rod visuals. Default: false")
            .define("disable_rod_fill_visual_update", false);

    public static final ModConfigSpec.ConfigValue<String> REACTOR_ROD_INSIDE_FALLBACK_COLOR = BUILDER
            .comment("Fallback hex color for reactor rod inside fill when fuel JSON has no color. Default: #2f833b")
            .define("rod_inside_fallback_color", "#2f833b");

    static {
        BUILDER.pop();
    }

    /** Parsed fallback RGB (0xRRGGBB) for untinted / unknown fuels. */
    public static int getRodInsideFallbackColorRgb() {
        return parseHexRgb(REACTOR_ROD_INSIDE_FALLBACK_COLOR.get(), 0x2f833b);
    }

    public static int parseHexRgb(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static {
        BUILDER.comment("Turbine rotor animation (client rendering only; does not affect RF/steam simulation)")
                .push("turbine");
    }

    public static final ModConfigSpec.BooleanValue TURBINE_ROTOR_ROTATION_ENABLED = BUILDER
            .comment("When false, turbine rods and blades use static block models only (no extra BER or model wrapping).",
                    "Useful on low-end PCs or very large turbines. Default: true")
            .define("enable_rotor_rotation", true);

    public static final ModConfigSpec.DoubleValue TURBINE_ROTOR_MAX_DEG_PER_TICK = BUILDER
            .comment("Maximum rotation speed in degrees per tick at full load (RF or steam vs estimated max). Default: 30")
            .defineInRange("rotor_max_deg_per_tick", 30.0, 0.1, 270.0);

    public static final ModConfigSpec.DoubleValue TURBINE_ROTOR_MIN_LOAD_TO_SPIN = BUILDER
            .comment("Minimum load factor (0-1) before the rotor starts spinning visually. Default: 0.01")
            .defineInRange("rotor_min_load_to_spin", 0.01, 0.0, 1.0);

    public static final ModConfigSpec.IntValue TURBINE_ROTOR_STRUCTURE_CHECK_INTERVAL_TICKS = BUILDER
            .comment("Ticks between client-side rod layout checks for nearby turbines. Default: 20")
            .defineInRange("rotor_structure_check_interval_ticks", 20, 5, 200);

    public static final ModConfigSpec.BooleanValue TURBINE_ROTOR_RENDER_OFFSCREEN = BUILDER
            .comment("When true, rotor BER may run outside the camera frustum (heavier). Default: false")
            .define("rotor_render_offscreen", false);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Hard cap for turbine rotor render/simulation range (blocks). */
    public static final int TURBINE_ROTOR_RENDER_DISTANCE_CAP_BLOCKS = 64;

    /**
     * Effective rotor distance: player render distance in blocks, capped at {@link #TURBINE_ROTOR_RENDER_DISTANCE_CAP_BLOCKS}.
     */
    public static int getTurbineRotorRenderDistanceBlocks() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.options == null) {
            return TURBINE_ROTOR_RENDER_DISTANCE_CAP_BLOCKS;
        }
        int renderChunks = mc.options.getEffectiveRenderDistance();
        return Math.min(renderChunks * 16, TURBINE_ROTOR_RENDER_DISTANCE_CAP_BLOCKS);
    }

    /** Whether the server should tick reactor rod FILL blockstate updates. */
    public static boolean shouldUpdateReactorRodFillVisuals() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            return true;
        }
        return !REACTOR_DISABLE_ROD_FILL_VISUAL_UPDATE.get();
    }

    private ClientConfig() {}
}
