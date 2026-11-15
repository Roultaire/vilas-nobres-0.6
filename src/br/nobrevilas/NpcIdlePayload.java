package br.nobrevilas;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor -> cliente: um NPC da vila esta falando sozinho (vida da vila).
 * O cliente responde pedindo a fala em NpcTalkPayload, no mesmo fluxo da conversa.
 */
public record NpcIdlePayload(String entityUuid) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NobreVilas.MOD_ID, "npc_idle");
    public static final CustomPacketPayload.Type<NpcIdlePayload> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcIdlePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, NpcIdlePayload::entityUuid,
        NpcIdlePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
