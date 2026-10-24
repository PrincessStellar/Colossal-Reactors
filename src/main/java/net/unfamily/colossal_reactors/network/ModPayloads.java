package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPayloads {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ColossalReactors.MODID).versioned("1");
        registrar.playToServer(
                ResourcePortModePayload.TYPE,
                ResourcePortModePayload.STREAM_CODEC,
                ResourcePortModePayload::handle
        );
        registrar.playToServer(
                ResourcePortFilterPayload.TYPE,
                ResourcePortFilterPayload.STREAM_CODEC,
                ResourcePortFilterPayload::handle
        );
        registrar.playToServer(
                ResourcePortSettingsPayload.TYPE,
                ResourcePortSettingsPayload.STREAM_CODEC,
                ResourcePortSettingsPayload::handle
        );
        registrar.playToServer(
                RedstonePortRedstoneModePayload.TYPE,
                RedstonePortRedstoneModePayload.STREAM_CODEC,
                RedstonePortRedstoneModePayload::handle
        );
        registrar.playToServer(
                MelterRedstoneModePayload.TYPE,
                MelterRedstoneModePayload.STREAM_CODEC,
                MelterRedstoneModePayload::handle
        );
        registrar.playToServer(
                HeatingCoilRedstoneModePayload.TYPE,
                HeatingCoilRedstoneModePayload.STREAM_CODEC,
                HeatingCoilRedstoneModePayload::handle
        );
        registrar.playToServer(
                ReactorControllerRefreshPayload.TYPE,
                ReactorControllerRefreshPayload.STREAM_CODEC,
                ReactorControllerRefreshPayload::handle
        );
        registrar.playToServer(
                TurbineControllerRefreshPayload.TYPE,
                TurbineControllerRefreshPayload.STREAM_CODEC,
                TurbineControllerRefreshPayload::handle
        );
        registrar.playToServer(
                ReactorBuilderSizePayload.TYPE,
                ReactorBuilderSizePayload.STREAM_CODEC,
                ReactorBuilderSizePayload::handle
        );
        registrar.playToServer(
                ReactorPreviewPayload.TYPE,
                ReactorPreviewPayload.STREAM_CODEC,
                ReactorPreviewPayload::handle
        );
        registrar.playToServer(
                BuilderPreviewTogglePayload.TYPE,
                BuilderPreviewTogglePayload.STREAM_CODEC,
                BuilderPreviewTogglePayload::handle
        );
        registrar.playToServer(
                ReactorBuilderHeatSinkPayload.TYPE,
                ReactorBuilderHeatSinkPayload.STREAM_CODEC,
                ReactorBuilderHeatSinkPayload::handle
        );
        registrar.playToServer(
                ReactorBuilderSimSettingsPayload.TYPE,
                ReactorBuilderSimSettingsPayload.STREAM_CODEC,
                ReactorBuilderSimSettingsPayload::handle
        );
        registrar.playToServer(
                ReactorBuilderOptionPayload.TYPE,
                ReactorBuilderOptionPayload.STREAM_CODEC,
                ReactorBuilderOptionPayload::handle
        );
        registrar.playToServer(
                ReactorBuilderBuildPayload.TYPE,
                ReactorBuilderBuildPayload.STREAM_CODEC,
                ReactorBuilderBuildPayload::handle
        );
        registrar.playToServer(
                ReactorBuilderMarkInputPayload.TYPE,
                ReactorBuilderMarkInputPayload.STREAM_CODEC,
                ReactorBuilderMarkInputPayload::handle
        );
        registrar.playToServer(
                ReactorBuilderWebsiteDesignPayload.TYPE,
                ReactorBuilderWebsiteDesignPayload.STREAM_CODEC,
                ReactorBuilderWebsiteDesignPayload::handle
        );
        registrar.playToServer(
                FluidTankDumpPayload.TYPE,
                FluidTankDumpPayload.STREAM_CODEC,
                FluidTankDumpPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderSizePayload.TYPE,
                TurbineBuilderSizePayload.STREAM_CODEC,
                TurbineBuilderSizePayload::handle
        );
        registrar.playToServer(
                TurbinePreviewPayload.TYPE,
                TurbinePreviewPayload.STREAM_CODEC,
                TurbinePreviewPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderCoilPayload.TYPE,
                TurbineBuilderCoilPayload.STREAM_CODEC,
                TurbineBuilderCoilPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderSimSettingsPayload.TYPE,
                TurbineBuilderSimSettingsPayload.STREAM_CODEC,
                TurbineBuilderSimSettingsPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderOptionPayload.TYPE,
                TurbineBuilderOptionPayload.STREAM_CODEC,
                TurbineBuilderOptionPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderBuildPayload.TYPE,
                TurbineBuilderBuildPayload.STREAM_CODEC,
                TurbineBuilderBuildPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderMarkInputPayload.TYPE,
                TurbineBuilderMarkInputPayload.STREAM_CODEC,
                TurbineBuilderMarkInputPayload::handle
        );
        registrar.playToServer(
                TurbineBuilderWebsiteDesignPayload.TYPE,
                TurbineBuilderWebsiteDesignPayload.STREAM_CODEC,
                TurbineBuilderWebsiteDesignPayload::handle
        );
        registrar.playToClient(
                ClearPreviewPayload.TYPE,
                ClearPreviewPayload.STREAM_CODEC,
                ClearPreviewPayload::handle
        );
        registrar.playToClient(
                ReactorPreviewMarkerPayload.TYPE,
                ReactorPreviewMarkerPayload.STREAM_CODEC,
                ReactorPreviewMarkerPayload::handle
        );
        registrar.playToClient(
                ClearPreviewForBuilderPayload.TYPE,
                ClearPreviewForBuilderPayload.STREAM_CODEC,
                ClearPreviewForBuilderPayload::handle
        );
    }

    /** S2C: footprint preview marker owned by a builder block. */
    public static void sendPreviewMarker(
            ServerPlayer player, BlockPos builderOrigin, BlockPos pos, int color, int durationTicks, int footprintGeneration) {
        PacketDistributor.sendToPlayer(player,
                new ReactorPreviewMarkerPayload(builderOrigin, pos, color, durationTicks, footprintGeneration));
    }

    /** S2C: ephemeral marker (validation hints) not tied to a builder. */
    public static void sendEphemeralPreviewMarker(ServerPlayer player, BlockPos pos, int color, int durationTicks) {
        sendPreviewMarker(player, BlockPos.ZERO, pos, color, durationTicks, 0);
    }
}
