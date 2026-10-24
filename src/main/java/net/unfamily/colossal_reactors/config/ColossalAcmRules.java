package net.unfamily.colossal_reactors.config;

import net.neoforged.fml.config.ModConfig;
import net.unfamily.anotherconfigmanager.config.ColorRuleRegistry;
import net.unfamily.anotherconfigmanager.config.CsvRuleRegistry;
import net.unfamily.colossal_reactors.ColossalReactors;

/**
 * Registers Another Config Manager CSV table schemas and color bindings for this mod's config.
 */
public final class ColossalAcmRules {
    private static final String MOD = ColossalReactors.MODID;

    private ColossalAcmRules() {}

    public static void register() {
        registerCsv(
                "website.000_coil_selector_map",
                "website/000_coil_selector_map");
        registerCsv(
                "website.001_heatsink_selector_map",
                "website/001_heatsink_selector_map");
        // Client hex string → ACM color picker (#RRGGBB).
        ColorRuleRegistry.register(
                MOD,
                ModConfig.Type.CLIENT,
                "reactor.rod_inside_fallback_color",
                "hex;rgb;prefix=#;case=lower");
    }

    private static void registerCsv(String configPathDots, String rulePathSuffix) {
        String path = MOD + ":" + rulePathSuffix;
        CsvRuleRegistry.register(
                MOD,
                configPathDots,
                ";" + path
                        + ";0=" + MOD + ".config.csv.datapack_selector"
                        + ";1=" + MOD + ".config.csv.website_id"
        );
    }
}
