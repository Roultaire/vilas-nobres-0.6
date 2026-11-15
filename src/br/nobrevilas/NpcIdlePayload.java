package br.nobrevilas;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor -> cliente: um NPC da vila esta falando sozinho (vida da vila).
 * O balao aparece ACIMA DA CABECA do mob, no mundo, sem abrir tela nenhuma.
 * texto = fala \u0001 cor-do-nome \u0001 nome-do-npc
 */
public record NpcIdlePayload(String entityUuid, String texto, int x, int y, int z) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NobreVilas.MOD_ID, "npc_idle");
    public static final CustomPacketPayload.Type<NpcIdlePayload> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcIdlePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, NpcIdlePayload::entityUuid,
        ByteBufCodecs.STRING_UTF8, NpcIdlePayload::texto,
        ByteBufCodecs.VAR_INT, NpcIdlePayload::x,
        ByteBufCodecs.VAR_INT, NpcIdlePayload::y,
        ByteBufCodecs.VAR_INT, NpcIdlePayload::z,
        NpcIdlePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
