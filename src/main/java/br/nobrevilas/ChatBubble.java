package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        LIVE.removeIf(b -> now - b.born() > LIFE);

        Vec3 cam = ctx.camera().getPosition();
        Matrix4f mat = ctx.matrixStack().last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        for (Bubble b : LIVE) {
            double x = b.x(), y = b.y(), z = b.z();
            // segue o mob enquanto ele existir no mundo
            for (var e : mc.level.entitiesForRendering()) {
                if (e.getUUID().toString().equals(b.uuid())) {
                    x = e.getX(); y = e.getY() + e.getBbHeight() + 0.45; z = e.getZ();
                    break;
                }
            }
            float a = 1f;
            long age = now - b.born();
            if (age > LIFE - 800) a = Math.max(0f, (LIFE - age) / 800f);
            int alpha = (int) (a * 255) << 24;

            String line = (b.name() == null || b.name().isEmpty()) ? b.text() : b.name() + ": " + b.text();
            int w = font.width(line);
            float scale = 0.026f;
            float px = (float) (x - cam.x), py = (float) (y - cam.y) + 0.35f, pz = (float) (z - cam.z);

            ctx.matrixStack().pushPose();
            ctx.matrixStack().translate(px, py, pz);
            ctx.matrixStack().mulPose(ctx.camera().rotation());
            ctx.matrixStack().scale(-scale, -scale, scale);
            mat = ctx.matrixStack().last().pose();

            // fundo do balao (pergaminho) + borda, em dois quads no mundo
            int bw = w + 10, bh = 13;
            float x0 = -bw / 2f, y0 = -bh;
            float y1 = y0 + bh, x1 = x0 + bw;
            var vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.textBackgroundSeeThrough());
            vc.addVertex(mat, x0 - 1, y1 + 1, 0).setColor(0xFFA88A55);
            vc.addVertex(mat, x1 + 1, y1 + 1, 0).setColor(0xFFA88A55);
            vc.addVertex(mat, x1 + 1, y0 - 1, 0).setColor(0xFFA88A55);
            vc.addVertex(mat, x0 - 1, y0 - 1, 0).setColor(0xFFA88A55);
            vc.addVertex(mat, x0, y1, 0).setColor(bg);
            vc.addVertex(mat, x1, y1, 0).setColor(bg);
            vc.addVertex(mat, x1, y0, 0).setColor(bg);
            vc.addVertex(mat, x0, y0, 0).setColor(bg);
            font.drawInBatch(line, x0 + 5, y0 + 3, (alpha & 0xFF000000) | 0x00FFF0C0, false, mat, buf,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ctx.matrixStack().popPose();
        }
        buf.endBatch();
    }
}
