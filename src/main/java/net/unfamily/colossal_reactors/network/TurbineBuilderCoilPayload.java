package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;

/**
 * C2S: cycle turbine builder coil material. {@code next}=true advances, false goes back.
 * {@code rodColumnCoil}=false cycles zone coil ({@link net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity#cycleCoil});
 * true cycles rod-column coil ({@link net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity#cycleRodCoil}).
 */
public record TurbineBuilderCoilPayload(BlockPos pos, boolean next, boolean rodColumnCoil) implements CustomPacketPayload {

    public static final Type<TurbineBuilderCoilPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "turbine_builder_coil"));

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
            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (be instanceof TurbineBuilderBlockEntity builder) {
                if (packet.rodColumnCoil()) {
                    builder.cycleRodCoil(packet.next());
                } else {
                    builder.cycleCoil(packet.next());
                }
                level.playSound(null, packet.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.0f);
            }
        });
    }
}
