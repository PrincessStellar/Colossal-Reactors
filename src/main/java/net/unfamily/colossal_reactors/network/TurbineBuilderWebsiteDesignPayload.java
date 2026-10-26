package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;
import net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns;
import net.unfamily.colossal_reactors.turbine.TurbineRodPatternLogic;

/**
 * C2S: set or clear the ModDecoded turbine design code on the builder.
 * Full apply (size / coils / {@code PATTERN_WEBSITE}) only when already on Website mode;
 * otherwise the seed is stored and ignored until the player returns to Website.
 */
public record TurbineBuilderWebsiteDesignPayload(BlockPos pos, String designCode) implements CustomPacketPayload {

    public static final Type<TurbineBuilderWebsiteDesignPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "turbine_builder_website_design"));

    public static final StreamCodec<FriendlyByteBuf, TurbineBuilderWebsiteDesignPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            TurbineBuilderWebsiteDesignPayload::pos,
            ByteBufCodecs.stringUtf8(1 << 20),
            TurbineBuilderWebsiteDesignPayload::designCode,
            TurbineBuilderWebsiteDesignPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TurbineBuilderWebsiteDesignPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (be instanceof TurbineBuilderBlockEntity builder) {
                String code = packet.designCode() == null ? "" : packet.designCode().trim();
                if (code.isEmpty()) {
                    builder.setWebsiteDesign("");
                } else if (builder.getRodPattern() == TurbineRodPatternLogic.PATTERN_WEBSITE) {
                    BuilderWebsiteDesigns.applyToTurbineBuilder(builder, code);
                } else {
                    // Keep seed for later; do not force Website / overwrite classic mode settings.
                    builder.setWebsiteDesign(code);
                }
            }
        });
    }
}
