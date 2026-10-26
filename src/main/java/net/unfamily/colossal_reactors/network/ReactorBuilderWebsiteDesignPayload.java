package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns;
import net.unfamily.colossal_reactors.reactor.RodPatternLogic;

/**
 * C2S: set or clear the ModDecoded reactor design code on the builder.
 * Full apply only when already on Website mode; otherwise the seed is stored and ignored.
 */
public record ReactorBuilderWebsiteDesignPayload(BlockPos pos, String designCode) implements CustomPacketPayload {

    public static final Type<ReactorBuilderWebsiteDesignPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ColossalReactors.MODID, "reactor_builder_website_design"));

    public static final StreamCodec<FriendlyByteBuf, ReactorBuilderWebsiteDesignPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ReactorBuilderWebsiteDesignPayload::pos,
            ByteBufCodecs.stringUtf8(1 << 20),
            ReactorBuilderWebsiteDesignPayload::designCode,
            ReactorBuilderWebsiteDesignPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReactorBuilderWebsiteDesignPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = (ServerLevel) player.level();
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (be instanceof ReactorBuilderBlockEntity builder) {
                String code = packet.designCode() == null ? "" : packet.designCode().trim();
                if (code.isEmpty()) {
                    builder.setWebsiteDesign("");
                } else if (builder.getRodPattern() == RodPatternLogic.PATTERN_WEBSITE) {
                    BuilderWebsiteDesigns.applyToReactorBuilder(builder, code);
                } else {
                    builder.setWebsiteDesign(code);
                }
            }
        });
    }
}
