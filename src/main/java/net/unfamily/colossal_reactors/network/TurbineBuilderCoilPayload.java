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
 * C2S: cycle turbine builder coil material. {@code next}=true advances, false goes back.
 * {@code rodColumnCoil}=false cycles zone coil; true cycles rod-column coil above rods.
 */
public record TurbineBuilderCoilPayload(BlockPos pos, boolean next, boolean rodColumnCoil) implements CustomPacketPayload {

    public static final Type<TurbineBuilderCoilPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ColossalReactors.MODID, "turbine_builder_coil"));

    public static final StreamCodec<FriendlyByteBuf, TurbineBuilderCoilPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            TurbineBuilderCoilPayload::pos,
            net.minecraft.network.codec.ByteBufCodecs.BOOL,
            TurbineBuilderCoilPayload::next,
            net.minecraft.network.codec.ByteBufCodecs.BOOL,
            TurbineBuilderCoilPayload::rodColumnCoil,
            TurbineBuilderCoilPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TurbineBuilderCoilPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.level();
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (be instanceof TurbineBuilderBlockEntity builder) {
                if (packet.rodColumnCoil()) {
                    builder.cycleRodCoil(packet.next());
                } else {
                    builder.cycleCoil(packet.next());
                }
            }
        });
    }
}
