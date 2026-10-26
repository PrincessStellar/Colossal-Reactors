package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;

/**
 * C2S: cycle turbine builder options. optionType: 0=openTop, 1=rodPattern, 2=coilLayerCount, 3=placementAxis, 4=bladeVariant.
 * next=true = next option, next=false = previous (same as heat sink: left click=next, right click=previous).
 */
public record TurbineBuilderOptionPayload(BlockPos pos, int optionType, boolean next) implements CustomPacketPayload {

    public static final Type<TurbineBuilderOptionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ColossalReactors.MODID, "turbine_builder_option"));

    public static final StreamCodec<FriendlyByteBuf, TurbineBuilderOptionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            TurbineBuilderOptionPayload::pos,
            net.minecraft.network.codec.ByteBufCodecs.INT,
            TurbineBuilderOptionPayload::optionType,
            net.minecraft.network.codec.ByteBufCodecs.BOOL,
            TurbineBuilderOptionPayload::next,
            TurbineBuilderOptionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TurbineBuilderOptionPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.level();
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (be instanceof TurbineBuilderBlockEntity builder) {
                switch (Math.max(0, packet.optionType())) {
                    case 0 -> builder.cycleOpenTop(packet.next());
                    case 1 -> builder.cycleRodPattern(packet.next());
                    case 2 -> builder.cycleCoilLayerCount(packet.next());
                    case 3 -> builder.cyclePlacementAxis(packet.next());
                    case 4 -> builder.cycleBladeVariant(packet.next());
                    default -> {}
                }
            }
        });
    }
}
