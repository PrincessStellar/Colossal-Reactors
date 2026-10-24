package net.unfamily.colossal_reactors.builder.web;

import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.heatsink.HeatSinkDefinition;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.turbine.ElecCoilDefinition;
import net.unfamily.colossal_reactors.turbine.ElecCoilLoader;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses config CSV rows {@code datapack_selector;website_id} for ModDecoded website material maps.
 */
public final class WebsiteSelectorMaps {

    public record Entry(String selector, String websiteId) {}

    private WebsiteSelectorMaps() {}

    public static List<Entry> coilEntries() {
        return parse(Config.WEBSITE_COIL_SELECTOR_MAP.get());
    }

    public static List<Entry> heatsinkEntries() {
        return parse(Config.WEBSITE_HEATSINK_SELECTOR_MAP.get());
    }

    public static List<Entry> parse(@Nullable List<? extends String> lines) {
        List<Entry> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (Object raw : lines) {
            if (!(raw instanceof String line) || line.isBlank()) {
                continue;
            }
            Entry e = parseLine(line.trim());
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    @Nullable
    public static Entry parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int sep = line.indexOf(';');
        if (sep <= 0 || sep >= line.length() - 1) {
            return null;
        }
        String selector = line.substring(0, sep).trim();
        String websiteId = line.substring(sep + 1).trim();
        if (selector.isEmpty() || websiteId.isEmpty()) {
            return null;
        }
        return new Entry(selector, websiteId);
    }

    /** Website coil id → datapack selector from config, if listed. */
    public static Optional<String> coilSelectorForWebsiteId(String websiteId) {
        return selectorForWebsiteId(coilEntries(), websiteId);
    }

    /** Website heatsink id → datapack selector/hint from config, if listed. */
    public static Optional<String> heatsinkSelectorForWebsiteId(String websiteId) {
        return selectorForWebsiteId(heatsinkEntries(), websiteId);
    }

    private static Optional<String> selectorForWebsiteId(List<Entry> entries, String websiteId) {
        if (websiteId == null || websiteId.isEmpty()) {
            return Optional.empty();
        }
        String want = websiteId.toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (e.websiteId().equalsIgnoreCase(want)) {
                return Optional.of(e.selector());
            }
        }
        return Optional.empty();
    }

    /**
     * Website id for a datapack selector (exact match preferred, then path-tail / contains).
     */
    public static Optional<String> websiteIdForCoilSelector(String selector) {
        return websiteIdForSelector(coilEntries(), selector);
    }

    public static Optional<String> websiteIdForHeatsinkSelector(String selector) {
        return websiteIdForSelector(heatsinkEntries(), selector);
    }

    private static Optional<String> websiteIdForSelector(List<Entry> entries, String selector) {
        if (selector == null || selector.isEmpty()) {
            return Optional.empty();
        }
        String s = selector.toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (e.selector().equalsIgnoreCase(s)) {
                return Optional.of(e.websiteId());
            }
        }
        String path = pathTail(s);
        for (Entry e : entries) {
            String ep = pathTail(e.selector().toLowerCase(Locale.ROOT));
            if (ep.equals(path) || s.contains(ep) || e.selector().toLowerCase(Locale.ROOT).contains(path)) {
                return Optional.of(e.websiteId());
            }
        }
        return Optional.empty();
    }

    /** First non-air elec-coil builder option index (0 if none). */
    public static int firstNonAirCoilOptionIndex() {
        List<ElecCoilDefinition> defs = ElecCoilLoader.getAllDefinitions();
        for (int i = 0; i < defs.size(); i++) {
            if (!ElecCoilLoader.isAirOnlyDefinition(defs.get(i))) {
                return i;
            }
        }
        return 0;
    }

    /** First non-air heat-sink builder option index (0 = air). */
    public static int firstNonAirHeatSinkOptionIndex() {
        int count = HeatSinkLoader.getHeatSinkOptionCount();
        for (int opt = 1; opt < count; opt++) {
            HeatSinkDefinition def = HeatSinkLoader.definitionForOptionIndex(opt);
            if (def != null) {
                return opt;
            }
        }
        return 0;
    }

    /** Website token for the first non-air coil mapping, or {@code air}. */
    public static String firstNonAirCoilWebsiteId() {
        for (Entry e : coilEntries()) {
            if (!"air".equalsIgnoreCase(e.websiteId()) && !"minecraft:air".equalsIgnoreCase(e.selector())) {
                return e.websiteId();
            }
        }
        int idx = firstNonAirCoilOptionIndex();
        List<ElecCoilDefinition> defs = ElecCoilLoader.getAllDefinitions();
        if (idx >= 0 && idx < defs.size() && defs.get(idx) != null && !defs.get(idx).validBlocks().isEmpty()) {
            return websiteIdForCoilSelector(defs.get(idx).validBlocks().get(0)).orElse("air");
        }
        return "air";
    }

    private static String pathTail(String selector) {
        String s = selector;
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.contains(":")) {
            s = s.substring(s.indexOf(':') + 1);
        }
        if (s.contains("/")) {
            s = s.substring(s.lastIndexOf('/') + 1);
        }
        return s;
    }
}
