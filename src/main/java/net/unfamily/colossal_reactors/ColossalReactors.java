package net.unfamily.colossal_reactors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.blockentity.HeatingCoilBlockEntity;
import net.unfamily.colossal_reactors.blockentity.HighCondPowerPortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.MelterBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ModBlockEntities;
import net.unfamily.colossal_reactors.blockentity.PowerPortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.RadiationScrubberBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ResourcePortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineHighCondPowerPortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbinePowerPortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineResourcePortBlockEntity;
import net.unfamily.colossal_reactors.client.GuideMeRegistration;
import net.unfamily.colossal_reactors.client.gui.HeatingCoilScreen;
import net.unfamily.colossal_reactors.client.gui.MelterScreen;
import net.unfamily.colossal_reactors.client.gui.RadiationScrubberScreen;
import net.unfamily.colossal_reactors.client.gui.ReactorBuilderScreen;
import net.unfamily.colossal_reactors.client.gui.ReactorControllerScreen;
import net.unfamily.colossal_reactors.client.gui.RedstonePortScreen;
import net.unfamily.colossal_reactors.client.gui.ResourcePortScreen;
import net.unfamily.colossal_reactors.client.gui.TurbineBuilderScreen;
import net.unfamily.colossal_reactors.client.gui.TurbineControllerScreen;
import net.unfamily.colossal_reactors.client.ColossalReactorsClientEvents;
import net.unfamily.colossal_reactors.client.turbine.TurbineRotorClientRegistration;
import net.unfamily.colossal_reactors.data.ColossalReactorsFusionModelProvider;
import net.unfamily.colossal_reactors.data.ModConditions;
import net.unfamily.colossal_reactors.crafting.ModColossalRecipes;
import net.unfamily.colossal_reactors.datapack.LoadDataReloadListener;
import net.unfamily.colossal_reactors.fluid.ModFluids;
import net.unfamily.colossal_reactors.integration.brandonscore.BrandonScoreIntegration;
import net.unfamily.colossal_reactors.item.ModCreativeModeTabs;
import net.unfamily.colossal_reactors.item.ModItems;
import net.unfamily.colossal_reactors.menu.ModMenuTypes;
import net.unfamily.colossal_reactors.network.ModPayloads;
import net.unfamily.colossal_reactors.network.BuilderPreviewServerEvents;
import net.unfamily.colossal_reactors.world.ModBiomeModifiers;
import net.unfamily.iskalib.client.marker.VanillaWorldMarkerClientHooks;
import net.unfamily.iskalib.gas.GasRegistrationRegisters;
import net.unfamily.iskalib.gas.IskaLibGases;
import net.unfamily.iskalib.gas.RegisteredGas;

@Mod(ColossalReactors.MODID)
public class ColossalReactors {
    public static final String MODID = "colossal_reactors";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Registered via {@link IskaLibGases}. */
    public static RegisteredGas STEAM_GAS;

