package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import net.unfamily.colossal_reactors.menu.ReactorBuilderMenu;
import org.jetbrains.annotations.Nullable;

public record ReactorBuilderMarkInputPayload(BlockPos pos, int mode) implements CustomPacketPayload {

    public static final int MODE_NORMAL = 0;
    public static final int MODE_SHIFT = 1;
    public static final int MODE_CTRL = 2;

    public static final Type<ReactorBuilderMarkInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "reactor_builder_mark_input"));

    public static final StreamCodec<FriendlyByteBuf, ReactorBuilderMarkInputPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ReactorBuilderMarkInputPayload::pos,
            ByteBufCodecs.INT,
            ReactorBuilderMarkInputPayload::mode,
            ReactorBuilderMarkInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReactorBuilderMarkInputPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            ReactorBuilderBlockEntity builder = resolveBuilderForMarkInput(player, level, packet.pos());
            if (builder == null) return;
            switch (packet.mode()) {
                case MODE_NORMAL -> builder.applyMarkInputFromBuffer();
                case MODE_SHIFT -> builder.clearAllMarkInputFilters();
                case MODE_CTRL -> builder.clearMarkInputFiltersWithoutMatchingStacks();
                default -> {}
            }
            builder.setChanged();
            player.connection.send(ClientboundBlockEntityDataPacket.create(builder));
        });
    }

    /**
     * Prefer the block entity bound to the open {@link ReactorBuilderMenu} so we always mutate the same instance
     * the server menu uses (avoids stale/wrong {@code packet.pos()} desync). Fall back to world lookup.
     */
    private static @Nullable ReactorBuilderBlockEntity resolveBuilderForMarkInput(ServerPlayer player, ServerLevel level, BlockPos packetPos) {
        if (player.containerMenu instanceof ReactorBuilderMenu menu
                && menu.getBlockEntity() != null
                && menu.stillValid(player)) {
            return menu.getBlockEntity();
        }
        BlockEntity be = level.getBlockEntity(packetPos);
        return be instanceof ReactorBuilderBlockEntity reactorBuilder ? reactorBuilder : null;
    }
}
