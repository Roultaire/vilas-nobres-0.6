package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Baloes de fala dos NPCs no mundo: aparecem acima da cabeca do mob, seguem ele e somem sozinhos.
 * Nunca abrem tela nem pausam o gameplay.
 */
public final class ChatBubble {
    private record Bubble(String uuid, String text, String name, String color, double x, double y, double z, long born) {}

    private static final List<Bubble> LIVE = new ArrayList<>();
    private static final long LIFE = 4200L;      // ms na tela
    private static final int MAX = 4;

    private ChatBubble() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ChatBubble::draw);
    }

    /** Chamado pelo cliente quando o servidor manda uma fala de fundo. */
    public static void push(NpcIdlePayload p) {
        String[] parts = p.texto().split("\u0001", 3);
        String fala = parts.length > 0 ? parts[0] : p.texto();
        String cor = parts.length > 1 ? parts[1] : "white";
        String nome = parts.length > 2 ? parts[2] : "";
        long now = System.currentTimeMillis();
        LIVE.removeIf(b -> b.uuid().equals(p.entityUuid()) && now - b.born() > 600);
        if (LIVE.size() >= MAX) LIVE.remove(0);
        LIVE.add(new Bubble(p.entityUuid(), fala, nome, cor, p.x() + 0.5, p.y(), p.z() + 0.5, now));
    }

    private static void draw(WorldRenderContext ctx) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        LIVE.removeIf(b -> now - b.born() > LIFE);

        Vec3 cam = ctx.camera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float scale = 0.026f;

        for (Bubble b : LIVE) {
            double x = b.x(), y = b.y(), z = b.z();
            // segue o mob enquanto ele existir no mundo
            for (var e : mc.level.entitiesForRendering()) {
                if (e.getUUID().toString().equals(b.uuid())) {
                    x = e.getX(); y = e.getY() + e.getBbHeight() + 0.55; z = e.getZ();
                    break;
                }
            }
            long age = now - b.born();
            float a = age > LIFE - 800 ? Math.max(0f, (LIFE - age) / 800f) : 1f;
            int alpha = (int) (a * 255) << 24;

            String line = (b.name() == null || b.name().isEmpty()) ? b.text() : b.name() + ": " + b.text();
            float px = (float) (x - cam.x), py = (float) (y - cam.y) + 0.30f, pz = (float) (z - cam.z);

            ctx.matrixStack().pushPose();
            ctx.matrixStack().translate(px, py, pz);
            ctx.matrixStack().mulPose(ctx.camera().rotation());
            ctx.matrixStack().scale(-scale, -scale, scale);
            var mat = ctx.matrixStack().last().pose();
            float w = font.width(line);
            font.drawInBatch(Component.literal(line), -w / 2f, 0f, (alpha & 0xFF000000) | 0x00FFF0C0, false,
                mat, buf, Font.DisplayMode.SEE_THROUGH, (alpha & 0xFF000000), 0xF000F0);
            ctx.matrixStack().popPose();
        }
        buf.endBatch();
    }
}
