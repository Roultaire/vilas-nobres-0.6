package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Baloes de fala dos NPCs no mundo: aparecem acima da cabeca do mob, seguem ele e somem sozinhos.
 * Nao abrem tela, nao pausam nada: e so uma legenda flutuante no HUD, presa ao NPC.
 * (Nao usa APIs de renderizacao do Fabric porque o pacote muda entre versoes; o HUD e estavel.)
 */
public final class ChatBubble {
    private record Bubble(String uuid, String text, String name, double x, double y, double z, long born) {}

    private static final List<Bubble> LIVE = new ArrayList<>();
    private static final long LIFE = 4500L;      // ms na tela
    private static final int MAX = 4;

    private ChatBubble() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> LIVE.removeIf(b -> System.currentTimeMillis() - b.born() > LIFE));
    }

    /** Chamado pelo cliente quando o servidor manda uma fala de fundo. */
    public static void push(NpcIdlePayload p) {
        String[] parts = p.texto().split("\u0001", 3);
        String fala = parts.length > 0 ? parts[0] : p.texto();
        String nome = parts.length > 2 ? parts[2] : "";
        long now = System.currentTimeMillis();
        LIVE.removeIf(b -> b.uuid().equals(p.entityUuid()) && now - b.born() > 600);
        if (LIVE.size() >= MAX) LIVE.remove(0);
        LIVE.add(new Bubble(p.entityUuid(), fala, nome, p.x() + 0.5, p.y(), p.z() + 0.5, now));
    }

    /** Desenha os baloes na tela (chamado pelo HUD mixin, depois do resto da HUD). */
    public static void render(GuiGraphicsExtractor g) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        int slot = 0;
        for (Bubble b : LIVE) {
            long age = now - b.born();
            float a = age > LIFE - 900 ? Math.max(0f, (LIFE - age) / 900f) : 1f;
            int alpha = ((int) (a * 255) << 24) | 0x00FFF0C0;

            String line = (b.name() == null || b.name().isEmpty()) ? b.text() : b.name() + ": " + b.text();
            int tw = mc.font.width(line);
            // ancora: centro da tela + a direcao do NPC (projetada na horizontal)
            double dx = b.x() - mc.player.getX(), dz = b.z() - mc.player.getZ();
            double dist = Math.max(0.6, Math.sqrt(dx * dx + dz * dz));
            double yaw = Math.toRadians(mc.player.getYRot());
            double rx = dx * Math.cos(-yaw) - dz * Math.sin(-yaw);     // direita do jogador
            double fz = dx * Math.sin(-yaw) + dz * Math.cos(-yaw);     // frente
            if (fz < 0.2) { slot++; continue; }                         // atras do jogador: nao mostra
            int sx = (int) (w / 2 + (rx / dist) * (w * 0.42));
            int sy = (int) (h * 0.72 - (fz / dist) * 40 - slot * 14);
            int bx = Math.max(4, Math.min(w - tw - 12, sx - tw / 2));
            int by = Math.max(4, sy);
            g.fill(bx - 3, by - 2, bx + tw + 3, by + 10, (int) (a * 190) << 24 | 0x00140C04);
            g.text(mc.font, line, bx, by, alpha, true);
            slot++;
        }
    }
}