    public ColossalReactors(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        if (ModList.get().isLoaded("mekanism")) {
            net.unfamily.colossal_reactors.item.ModMekItems.MEK_ITEMS.register(modEventBus);
            net.unfamily.colossal_reactors.integration.mekanism.ModMekanismChemicals.CHEMICALS.register(modEventBus);
        }
        ModFluids.register(modEventBus);

        STEAM_GAS = IskaLibGases.registerGas(
                modEventBus,
                new GasRegistrationRegisters(
                        ModFluids.FLUID_TYPES,
                        ModFluids.FLUIDS,
                        ModBlocks.BLOCKS,
                        ModItems.ITEMS),
                MODID,
                "steam",
                0xFFE8F0F0);

        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModPayloads.register(modEventBus);
        ModConditions.CONDITION_CODECS.register(modEventBus);
        ModBiomeModifiers.BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModColossalRecipes.register(modEventBus);
        modEventBus.addListener(this::gatherData);
        modEventBus.addListener(this::registerCapabilities);

        GuideMeRegistration.register();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        IskaLibGases.registerCapabilities(event);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.RESOURCE_PORT_BE.get(),
                (be, direction) -> ((ResourcePortBlockEntity) be).getItemHandlerForCapability());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.RESOURCE_PORT_BE.get(),
                (be, direction) -> ((ResourcePortBlockEntity) be).getFluidHandlerForCapability());
        registerResourcePortChemicalCapabilities(event);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.REACTOR_BUILDER_BE.get(),
                (be, direction) -> ((ReactorBuilderBlockEntity) be).getItemHandlerForCapability());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.REACTOR_BUILDER_BE.get(),
                (be, direction) -> ((ReactorBuilderBlockEntity) be).getFluidTank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.TURBINE_BUILDER_BE.get(),
                (be, direction) -> ((TurbineBuilderBlockEntity) be).getItemHandlerForCapability());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TURBINE_BUILDER_BE.get(),
                (be, direction) -> ((TurbineBuilderBlockEntity) be).getFluidTank());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_PORT_BE.get(),
                (be, direction) -> ((PowerPortBlockEntity) be).getEnergyStorageForCapability());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HIGH_COND_POWER_PORT_BE.get(),
                (be, direction) -> ((HighCondPowerPortBlockEntity) be).getEnergyStorageForCapability());
        BrandonScoreIntegration.registerHighCondPowerPortCapabilities(event);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.TURBINE_RESOURCE_PORT_BE.get(),
                (be, direction) -> ((TurbineResourcePortBlockEntity) be).getItemHandlerForCapability());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TURBINE_RESOURCE_PORT_BE.get(),
                (be, direction) -> ((TurbineResourcePortBlockEntity) be).getFluidHandlerForCapability());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.TURBINE_POWER_PORT_BE.get(),
                (be, direction) -> ((TurbinePowerPortBlockEntity) be).getEnergyStorageForCapability());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.TURBINE_HIGH_COND_POWER_PORT_BE.get(),
                (be, direction) -> ((TurbineHighCondPowerPortBlockEntity) be).getEnergyStorageForCapability());
        BrandonScoreIntegration.registerTurbineHighCondPowerPortCapabilities(event);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.HEATING_COIL_BE.get(),
                (be, direction) -> ((HeatingCoilBlockEntity) be).allowsCapabilityOnSide(direction)
                        && ((HeatingCoilBlockEntity) be).acceptsItemCapability()
                        ? ((HeatingCoilBlockEntity) be).getItemHandler()
                        : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.HEATING_COIL_BE.get(),
                (be, direction) -> ((HeatingCoilBlockEntity) be).allowsCapabilityOnSide(direction)
                        && ((HeatingCoilBlockEntity) be).acceptsFluidCapability()
                        ? ((HeatingCoilBlockEntity) be).getFluidHandler()
                        : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HEATING_COIL_BE.get(),
                (be, direction) -> ((HeatingCoilBlockEntity) be).allowsCapabilityOnSide(direction)
                        && ((HeatingCoilBlockEntity) be).acceptsEnergyCapability()
                        ? ((HeatingCoilBlockEntity) be).getEnergyStorage()
                        : null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MELTER_BE.get(),
                (be, direction) -> ((MelterBlockEntity) be).getItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.MELTER_BE.get(),
                (be, direction) -> ((MelterBlockEntity) be).getFluidHandlerForCapability());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.RADIATION_SCRUBBER_BE.get(),
                (be, direction) -> ((RadiationScrubberBlockEntity) be).getItemHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.RADIATION_SCRUBBER_BE.get(),
                (be, direction) -> ((RadiationScrubberBlockEntity) be).getEnergyStorage());
        registerRadiationScrubberChemicalCapability(event);
        registerHeatingCoilChemicalCapability(event);
    }

    @SuppressWarnings("unchecked")
    private static void registerHeatingCoilChemicalCapability(RegisterCapabilitiesEvent event) {
        try {
            if (!net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper.isLoaded()) return;
            Class<?> capsClass = Class.forName("mekanism.common.capabilities.Capabilities");
            Object chemicalMulti = capsClass.getField("CHEMICAL").get(null);
            Object blockCap = chemicalMulti.getClass().getMethod("block").invoke(chemicalMulti);
            event.registerBlockEntity(
                    (BlockCapability<Object, Direction>) blockCap,
                    ModBlockEntities.HEATING_COIL_BE.get(),
                    (HeatingCoilBlockEntity be, Direction direction) -> be.allowsCapabilityOnSide(direction)
                            && be.acceptsChemicalCapability()
                            ? be.getChemicalHandlerForCapability()
                            : null);
        } catch (Throwable t) {
            LOGGER.debug("Could not register Heating Coil chemical capability: {}", t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerResourcePortChemicalCapabilities(RegisterCapabilitiesEvent event) {
        try {
            if (!ModList.get().isLoaded("mekanism")) return;
            Class<?> capsClass = Class.forName("mekanism.common.capabilities.Capabilities");
            Object chemicalMulti = capsClass.getField("CHEMICAL").get(null);
            Object blockCap = chemicalMulti.getClass().getMethod("block").invoke(chemicalMulti);
            var cap = (net.neoforged.neoforge.capabilities.BlockCapability<Object, net.minecraft.core.Direction>) blockCap;
            event.registerBlockEntity(cap, ModBlockEntities.RESOURCE_PORT_BE.get(),
                    (ResourcePortBlockEntity be, net.minecraft.core.Direction direction) -> be.getChemicalHandlerForCapability());
            event.registerBlockEntity(cap, ModBlockEntities.TURBINE_RESOURCE_PORT_BE.get(),
                    (ResourcePortBlockEntity be, net.minecraft.core.Direction direction) -> be.getChemicalHandlerForCapability());
        } catch (Throwable t) {
            LOGGER.debug("Could not register Resource Port chemical capability: {}", t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerRadiationScrubberChemicalCapability(RegisterCapabilitiesEvent event) {
        try {
            if (!ModList.get().isLoaded("mekanism")) return;
            Class<?> capsClass = Class.forName("mekanism.common.capabilities.Capabilities");
            Object chemicalMulti = capsClass.getField("CHEMICAL").get(null);
            Object blockCap = chemicalMulti.getClass().getMethod("block").invoke(chemicalMulti);
            event.registerBlockEntity(
                    (BlockCapability<Object, Direction>) blockCap,
                    ModBlockEntities.RADIATION_SCRUBBER_BE.get(),
                    (RadiationScrubberBlockEntity be, Direction direction) -> be.getChemicalHandler());
        } catch (Throwable t) {
            LOGGER.debug("Could not register Radiation Scrubber chemical capability: {}", t.getMessage());
        }
    }

    private void gatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var packOutput = generator.getPackOutput();
        generator.addProvider(event.includeClient(), new ColossalReactorsFusionModelProvider(packOutput));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        NeoForge.EVENT_BUS.register(BuilderPreviewServerEvents.class);
        net.unfamily.colossal_reactors.config.ColossalAcmRules.register();
        LOGGER.debug("Colossal Reactors common setup");
        LOGGER.info("Reactor validation debug (dev.001_reactor_validation_debug): {}", Config.REACTOR_VALIDATION_DEBUG.get());
        LOGGER.info("Reactor simulation debug (dev.002_reactor_simulation_debug): {}", Config.REACTOR_SIMULATION_DEBUG.get());
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        // Native Colossal data recipes load via RecipeManager + ColossalRecipeData.
        // Keep load/ scanner for heating-coil retrocompat only.
        event.addListener(new LoadDataReloadListener());
    }

    @EventBusSubscriber(modid = ColossalReactors.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                NeoForge.EVENT_BUS.register(ColossalReactorsClientEvents.class);
                VanillaWorldMarkerClientHooks.registerIfNeeded(NeoForge.EVENT_BUS);
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.REACTOR_GLASS.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.TURBINE_GLASS.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.REACTOR_ROD.get(), RenderType.cutout());
                TurbineRotorClientRegistration.registerRenderLayers();
                ItemBlockRenderTypes.setRenderLayer(ModFluids.MOLTEN_TOUGH_ALLOY.block(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.MOLTEN_STAINLESS_STEEL.block(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.GELID_BREEZIUM.block(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.ENDER_GOO.block(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(STEAM_GAS.block(), RenderType.translucent());
            });
        }

        @SubscribeEvent
        static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new LoadDataReloadListener());
        }

        @SubscribeEvent
        static void onModifyBakingResult(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
            TurbineRotorClientRegistration.onModifyBakingResult(event);
        }

        @SubscribeEvent
        static void registerTurbineBer(EntityRenderersEvent.RegisterRenderers event) {
            TurbineRotorClientRegistration.registerRenderers(event);
        }

        @SubscribeEvent
        static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
            net.unfamily.colossal_reactors.client.ReactorRodColors.register(event);
        }

        @SubscribeEvent
        static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
            net.unfamily.colossal_reactors.client.ReactorRodColors.registerItem(event);
        }

        @SubscribeEvent
        static void registerMenuScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.RESOURCE_PORT_MENU.get(), ResourcePortScreen::new);
            event.register(ModMenuTypes.REDSTONE_PORT_MENU.get(), RedstonePortScreen::new);
            event.register(ModMenuTypes.REACTOR_CONTROLLER_MENU.get(), ReactorControllerScreen::new);
            event.register(ModMenuTypes.REACTOR_BUILDER_MENU.get(), ReactorBuilderScreen::new);
            event.register(ModMenuTypes.TURBINE_CONTROLLER_MENU.get(), TurbineControllerScreen::new);
            event.register(ModMenuTypes.TURBINE_BUILDER_MENU.get(), TurbineBuilderScreen::new);
            event.register(ModMenuTypes.HEATING_COIL_MENU.get(), HeatingCoilScreen::new);
            event.register(ModMenuTypes.MELTER_MENU.get(), MelterScreen::new);
            event.register(ModMenuTypes.RADIATION_SCRUBBER_MENU.get(), RadiationScrubberScreen::new);
        }
    }
}
