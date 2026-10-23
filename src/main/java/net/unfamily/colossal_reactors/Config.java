package net.unfamily.colossal_reactors;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ========== dev ==========
    static {
        BUILDER.comment("Development and advanced configuration").push("dev");
    }

    public static final ModConfigSpec.ConfigValue<Boolean> REACTOR_VALIDATION_DEBUG = BUILDER
            .comment("Dump full reactor validation to log (info level). Default: false")
            .define("000_reactor_validation_debug", false);

    public static final ModConfigSpec.ConfigValue<Boolean> REACTOR_SIMULATION_DEBUG = BUILDER
            .comment("Log reactor simulation (heat sink rod/adj/nonAdj, weights, fuel/energy mult) each tick. Default: false")
            .define("001_reactor_simulation_debug", false);

    public static final ModConfigSpec.ConfigValue<Boolean> MELTER_DEBUG = BUILDER
            .comment("Log why the Melter is not advancing when it has input (no recipe, fluid not found, heat product). Default: false")
            .define("002_melter_debug", false);

    public static final ModConfigSpec.ConfigValue<Boolean> TURBINE_VALIDATION_DEBUG = BUILDER
            .comment("Dump full turbine validation to log (info level). Default: false")
            .define("004_turbine_validation_debug", false);

    public static final ModConfigSpec.ConfigValue<Boolean> TURBINE_SIMULATION_DEBUG = BUILDER
            .comment("Log turbine simulation (coil/blade eff, steam, RF/tick). Default: false")
            .define("005_turbine_simulation_debug", false);

    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_RADIATION_MANAGEMENT = BUILDER
            .comment("Does not enable radiation in Colossal Reactors reactors. Enable radiation management features (Radiation Scrubber, Radiation Cure). When true and Mekanism is installed, items and recipes appear in creative tab and are craftable. Default: false")
            .define("003_enable_radiation_management", false);

    public static final ModConfigSpec.ConfigValue<Boolean> POWER_PORT_OP_DEBUG = BUILDER
            .comment("Log high-conduction power port OP push details (Brandon's Core / Draconic). Default: false")
            .define("006_power_port_op_debug", false);

    static {
        BUILDER.pop();
    }

    // ========== worldgen ==========
    static {
        BUILDER.comment("Ore generation toggles").push("worldgen");
    }

    public static final ModConfigSpec.ConfigValue<Boolean> DISABLE_URANIUM_OREGEN = BUILDER
            .comment("When true, disables uranium ore generation in the world. Default: false")
            .define("000_disable_uranium_oregen", false);
    public static final ModConfigSpec.ConfigValue<Boolean> DISABLE_LEAD_OREGEN = BUILDER
            .comment("When true, disables lead ore generation in the world. Default: false")
            .define("001_disable_lead_oregen", false);
    public static final ModConfigSpec.ConfigValue<Boolean> DISABLE_BORON_OREGEN = BUILDER
            .comment("When true, disables boron ore generation in the world. Few mods add boron; useful to keep only boron. Default: false")
            .define("002_disable_boron_oregen", false);
    public static final ModConfigSpec.ConfigValue<Boolean> DISABLE_CHROMIUM_OREGEN = BUILDER
            .comment("When true, disables chromium ore generation in the world. Default: false")
            .define("003_disable_chromium_oregen", false);
    public static final ModConfigSpec.ConfigValue<Boolean> DISABLE_CALIFORNIUM_OREGEN = BUILDER
            .comment("When true, disables californium End ore generation. Default: false")
            .define("004_disable_californium_oregen", false);

    static {
        BUILDER.pop();
    }

    // ========== reactor ==========
    static {
        BUILDER.comment("Reactor multiblock and simulation").push("reactor");
    }

    // --- reactor.size ---
    static {
        BUILDER.comment("Maximum reactor dimensions").push("size");
    }

    public static final ModConfigSpec.IntValue MAX_REACTOR_WIDTH = BUILDER
            .comment("Maximum reactor width (X or Z). Default: 65")
            .defineInRange("000_maxReactorWidth", 65, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue MAX_REACTOR_LENGTH = BUILDER
            .comment("Maximum reactor length (X or Z). Default: 64")
            .defineInRange("001_maxReactorLength", 65, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue MAX_REACTOR_HEIGHT = BUILDER
            .comment("Maximum reactor height (Y). Default: 65")
            .defineInRange("002_maxReactorHeight", 65, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // --- reactor.base ---
    static {
        BUILDER.comment("Reactor capacity and minimums (base RF/fuel come from datapack fuel definitions)").push("base");
    }

    public static final ModConfigSpec.DoubleValue MIN_RF_PER_TICK = BUILDER
            .comment("Minimum RF/t produced by any running reactor. Default: 500")
            .defineInRange("101b_minRfPerTick", 500.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue MIN_FUEL_UNITS_PER_TICK = BUILDER
            .comment("Minimum fuel units consumed per tick. Default: 0.05")
            .defineInRange("101c_minFuelUnitsPerTick", 0.05, 0.0, 100.0);
    public static final ModConfigSpec.IntValue ROD_MAX_FUEL_UNITS = BUILDER
            .comment("Max fuel units per reactor rod (shared capacity). Default: 10000")
            .defineInRange("000_rodMaxFuelUnits", 10000, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // --- reactor.efficiency ---
    static {
        BUILDER.comment("Efficiency and global multipliers").push("efficiency");
    }

    public static final ModConfigSpec.DoubleValue RF_EFFICIENCY_LOSS = BUILDER
            .comment("RF efficiency loss factor. Default: 0.2")
            .defineInRange("000_rfEfficiencyLoss", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FUEL_EFFICIENCY_LOSS = BUILDER
            .comment("Fuel efficiency factor. Default: 1")
            .defineInRange("001_fuelEfficiencyLoss", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue PRODUCTION_MULTIPLIER = BUILDER
            .comment("Production multiplier. Default: 1")
            .defineInRange("002_productionMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CONSUMPTION_MULTIPLIER = BUILDER
            .comment("Consumption multiplier. Default: 1")
            .defineInRange("003_consumptionMultiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
    }

    // --- reactor.consumption ---
    static {
        BUILDER.comment("Fuel consumption curve (scale and size-based decay)").push("consumption");
    }

    public static final ModConfigSpec.DoubleValue CONSUMPTION_SCALE = BUILDER
            .comment("Consumption scale factor for empirical curve. Default: 1.72")
            .defineInRange("106b_consumptionScale", 1.72, 0.01, 10.0);
    public static final ModConfigSpec.DoubleValue CONSUMPTION_CURVE_DECAY_RODS = BUILDER
            .comment("As reactor grows, curve advantage fades. 0 = always sqrt; 400 = big reactors closer to linear. Default: 400")
            .defineInRange("106b2_consumptionCurveDecayRods", 400.0, 0.0, Double.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // --- reactor.rods ---
    static {
        BUILDER.comment("Rod adjacency (penalty for neighbors)").push("rods");
    }

    public static final ModConfigSpec.DoubleValue ROD_ADJACENCY_PENALTY = BUILDER
            .comment("Penalty per horizontal neighbor (rod or border). Default: 0.25")
            .defineInRange("106c_rodAdjacencyPenalty", 0.25, 0.0, 1.0);

    static {
        BUILDER.pop();
    }

    // --- reactor.balance ---
    static {
        BUILDER.comment("Reactor balance (size scaling curves)").push("balance");
    }

    public static final ModConfigSpec.IntValue ROD_ENERGY_SCALING_MODE = BUILDER
            .comment("Energy scaling mode vs effectiveRodCount. 0=Legacy (n*log), 1=Power (n^exp), 2=Saturating (n/(1+n/k)). Default: 1")
            .defineInRange("000_rodEnergyScalingMode", 1, 0, 2);

    public static final ModConfigSpec.DoubleValue ROD_ENERGY_SCALING_EXPONENT = BUILDER
            .comment("When rodEnergyScalingMode=1: exponent for n^exp. Smaller = flatter for large reactors. Default: 0.90")
            .defineInRange("001_rodEnergyScalingExponent", 0.90, 0.05, 1.50);

    public static final ModConfigSpec.DoubleValue ROD_ENERGY_SCALING_SATURATION_K = BUILDER
            .comment("When rodEnergyScalingMode=2: saturation constant k for n/(1+n/k). Default: 600")
            .defineInRange("002_rodEnergyScalingSaturationK", 600.0, 1.0, Double.MAX_VALUE);

    public static final ModConfigSpec.IntValue ROD_FUEL_SCALING_MODE = BUILDER
            .comment("Fuel scaling mode vs effectiveRodCount. 0=Legacy (n*log), 1=Power (n^exp), 2=Saturating (n/(1+n/k)). Default: 1 (linear exponent=1.0 keeps old behavior)")
            .defineInRange("003_rodFuelScalingMode", 1, 0, 2);

    public static final ModConfigSpec.DoubleValue ROD_FUEL_SCALING_EXPONENT = BUILDER
            .comment("When rodFuelScalingMode=1: exponent for n^exp. Default: 1.00 (linear; keeps old behavior)")
            .defineInRange("004_rodFuelScalingExponent", 1.00, 0.05, 1.50);

    public static final ModConfigSpec.DoubleValue ROD_FUEL_SCALING_SATURATION_K = BUILDER
            .comment("When rodFuelScalingMode=2: saturation constant k for n/(1+n/k). Default: 600")
            .defineInRange("005_rodFuelScalingSaturationK", 600.0, 1.0, Double.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // --- reactor.heatSink ---
    static {
        BUILDER.comment("Heat sink (coolant block) weights and formula multipliers").push("heatSink");
    }

    public static final ModConfigSpec.DoubleValue HEAT_SINK_ADJACENT_WEIGHT = BUILDER
            .comment("Weight for coolant cells touching a rod (Refrigerante 0). Default: 1.0")
            .defineInRange("106d_heatSinkAdjacentWeight", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue HEAT_SINK_NON_ADJACENT_WEIGHT = BUILDER
            .comment("Weight for coolant cells not touching any rod (Refrigerante 1). Default: 0.5")
            .defineInRange("106e_heatSinkNonAdjacentWeight", 0.5, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue HEAT_SINK_CONSUMPTION_DIVISOR = BUILDER
            .comment("Extra divisor for fuel consumption with heat sinks (MB%%). Default: 1.35")
            .defineInRange("106f_heatSinkConsumptionDivisor", 1.35, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue HEAT_SINK_RF_MULTIPLIER = BUILDER
            .comment("Scale RF produced with heat sink formula. Default: 1.16")
            .defineInRange("106g_heatSinkRfMultiplier", 1.16, 0.1, 5.0);
    public static final ModConfigSpec.DoubleValue HEAT_SINK_FUEL_UNITS_MULTIPLIER = BUILDER
            .comment("Scale fuel consumption with heat sinks. Default: 1.21")
            .defineInRange("106h_heatSinkFuelUnitsMultiplier", 1.21, 0.1, 5.0);

    static {
        BUILDER.pop();
    }

    // --- reactor.validation ---
    static {
        BUILDER.comment("Structure validation").push("validation");
    }

    public static final ModConfigSpec.IntValue REACTOR_VALIDATION_INTERVAL_TICKS = BUILDER
            .comment("Ticks between reactor structure re-validation when ON (e.g. 200 = 10s). Default: 200")
            .defineInRange("000_reactorValidationIntervalTicks", 200, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.BooleanValue ALLOW_MULTIPLE_REACTOR_CONTROLLERS = BUILDER
            .comment("If false, reactor is valid only with exactly one reactor controller on its outer faces. If true, multiple controllers allowed. Default: false")
            .define("001_allowMultipleReactorControllers", false);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop(); // reactor
    }

    // ========== builder ==========
    static {
        BUILDER.comment("Reactor Builder (construction speed and behavior)").push("builder");
    }

    public static final ModConfigSpec.IntValue REACTOR_BUILDER_BUILD_STEPS_PER_TICK = BUILDER
            .comment("How many build steps to execute per tick while building. Higher = faster, but heavier server load. Default: 2")
            .defineInRange("000_buildStepsPerTick", 2, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue REACTOR_BUILDER_TANK_CAPACITY_MB = BUILDER
            .comment("Reactor Builder internal fluid tank capacity in mB. Default: 64000")
            .defineInRange("001_tankCapacityMb", 64000, 1000, Integer.MAX_VALUE);

    static {
        BUILDER.pop(); // builder
    }

    // ========== melter ==========
    static {
        BUILDER.comment("Melter fluid tank").push("melter");
    }

    public static final ModConfigSpec.IntValue MELTER_TANK_CAPACITY_MB = BUILDER
            .comment("Melter internal fluid tank capacity in mB. Default: 64000")
            .defineInRange("000_tankCapacityMb", 64000, 1000, Integer.MAX_VALUE);

    static {
        BUILDER.pop(); // melter
    }

    // ========== radiation_scrubber ==========
    static {
        BUILDER.comment("Radiation Scrubber (Mekanism integration). Energy, scrub range, interval and catalyst.").push("radiation_scrubber");
    }

    public static final ModConfigSpec.IntValue RADIATION_SCRUBBER_ENERGY_PER_TICK = BUILDER
            .comment("Energy (RF) consumed per scrub tick when the machine runs. Default: 100")
            .defineInRange("000_energyPerTick", 100, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue RADIATION_SCRUBBER_ENERGY_CAPACITY = BUILDER
            .comment("Energy buffer capacity in RF. Default: 10000")
            .defineInRange("001_energyCapacity", 10000, 100, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue RADIATION_SCRUBBER_RADIUS_BLOCKS = BUILDER
            .comment("Scrub radius in blocks (distance from machine)")
            .defineInRange("010_radiusBlocks", 50, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue RADIATION_SCRUBBER_INTERVAL_TICKS = BUILDER
            .comment("Ticks between each scrub pass. Default: 10")
            .defineInRange("011_intervalTicks", 10, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue RADIATION_SCRUBBER_BASE_RADIATION_REMOVAL = BUILDER
            .comment("Base multiplier for how much radiation is removed per scrub (applied to datapack effectiveness). Default: 100")
            .defineInRange("100_baseRadiationRemoval", 100, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue RADIATION_SCRUBBER_BASE_GAS_DESTRUCTION = BUILDER
            .comment("Base mB of radioactive gas destroyed per tick (100 RF per tick). With catalyst: base * gas_mult from datapack. Default: 10")
            .defineInRange("101_baseGasDestruction", 10, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // ========== turbine ==========
    static {
        BUILDER.comment("Turbine multiblock and simulation").push("turbine");
    }

    static {
        BUILDER.comment("Maximum turbine dimensions").push("size");
    }

    public static final ModConfigSpec.IntValue MAX_TURBINE_WIDTH = BUILDER
            .comment("Maximum turbine width (X or Z). Default: 65")
            .defineInRange("000_maxTurbineWidth", 65, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue MAX_TURBINE_LENGTH = BUILDER
            .comment("Maximum turbine length (X or Z). Default: 65")
            .defineInRange("001_maxTurbineLength", 65, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue MAX_TURBINE_HEIGHT = BUILDER
            .comment("Maximum turbine height (Y). Default: 65")
            .defineInRange("002_maxTurbineHeight", 65, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Turbine capacity and defaults (rf_production primary source is datapack)").push("base");
    }

    public static final ModConfigSpec.DoubleValue TURBINE_MIN_RF_PER_TICK = BUILDER
            .comment("Minimum RF/t produced by any running turbine. Default: 0")
            .defineInRange("101b_minRfPerTick", 0.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue TURBINE_DEFAULT_RF_PER_STEAM_MB = BUILDER
            .comment("Fallback RF per mB steam when datapack entry is missing. Default: 4")
            .defineInRange("000_defaultRfPerSteamMb", 4.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.IntValue TURBINE_DEFAULT_COIL_LAYER_COUNT = BUILDER
            .comment("Default coil-layer setting in turbine builder (GUI shows this + 1). Default: 1 (= 2 layers in GUI)")
            .defineInRange("001_defaultCoilLayerCount", 1, 0, 32);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Turbine balance (global multipliers and steam cap)").push("balance");
    }

    public static final ModConfigSpec.DoubleValue TURBINE_PRODUCTION_MULTIPLIER = BUILDER
            .comment("Production multiplier for turbine RF output. Default: 1")
            .defineInRange("000_productionMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue TURBINE_CONSUMPTION_MULTIPLIER = BUILDER
            .comment("Consumption multiplier for turbine steam input. Default: 1")
            .defineInRange("001_consumptionMultiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Balanced blade rings on turbine rods").push("blade");
    }

    public static final ModConfigSpec.DoubleValue TURBINE_STEAM_MB_PER_BLADE_PER_TICK = BUILDER
            .comment("Max mB steam per tick counted per valid balanced blade. Default: 1000")
            .defineInRange("004_steamMbPerBladePerTick", 1000.0, 1.0, Double.MAX_VALUE);
    public static final ModConfigSpec.IntValue TURBINE_STEAM_INPUT_MB_PER_BLADE = BUILDER
            .comment("Steam input buffer capacity (mB) per valid balanced blade in the turbine controller,",
                    "same idea as reactor coolant per rod (10000). Output condensate stays one tick on ports. Default: 10000")
            .defineInRange("005_steamInputMbPerBlade", 10_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_TURBINE_BLADE_RING = BUILDER
            .comment("Maximum blade distance from a turbine rod in blocks (ring index). Each complete ring adds four blades.",
                    "Default 31 allows a 63x63 internal span with the rod at the center ((63 - 1) / 2).")
            .defineInRange("000_maxBladeRing", 31, 1, 64);
    public static final ModConfigSpec.DoubleValue TURBINE_BLADE_EFFICIENCY_BONUS_PER_ASCENDING_LAYER = BUILDER
            .comment("Blade efficiency bonus per ascending layer (+3%% = 0.03). Default: 0.03")
            .defineInRange("001_efficiencyBonusPerAscendingLayer", 0.03, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue TURBINE_BLADE_EFFICIENCY_MALUS_PER_DESCENDING_LAYER = BUILDER
            .comment("Blade efficiency malus per descending layer after bonus break. Default: 0.03")
            .defineInRange("002_efficiencyMalusPerDescendingLayer", 0.03, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue TURBINE_REQUIRE_BALANCED_BLADE_RINGS = BUILDER
            .comment("Require balanced blade rings (4, 8, 12... per rod) for runtime validation and steam cap. Default: true")
            .define("003_requireBalancedBladeRings", true);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Elec coil zone tuning").push("coil");
    }

    public static final ModConfigSpec.DoubleValue TURBINE_EMPTY_COIL_EFFICIENCY = BUILDER
            .comment("Efficiency for air or unknown blocks in coil zone. Default: 0.3")
            .defineInRange("000_emptyCoilEfficiency", 0.3, 0.0, 10.0);
    public static final ModConfigSpec.BooleanValue TURBINE_LEGACY_IGNORE_AIR_IN_COIL_AVERAGE = BUILDER
            .comment("Recreate old bug: skip air cells when averaging coil-zone efficiency (solid coils only). Default: false (air counted)")
            .define("001_legacyIgnoreAirInCoilAverage", false);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Structure validation").push("validation");
    }

    public static final ModConfigSpec.IntValue TURBINE_VALIDATION_INTERVAL_TICKS = BUILDER
            .comment("Ticks between turbine structure re-validation when ON. Default: 200")
            .defineInRange("000_turbineValidationIntervalTicks", 200, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.BooleanValue ALLOW_MULTIPLE_TURBINE_CONTROLLERS = BUILDER
            .comment("If false, turbine is valid only with exactly one turbine controller. Default: false")
            .define("001_allowMultipleTurbineControllers", false);

    static {
        BUILDER.pop();
        BUILDER.pop(); // turbine
    }

    // ========== turbine_builder ==========
    static {
        BUILDER.comment("Turbine Builder (construction speed and behavior)").push("turbine_builder");
    }

    public static final ModConfigSpec.IntValue TURBINE_BUILDER_BUILD_STEPS_PER_TICK = BUILDER
            .comment("Build steps per tick while building. Default: 2")
            .defineInRange("000_buildStepsPerTick", 2, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue TURBINE_BUILDER_TANK_CAPACITY_MB = BUILDER
            .comment("Turbine Builder internal fluid tank capacity in mB. Default: 64000")
            .defineInRange("001_tankCapacityMb", 64000, 1000, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // ========== evil_things ==========
    static {
        BUILDER.comment("Optional integrations that may be considered overpowered or disruptive. All default: false.",
                "Enable only if you want these effects.").push("evil_things");
    }

    public static final ModConfigSpec.BooleanValue REACTOR_UNSTABILITY = BUILDER
            .comment("Reactor unstability. Default: false")
            .define("000_reactor_unstability", false);

    static {
        BUILDER.comment("Cooling/production ratio, permille cap, and interior thermal stress when unstability is enabled.")
                .push("reactor_stability");
    }

    public static final ModConfigSpec.IntValue REACTOR_UNSTABILITY_MAX_STABILITY_PERMILLE = BUILDER
            .comment("Maximum stability when reactor unstability is enabled. Default: 1000")
            .defineInRange("000_reactor_unstability_max_stability", 1000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue STABILITY_INTERIOR_VOLUME_BASELINE = BUILDER
            .comment("Interior block count at or below: base cooling/production ratio only (forgiving for small reactors). Default: 4096")
            .defineInRange("001_stability_interior_volume_baseline", 4096, 64, 1_000_000_000);
    public static final ModConfigSpec.IntValue STABILITY_INTERIOR_VOLUME_STRESS = BUILDER
            .comment("Reference interior size where extra required ratio reaches maximum (huge reactors need top-tier coolant). Default: 262144")
            .defineInRange("002_stability_interior_volume_stress", 262144, 1000, 1_000_000_000);
    public static final ModConfigSpec.DoubleValue STABILITY_MAX_EXTRA_REQUIRED_RATIO = BUILDER
            .comment("At stress volume, required cooling/production ratio is base (0.85) plus this value. Default: 0.11")
            .defineInRange("003_stability_max_extra_required_ratio", 0.11, 0.0, 0.25);

    public static final ModConfigSpec.BooleanValue THERMAL_STRESS_FROM_SIZE_ENABLED = BUILDER
            .comment("Interior-span thermal stress divides overheating-based cooling RF (longest interior edge). Larger reactors need higher overheating tiers (datapack) for stability. Default: true")
            .define("100_thermal_stress_from_size_enabled", true);
    public static final ModConfigSpec.DoubleValue THERMAL_STRESS_L_REF = BUILDER
            .comment("Reference interior span in blocks where factor is neutral (~1 after clipping). Typical small interior ~5 for a 7-shell reactor. Default: 5")
            .defineInRange("101_thermal_stress_l_ref", 5.0, 1.0, 512.0);
    public static final ModConfigSpec.DoubleValue THERMAL_STRESS_L_MIN = BUILDER
            .comment("Minimum interior span used in the stress curve (clip). Default: 3")
            .defineInRange("102_thermal_stress_l_min", 3.0, 1.0, 512.0);
    public static final ModConfigSpec.DoubleValue THERMAL_STRESS_GAMMA = BUILDER
            .comment("Exponent for (L/L_ref)^gamma; lower = gentler growth with size. Default: 0.38")
            .defineInRange("103_thermal_stress_gamma", 0.38, 0.05, 3.0);
    public static final ModConfigSpec.DoubleValue THERMAL_STRESS_ALPHA = BUILDER
            .comment("Stress strength: factor = 1 + alpha * ((L/L_ref)^gamma - 1). Default: 0.45")
            .defineInRange("104_thermal_stress_alpha", 0.45, 0.0, 3.0);
    public static final ModConfigSpec.BooleanValue THERMAL_STRESS_SCALE_FLUID_COOLING = BUILDER
            .comment("Apply the same thermal stress factor to fluid coolant overheating (water/steam path). Default: true")
            .define("105_thermal_stress_scale_fluid_cooling", true);

    static {
        BUILDER.pop(); // reactor_stability
    }

    public static final ModConfigSpec.BooleanValue MEKANISM_RADIATION_INTEGRATION = BUILDER
            .comment("Mekanism radiation integration. Default: false")
            .define("100_mekanism_radiation_integration", false);

    public static final ModConfigSpec.BooleanValue ISKA_UTILS_EXPLOSION_INTEGRATION = BUILDER
            .comment("Iska Utils freelag explosion on meltdown (requires iska_utils in the pack). Default: false")
            .define("101_iska_utils_explosion_integration", false);

    public static final ModConfigSpec.BooleanValue ALEXS_CAVES_EXPLOSION_INTEGRATION = BUILDER
            .comment("Alex's Caves explosion integration. Default: false")
            .define("102_alexs_caves_explosion_integration", false);

    static {
        BUILDER.pop(); // evil_things
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
