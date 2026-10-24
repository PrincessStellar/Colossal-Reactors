package net.unfamily.colossal_reactors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.menu.ReactorBuilderMenu;
import net.unfamily.colossal_reactors.menu.TurbineBuilderMenu;

/** Sets one builder buffer mark-input filter from a JEI ghost drop (or double-click clear). */
public record BuilderMarkFilterSetPayload(BlockPos pos, int slotIndex, ItemStack stack)
        implements CustomPacketPayload {

    public static final Type<BuilderMarkFilterSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "builder_mark_filter_set"));

    private static final StreamCodec<FriendlyByteBuf, ItemStack> ITEM_CODEC = new StreamCodec<>() {
        @Override
        public ItemStack decode(FriendlyByteBuf buffer) {
            return ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ItemStack stack) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, stack);
        }
    };

    public static final StreamCodec<FriendlyByteBuf, BuilderMarkFilterSetPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BuilderMarkFilterSetPayload::pos,
            ByteBufCodecs.INT, BuilderMarkFilterSetPayload::slotIndex,
            ITEM_CODEC, BuilderMarkFilterSetPayload::stack,
            BuilderMarkFilterSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BuilderMarkFilterSetPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof ReactorBuilderMenu menu
                    && menu.getBlockEntity() != null
                    && menu.getBlockEntity().getBlockPos().equals(packet.pos())
                    && menu.stillValid(player)) {
                menu.getBlockEntity().setMarkInputFilter(packet.slotIndex(), packet.stack());
                player.connection.send(ClientboundBlockEntityDataPacket.create(menu.getBlockEntity()));
                return;
            }
            if (player.containerMenu instanceof TurbineBuilderMenu menu
                    && menu.getBlockEntity() != null
                    && menu.getBlockEntity().getBlockPos().equals(packet.pos())
                    && menu.stillValid(player)) {
                menu.getBlockEntity().setMarkInputFilter(packet.slotIndex(), packet.stack());
                player.connection.send(ClientboundBlockEntityDataPacket.create(menu.getBlockEntity()));
            }
        });
    }
}
