package br.nobrevilas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Tela de conversa em estilo medieval rustico (pedra, ouro, estandartes), desenhada so com retangulos e texto:
 * moldura de tijolos com musgo, placa com nome/cargo, retrato em pixel art, "Profissao", caixa de fala com historico
 * e botoes de pedra com icones. Os botoes reais continuam sendo widgets do Minecraft (clique, teclado, narracao);
 * a arte e pintada por cima deles.
 */
public class NpcScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════════════
    //  TEMA DAS TELAS  —  tudo o que você quer mudar fica AQUI (troque os PNGs)
    //
    //  Para cada tipo de tela existe uma "skin": as texturas do painel de fundo
    //  e dos botões (normal, hover, pressionado) e as cores do texto.
    //  Os PNGs ficam em:  src/main/resources/assets/nobrevilas/textures/gui/
    //
    //  Regra de tamanho: o painel é esticado para caber (pode ser fino);
    //  os botões são fatiados em 9 partes (cantos fixos + bordas + centro esticado),
    //  então faça o PNG do botão com o canto já desenhado (ex.: 32x32, canto de 8px).
    // ══════════════════════════════════════════════════════════════════════════
    record Skin(
        Identifier backdrop,     // IMAGEM DE FUNDO da tela inteira (a arte do castelo)
        Identifier panel,        // fundo do painel (esticado)
        Identifier button,       // botão normal (fatiado em 9)
        Identifier buttonHover,  // botão com o mouse em cima
        Identifier buttonDown,   // botão clicado
        Identifier plate,        // placa atrás do nome (esticada na horizontal)
        Identifier paper,        // folha onde vai o texto da fala (esticada)
        int border,              // espessura do canto no PNG do botão/placa (px)
        int text, int textDim, int textTitle   // cores de texto AARRGGBB
    ) {}

    private static Identifier tex(String file) {
        return Identifier.fromNamespaceAndPath(NobreVilas.MOD_ID, "textures/gui/" + file);
    }

    /** Texturas do painel/botões por tipo de tela. Troque o nome do PNG aqui. */
    private static final Skin SKIN_TALK = new Skin(
        tex("bg_talk.png"), tex("panel_talk.png"), tex("button_talk.png"), tex("button_talk_hover.png"), tex("button_talk_down.png"),
        tex("plate.png"), tex("paper.png"), 8, 0xFFFBEFC9, 0xFF5A4A33, 0xFF8A6C12);

    private static final Skin SKIN_GUIDE = new Skin(
        tex("bg_guide.png"), tex("panel_guide.png"), tex("button_guide.png"), tex("button_guide_hover.png"), tex("button_guide_down.png"),
        tex("plate.png"), tex("paper.png"), 8, 0xFFF0E6D2, 0xFF8F8470, 0xFFF2CF5A);

    /** Cores de texto por tipo (troque aqui se quiser texto escuro sobre papel). */
    private static int COL_TEXT = 0xFFFBEFC9, COL_DIM = 0xFFD8C089, COL_TITLE = 0xFFFBEFC9, COL_ACCENT = 0xFFC9A227;

    /** Cor de texto escolhida pelo tipo de tela. */
    private Skin skin() { return kind == Kind.GUIDE ? SKIN_GUIDE : SKIN_TALK; }

    /** Texturas que ja falharam: nao tentamos de novo (evita desenhar lixo na tela). */
    private static final java.util.Set<Identifier> TEX_BAD = new java.util.HashSet<>();
    /** Tamanho real de cada PNG (lido do próprio arquivo) — base do 9-slice e do fundo. */
    private static final java.util.Map<Identifier, Integer> TEX_W = new java.util.HashMap<>();
    private static final java.util.Map<Identifier, Integer> TEX_H = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> AVG = new java.util.HashMap<>();

    /** Textura esticada; se o arquivo nao existir, desliga essa textura e o desenho por cor assume. */
    private void tex(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
        if (id == null || TEX_BAD.contains(id)) return;
        registerSize(id);
        try { g.blit(id, x, y, w, h, 0f, 0f, 1f, 1f); }
        catch (Throwable t) { TEX_BAD.add(id); NobreVilas.LOG.warn("Textura ausente: {}", id); }
    }

    /**
     * Fatiamento em 9 partes: os cantos ficam do tamanho do PNG, as bordas esticam num sentido
     * e o centro estica nos dois. Serve para paineis e botoes de qualquer tamanho.
     */
    /**
     * 9-slice com recorte de verdade: o canto é a fatia real do PNG, e as bordas/centro se
     * repetem (nunca esticam), então o desenho fica nítido em qualquer tamanho de painel.
     */
    private void nine(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int b) {
        if (id == null || TEX_BAD.contains(id)) return;
        registerSize(id);
        int iw = TEX_W.getOrDefault(id, 32), ih = TEX_H.getOrDefault(id, 32);
        if (b <= 0 || w < 2 * b || h < 2 * b || iw < 2 * b || ih < 2 * b) { tex(g, id, x, y, w, h); return; }
        float u0 = b / (float) iw, v0 = b / (float) ih;
        float u1 = (iw - b) / (float) iw, v1 = (ih - b) / (float) ih;
        // cantos (fatia exata)
        slice(g, id, x, y, b, b, 0f, 0f, u0, v0);
        slice(g, id, x + w - b, y, b, b, u1, 0f, 1f, v0);
        slice(g, id, x, y + h - b, b, b, 0f, v1, u0, 1f);
        slice(g, id, x + w - b, y + h - b, b, b, u1, v1, 1f, 1f);
        // bordas: repetem em blocos de b
        for (int cx = x + b; cx < x + w - b; cx += b) {
            int cw = Math.min(b, x + w - b - cx);
            float uu = u0 + (u1 - u0) * (cw / (float) b);
            slice(g, id, cx, y, cw, b, u0, 0f, uu, v0);
            slice(g, id, cx, y + h - b, cw, b, u0, v1, uu, 1f);
        }
        for (int cy = y + b; cy < y + h - b; cy += b) {
            int ch = Math.min(b, y + h - b - cy);
            float vv = v0 + (v1 - v0) * (ch / (float) b);
            slice(g, id, x, cy, b, ch, 0f, v0, u0, vv);
            slice(g, id, x + w - b, cy, b, ch, u1, v0, 1f, vv);
        }
        // centro repetido
        for (int cx = x + b; cx < x + w - b; cx += b) {
            int cw = Math.min(b, x + w - b - cx);
            float uu = u0 + (u1 - u0) * (cw / (float) b);
            for (int cy = y + b; cy < y + h - b; cy += b) {
                int ch = Math.min(b, y + h - b - cy);
                float vv = v0 + (v1 - v0) * (ch / (float) b);
                slice(g, id, cx, cy, cw, ch, u0, v0, uu, vv);
            }
        }
    }

    /** Le a largura/altura real do PNG (uma vez por textura) para o 9-slice e o fundo. */
    private static void registerSize(Identifier id) {
        if (TEX_W.containsKey(id)) return;
        try {
            var res = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png"));
            try (var in = res.orElseThrow().open()) {
                byte[] b = in.readAllBytes();                    // PNG: largura/altura no cabecalho IHDR
                int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
                int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
                TEX_W.put(id, w);
                TEX_H.put(id, h);
            }
        } catch (Throwable t) { TEX_W.put(id, 32); TEX_H.put(id, 32); }
    }

    /** Desenha uma fatia do PNG em coordenadas UV 0..1. */
    private void slice(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, float u0, float v0, float u1, float v1) {
        if (w <= 0 || h <= 0) return;
        // 26.3: blit(pipeline, textura, x, y, u, v, destinoW, destinoH, origemW, origemH, texW, texH)
        int tw = TEX_W.getOrDefault(id, 32), th = TEX_H.getOrDefault(id, 32);
        int srcW = Math.max(1, Math.round((u1 - u0) * tw)), srcH = Math.max(1, Math.round((v1 - v0) * th));
        int srcX = Math.round(u0 * tw), srcY = Math.round(v0 * th);
        try {
            // pinta o pedaco do PNG tal qual (nunca esticado)
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, (float) srcX, (float) srcY, srcW, srcH, srcW, srcH, tw, th);
            if (w != srcW || h != srcH) {                      // completa a sobra com o mesmo pixel
                int c = avgColor(id, srcX, srcY, srcW, srcH);
                g.fill(x, y, x + w, y + h, c);
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, (float) srcX, (float) srcY, srcW, srcH, srcW, srcH, tw, th);
            }
        } catch (Throwable t) { TEX_BAD.add(id); NobreVilas.LOG.warn("Textura ausente: {}", id); }
    }

    /** Cor media de um pedaco do PNG (para completar sobras sem esticar). */
    private static int avgColor(Identifier id, int sx, int sy, int sw, int sh) {
        String k = id + "@" + sx + "," + sy + "," + sw + "," + sh;
        Integer got = AVG.get(k);
        if (got != null) return got;
        int col = 0xFF8A8A8A;
        try {
            var res = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png"));
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(res.orElseThrow().open());
            long r = 0, gg = 0, b = 0, n = 0;
            for (int y = sy; y < Math.min(sy + sh, img.getHeight()); y += 2)
                for (int x = sx; x < Math.min(sx + sw, img.getWidth()); x += 2) {
                    int px = img.getRGB(x, y);
                    if (((px >> 24) & 0xFF) < 8) continue;
                    r += (px >> 16) & 0xFF; gg += (px >> 8) & 0xFF; b += px & 0xFF; n++;
                }
            if (n > 0) col = 0xFF000000 | ((int) (r / n) << 16) | ((int) (gg / n) << 8) | (int) (b / n);
        } catch (Throwable ignored) { }
        AVG.put(k, col);
        return col;
    }

    private enum Kind { GUIDE, ROYAL, NOBLE, GUARD, TRADER, VILLAGER }



    // ───────────── paleta ─────────────
    private static final int MORTAR = 0xFF2B2720, DARK = 0xFF17130E, GOLD = 0xFFD4A21F, GOLD_D = 0xFF8C6A12, GOLD_L = 0xFFF2CF5A,
        RED = 0xFF9E2222, RED_D = 0xFF661414, TEXT = 0xFFF0E6D2, DIM = 0xFF8F8470, MOSS = 0xFF4B6B2F, SHADOW = 0x66000000;
    private static final int[] STONES = {0xFF6E675B, 0xFF625B50, 0xFF776F62, 0xFF5A544A};
    private static final int[] PAL = new int[128];
    static {
        PAL['G'] = 0xFFE6B422; PAL['g'] = 0xFF9A7410; PAL['R'] = 0xFFB02828; PAL['r'] = 0xFF6B1414;
        PAL['S'] = 0xFFB9B3A6; PAL['s'] = 0xFF7A7468; PAL['K'] = 0xFF1A140C; PAL['P'] = 0xFFEBDDB4; PAL['p'] = 0xFFBFA974;
        PAL['B'] = 0xFF7A4A21; PAL['N'] = 0xFFC38A62; PAL['n'] = 0xFF8F5D3C; PAL['H'] = 0xFF3F2616; PAL['W'] = 0xFFF4F4F4;
        PAL['E'] = 0xFF2FA33A; PAL['A'] = 0xFF4CAF50; PAL['U'] = 0xFF2E5FA3; PAL['u'] = 0xFF1E3F73; PAL['M'] = 0xFF6A2C91;
        PAL['C'] = 0xFF7FD4E8; PAL['L'] = 0xFF3A2C1A; PAL['V'] = 0xFFB03A2E; PAL['X'] = 0xFFD8D2C4; PAL['T'] = 0xFF56606B;
    }

    // ───────────── icones (pixel art) ─────────────
    private static final String[] CROWN = {"G..G..G", "GG.G.GG", "GGGGGGG", "GRGGGRG", "ggggggg"};
    private static final String[] BUBBLE = {".PPPPPP.", "PPPPPPPP", "PpPpPpPP", "PPPPPPPP", ".PPPPPP.", "..PP....", "..P....."};
    private static final String[] SCROLL = {"BBBBBBB", ".PPPPP.", ".PpppP.", ".PPPPP.", ".PpppP.", ".PPPPP.", "BBBBBBB"};
    private static final String[] HAND = {"RRRNNUUU", "RRNNNNUU", "RRNnnNUU", ".NNNNNN.", "..NnnN.."};
    private static final String[] ARROW = {"...A...", "..AA...", ".AAAAAA", "AAAAAAA", ".AAAAAA", "..AA...", "...A..."};
    private static final String[] CASTLE = {"S.S.S.S", "SSSSSSS", "SsSsSsS", "SSSKSSS", "SSKKKSS", "SSKKKSS"};
    private static final String[] TROPHY = {"GGGGGGG", "GGGGGGG", ".GGGGG.", "..GGG..", "...g...", "..ggg.."};
    private static final String[] BOOK = {"BBBBBBBBBBB", "BPPPPBPPPPB", "BPpppBpppPB", "BPPPPBPPPPB", "BPpppBpppPB", "BPPPPBPPPPB", "BBBBBBBBBBB"};
    // icones do guia (pixel art, 9x9)
    private static final String[] I_CROWN = {"G...G...G", "GG.GGG.GG", "GGGGGGGGG", "GgGGGGGgG", "GGGGGGGGG", ".GGGGGGG.", "..GgGgG..", "..GGGGG..", "...ggg..."};
    private static final String[] I_TOWER = {"SSSSSSSSS", "SsSsSsSsS", "SSSSSSSSS", ".SSSSSSS.", ".SSKSSSS.", ".SSSSKSS.", ".SSKSSSS.", ".SSSSKSS.", ".SSSSSSS."};
    private static final String[] I_SWORD = {"......GGS", ".....GGS.", "....GGS..", "...GGS...", "..GGS....", ".SGGS....", "SGG.S....", "GG.......", "G........"};
    private static final String[] I_COIN  = {".GGGGGGG.", "GGgGGGgGG", "GgGgGgGgG", "GGgGGGgGG", "GGGgGgGGG", "GGgGGGgGG", "GgGgGgGgG", "GGgGGGgGG", ".GGGGGGG."};
    private static final String[] I_SCROLL= {"LLLLLLLLL", "LXXXXXXXL", "LXxxxxxXL", "LXXXXXXXL", "LXxxxxxXL", "LXXXXXXXL", "LXxxxxxXL", "LXXXXXXXL", "LLLLLLLLL"};
    private static final String[] I_FLAG  = {"LLLLLLLLL", "LVVVVVVV.", "LVVVVVVV.", "LVVVVGVVV", "LVVVVVVV.", "LVVVVVVV.", "LL.......", "LL.......", "LL......."};

    private static final List<String> HIST = new ArrayList<>();
    private static String lastKey = "";
    private static long lastTime;

    private String name, role, title, body;
    private String entityUuid;
    private List<String[]> opts = new ArrayList<>();
    private List<String> hist = new ArrayList<>();
    private Kind kind;

    // layout
    private List<String> histLines = new ArrayList<>(), bodyLines = new ArrayList<>();
    private final List<int[]> optRects = new ArrayList<>();
    /** true depois de clicar numa opcao: a tela so fecha quando o servidor mandar (nunca sozinha). */
    private boolean holding;
    private int bx, by2, bwid, bhei, leftX, rightX, pageWid, pageNo;
    private List<String> titleLines = new ArrayList<>();
    private int px, py, pw, ph, panelTop, titleY, bodyY, boxH, tbX, tbW, lh, closeIdx;
    private int portraitW;
    private String icon = "";
    private boolean hasIcon;
    private boolean clicking;
    private EditBox nameBox;

    public NpcScreen(NpcTalkPayload p) {
        this(p, p.entityUuid());
    }

    public NpcScreen(NpcTalkPayload p, String entity) {
        super(Component.literal("Conversa"));
        apply(p, entity);
    }

    /** true se a tela ja esta mostrando este NPC */
    public boolean sameEntity(String uuid) {
        return uuid != null && !uuid.isEmpty() && uuid.equals(entityUuid);
    }

    /** Troca o conteudo sem fechar a tela (navegacao entre opcoes / NPC que continua falando). */
    public void update(NpcTalkPayload p) {
        String novo = NpcTalkPayload.KEEP.equals(p.entityUuid()) || p.entityUuid() == null || p.entityUuid().isEmpty()
            ? entityUuid : p.entityUuid();
        if (sameEntity(novo)) applySoft(p);          // mesma tela: so o texto e as opcoes mudam
        else apply(p, novo);                          // outro NPC: ai sim a tela se refaz por dentro
    }

    /** Atualiza fala/botoes sem destruir a tela (o jogador nao sai da conversa). */
    private void applySoft(NpcTalkPayload p) {
        String[] h = p.head().split("\u0001", -1);
        String novoNome = h[0];
        if (!novoNome.equals(name)) { apply(p, entityUuid); return; }   // trocou de NPC: refaz
        body = p.body();
        title = h.length > 3 ? h[3] : "";
        opts.clear();
        for (String e : p.options().split("\u0002", -1)) {
            if (e.isEmpty()) continue;
            String[] f = e.split("\u0001", -1);
            opts.add(new String[]{f[0], f.length > 1 ? f[1] : ""});
        }
        String entry = (title.isEmpty() || title.equals(name) ? "" : title + " — ") + body.replace("\n", " ");
        if (entry.length() > 140) entry = entry.substring(0, 137) + "...";
        HIST.add(entry);
        while (HIST.size() > 3) HIST.remove(0);
        hist = new ArrayList<>(HIST);
        holding = false;              // resposta chegou: a tela volta a poder fechar
        init();                       // recalcula posicoes e botoes na mesma tela
    }

    private void apply(NpcTalkPayload p, String entity) {
        String[] h = p.head().split("\u0001", -1);
        name = h[0];
        role = h.length > 1 ? h[1] : "";
        title = h.length > 3 ? h[3] : "";
        body = p.body();
        icon = h.length > 5 ? h[5] : "";
        entityUuid = entity == null ? "" : entity;
        opts.clear();
        for (String e : p.options().split("\u0002", -1)) {
            if (e.isEmpty()) continue;
            String[] f = e.split("\u0001", -1);
            opts.add(new String[]{f[0], f.length > 1 ? f[1] : ""});
        }
        kind = detectKind();
        long now = System.currentTimeMillis();
        if (!name.equals(lastKey) || now - lastTime > 120_000) HIST.clear();
        lastKey = name;
        lastTime = now;
        hist = new ArrayList<>(HIST);
        String entry = (title.isEmpty() || title.equals(name) ? "" : title + " — ") + body.replace("\n", " ");
        if (entry.length() > 140) entry = entry.substring(0, 137) + "...";
        HIST.add(entry);
        while (HIST.size() > 3) HIST.remove(0);
        init();
    }

    private Kind detectKind() {
        String n = name.toLowerCase(), r = role.toLowerCase();
        if (n.startsWith("guia")) return Kind.GUIDE;
        if (n.startsWith("soldado") || r.startsWith("guarda")) return Kind.GUARD;
        if (n.startsWith("mercador") || r.startsWith("comerciante")) return Kind.TRADER;
        if (n.startsWith("aldeão") || n.startsWith("aldeao") || r.startsWith("habitante")) return Kind.VILLAGER;
        for (String t : new String[]{"rei", "rainha", "imperador", "imperatriz", "príncipe", "princesa", "duque", "duquesa", "arquiduque", "arquiduquesa"})
            if (r.startsWith(t + " ")) return Kind.ROYAL;
        return Kind.NOBLE;
    }

    private String profession() {
        return switch (kind) {
            case ROYAL -> "Realeza";
            case NOBLE -> "Nobreza";
            case GUARD -> "Guarda";
            case TRADER -> "Comércio";
            default -> "Aldeão";
        };
    }

    // ───────────── utilitarios ─────────────
    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (this.font.width(t) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(t);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private String fit(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && this.font.width(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }

    private boolean showTitle() { return !title.isEmpty() && !title.equals(name); }

    // ───────────── layout ─────────────
    @Override
    protected void init() {
        optRects.clear();
        lh = this.font.lineHeight + 2;
        if (kind == Kind.GUIDE) { initBook(); return; }
        int t = 8;
        // largura: aproveita a tela, mas nunca passa de 560 (janela compacta)
        pw = Math.min(560, this.width - 24);
        px = (this.width - pw) / 2;
        portraitW = kind == Kind.GUIDE ? 0 : 64;
        hasIcon = !icon.isEmpty();

        int pad = 10;
        int frameW = portraitW > 0 ? portraitW + pad * 2 : 0;     // quadro do retrato
        tbX = px + frameW + pad;
        tbW = px + pw - pad - tbX;

        // coluna da direita: opcoes (dentro da mesma janela, como na referencia)
        int nBtn = opts.size() + 1;
        int btnH = 18, btnGap = 2;
        int optW = Math.min(230, Math.max(150, tbW / 2));
        int optX = px + pw - pad - optW;

        // texto da fala ocupa a coluna esquerda
        int textW = (optX - tbX) - 12;
        histLines = new ArrayList<>();
        for (String s : hist) histLines.addAll(wrap("› " + s, textW));
        while (histLines.size() > 3) histLines.remove(0);
        bodyLines = wrap(body, textW);

        int txtH = 22 + (histLines.isEmpty() ? 0 : histLines.size() * lh + 4) + (showTitle() ? lh + 2 : 0) + bodyLines.size() * lh;
        int optH = nBtn * (btnH + btnGap);
        int contentH = Math.max(txtH, optH + 18);
        contentH = Math.min(contentH, Math.max(80, this.height - 90));

        int frameH = Math.max(contentH + pad * 2 + 6, portraitW > 0 ? 88 : 0);
        ph = frameH + pad * 2;
        panelTop = this.height - ph - 14;                 // janela embaixo
        titleY = panelTop + pad + 2;
        bodyY = titleY + this.font.lineHeight + 8;

        int by = panelTop + pad + 20;
        for (String[] o : opts) {
            final String cmd = o[1];
            this.addRenderableWidget(Button.builder(Component.literal(o[0]), b -> click(cmd)).bounds(optX, by, optW, btnH).build());
            optRects.add(new int[]{optX, by, optW, btnH});
            by += btnH + btnGap;
        }
        closeIdx = optRects.size();
        this.addRenderableWidget(Button.builder(Component.literal("Encerrar conversa"), b -> this.onClose()).bounds(optX, by, optW, btnH).build());
        optRects.add(new int[]{optX, by, optW, btnH});

        boolean needBox = false;
        for (String[] o : opts) if (o[1].contains("$(nome)")) needBox = true;
        if (needBox) {
            nameBox = new EditBox(this.font, tbX, panelTop - 26, pw - pad * 2, 20, Component.literal("Nome do reino"));
            nameBox.setMaxLength(24);
            nameBox.setHint(Component.literal("Nome do novo reino"));
            this.addRenderableWidget(nameBox);
        }
    }


    // ───────────── LIVRO DO GUIA ─────────────
    /** Layout do livro: duas páginas lado a lado, com setas e numeração. */
    private void initBook() {
        int bw = Math.min(560, this.width - 40);
        int bh = Math.min(320, this.height - 60);
        bx = (this.width - bw) / 2;
        by2 = (this.height - bh) / 2;
        bwid = bw;
        bhei = bh;
        int pageW = (bw - 28) / 2;
        leftX = bx + 14;
        rightX = bx + 14 + pageW + 8;
        pageWid = pageW - 8;
        int tby = by2 + bh - 34;
        // setas de virar pagina + fechar
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1)).bounds(bx + 6, tby, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1)).bounds(bx + bw - 26, tby, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> { holding = false; this.onClose(); }).bounds(bx + bw / 2 - 30, tby, 60, 20).build());
        // categorias da pagina esquerda
        int cy = by2 + 40;
        int ch = 20;
        for (String[] o : opts) {
            final String cmd = o[1];
            this.addRenderableWidget(Button.builder(Component.literal(o[0]), b -> click(cmd)).bounds(leftX + 6, cy, pageWid - 4, ch).build());
            optRects.add(new int[]{leftX + 6, cy, pageWid - 4, ch});
            cy += ch + 2;
        }
        closeIdx = -1;
        bodyLines = wrap(body, pageWid - 16);
        titleLines = wrap(title, pageWid - 16);
    }

    private void page(int d) { pageNo = Math.max(0, pageNo + d); }

    @Override
    public void onClose() {
        if (holding) return;          // esperando a resposta do NPC: nao fecha
        super.onClose();
    }

    private void click(String cmd) {
        String c = cmd;
        if (c.contains("$(nome)")) {
            String v = nameBox == null ? "" : nameBox.getValue().trim();
            if (v.length() < 3) return;
            c = c.replace("$(nome)", v);
        }
        if (this.minecraft == null || this.minecraft.player == null) return;
        // segura a tela: opcao de conversa nunca fecha a tela nem deixa cair no guia
        holding = true;
        this.minecraft.player.connection.sendCommand(c);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ───────────── livro aberto de fundo ─────────────
    private static final int PARCH = 0xFFE8D5A9, PARCH_D = 0xFFCBB077, PARCH_S = 0xFFA88A55,
        LEATHER = 0xFF6B3F1D, LEATHER_D = 0xFF4A2A11, RIBBON = 0xFF8E1F1F;
    // ── tema do DIALOGO (madeira e pergaminho; o guia e que usa o livro) ──
    private static final int W_OAK = 0xFF8A6134, W_OAK_D = 0xFF5D3F1F, W_OAK_L = 0xFFA87B47,
        W_GRAIN = 0xFF6E4B26, BRASS = 0xFFC9A227, BRASS_D = 0xFF8A6C12,
        PAPER = 0xFFF3E7C8, PAPER_D = 0xFFDCC9A0, INK = 0xFF2A2115, INK_DIM = 0xFF5A4A33;

    /** Livro aberto atras da conversa: capa, duas paginas de pergaminho, lombada e fitas. */
    private void drawOpenBook(GuiGraphicsExtractor g) {
        int bw = Math.min(this.width - 24, 560);
        int bh = Math.min(this.height - 16, 340);
        int bx = (this.width - bw) / 2, by = (this.height - bh) / 2;
        int half = bw / 2, spine = 10;
        // capa
        g.fill(bx - 6, by - 6, bx + bw + 6, by + bh + 6, LEATHER_D);
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, LEATHER);
        g.outline(bx - 3, by - 3, bw + 6, bh + 6, 0xFF2A1708);
        // paginas
        g.fill(bx, by, bx + half - spine / 2, by + bh, PARCH);
        g.fill(bx + half + spine / 2, by, bx + bw, by + bh, PARCH);
        g.outline(bx, by, half - spine / 2, bh, PARCH_S);
        g.outline(bx + half + spine / 2, by, bw - half - spine / 2, bh, PARCH_S);
        // linhas de pauta nas bordas (disfarcadas de texto antigo)
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? bx + 10 : bx + half + spine / 2 + 10;
            int x1 = side == 0 ? bx + half - spine / 2 - 10 : bx + bw - 10;
            for (int y = by + 14; y < by + bh - 12; y += 9) {
                if ((y / 9) % 5 == 0) continue;
                g.fill(x0, y, x0 + (x1 - x0) * 2 / 3, y + 1, PARCH_D);
            }
        }
        // lombada e dobra central
        g.fill(bx + half - spine / 2, by, bx + half + spine / 2, by + bh, PARCH_D);
        g.fill(bx + half - 1, by, bx + half + 1, by + bh, PARCH_S);
        // fitas marcadoras
        g.fill(bx + half - 14, by - 3, bx + half - 8, by + 26, RIBBON);
        g.fill(bx + half + 8, by + bh - 26, bx + half + 14, by + bh + 3, RIBBON);
    }

    /** Arte de fundo: cobre a tela inteira mantendo a proporção (corta o excedente). */
    private void drawBackdrop(GuiGraphicsExtractor g) {
        Identifier bg = skin().backdrop();
        if (bg != null && !TEX_BAD.contains(bg)) {
            registerSize(bg);
            int before = TEX_BAD.size();
            int srcW = TEX_W.getOrDefault(bg, 576), srcH = TEX_H.getOrDefault(bg, 324);
            float scale = Math.max(this.width / (float) srcW, this.height / (float) srcH);
            int dw = Math.round(srcW * scale), dh = Math.round(srcH * scale);
            slice(g, bg, (this.width - dw) / 2, (this.height - dh) / 2, dw, dh, 0f, 0f, 1f, 1f);
            if (TEX_BAD.size() == before) {                     // desenhou a arte
                g.fill(0, 0, this.width, this.height, kind == Kind.GUIDE ? 0x44000000 : 0x33000000);
                return;
            }
        }
        // fallback: noite com brilho no centro (nunca fica chapado)
        for (int y = 0; y < this.height; y += 4) {
            int k = 255 - (int) (Math.abs(y - this.height / 2) * 120.0 / Math.max(1, this.height / 2));
            g.fill(0, y, this.width, Math.min(this.height, y + 4), 0xFF000000 | (k / 6 << 16) | (k / 5 << 8) | (k / 3));
        }
    }

    /** Painel do DIALOGO: tabua de madeira com rebites, pergaminho da fala e fita de pergaminho nos botoes. */
    private void drawTalkPanel(GuiGraphicsExtractor g) {
        Skin sk = skin();
        // base de cor (aparece se o PNG ainda nao existir)
        g.fill(px - 4, panelTop - 4, px + pw + 4, panelTop + ph + 4, W_OAK_D);
        g.fill(px, panelTop, px + pw, panelTop + ph, kind == Kind.GUIDE ? 0xFF3A3128 : W_OAK);
        // painel e folha de texto por textura
        nine(g, sk.panel(), px, panelTop, pw, ph, Math.min(12, Math.max(4, sk.border())));
        int sheetX = tbX - 2, sheetY = bodyY - 2;
        tex(g, sk.paper(), sheetX, sheetY, tbW + 4, boxH + 4);
        // placa do nome por textura
        int plateWid = Math.max(120, Math.min(256, pw - 32));
        nine(g, sk.plate(), px + pw / 2 - plateWid / 2, panelTop + 4, plateWid, 32, sk.border());
        // veio da madeira e rebites (opcionais: so aparecem se nao houver textura de painel)
        if (sk.panel() == null) {
            int t = 8;
            for (int y = panelTop + 3; y < panelTop + ph - 2; y += 6) g.fill(px + 2, y, px + pw - 2, y + 1, W_GRAIN);
            g.outline(px, panelTop, pw, ph, W_OAK_D);
            g.outline(px + 2, panelTop + 2, pw - 4, ph - 4, W_OAK_L);
            for (int[] c : new int[][]{{px + 5, panelTop + 5}, {px + pw - 9, panelTop + 5}, {px + 5, panelTop + ph - 9}, {px + pw - 9, panelTop + ph - 9}}) {
                g.fill(c[0], c[1], c[0] + 4, c[1] + 4, BRASS);
                g.fill(c[0] + 1, c[1] + 1, c[0] + 3, c[1] + 3, BRASS_D);
            }
        }
    }

    // ───────────── desenho ─────────────
    /** Pixel art do icone pedido pelo servidor (guia). */
    private String[] iconArt() {
        return switch (icon) {
            case "coroa" -> I_CROWN;
            case "torre" -> I_TOWER;
            case "espada" -> I_SWORD;
            case "moeda" -> I_COIN;
            case "pergaminho" -> I_SCROLL;
            case "estandarte" -> I_FLAG;
            default -> BOOK;
        };
    }

    private void art(GuiGraphicsExtractor g, String[] rows, int x, int y, int s) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch == '.' || ch >= 128 || PAL[ch] == 0) continue;
                g.fill(x + c * s, y + r * s, x + (c + 1) * s, y + (r + 1) * s, PAL[ch]);
            }
        }
    }

    private void stone(GuiGraphicsExtractor g, int x, int y, int w, int h, int bh, int bw, int seed) {
        g.fill(x, y, x + w, y + h, MORTAR);
        int row = 0;
        for (int yy = y; yy < y + h; yy += bh, row++) {
            int off = (row % 2 == 0) ? 0 : bw / 2;
            for (int xx = x - off; xx < x + w; xx += bw) {
                int x1 = Math.max(xx + 1, x), x2 = Math.min(xx + bw, x + w), y1 = yy + 1, y2 = Math.min(yy + bh, y + h);
                if (x2 <= x1 || y2 <= y1) continue;
                g.fill(x1, y1, x2, y2, STONES[hash(xx, yy, seed) & 3]);
            }
        }
    }

    private void goldBox(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DARK);
        g.outline(x, y, w, h, GOLD_D);
        g.outline(x + 1, y + 1, w - 2, h - 2, GOLD);
    }

    private void banner(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean castle) {
        int hb = h - w / 2;
        g.fill(x, y, x + w, y + hb, RED);
        g.fill(x, y, x + 2, y + hb, RED_D);
        g.fill(x + w - 2, y, x + w, y + hb, RED_D);
        for (int i = 0; i < w / 2; i++) g.fill(x + i, y + hb + i, x + w - i, y + hb + i + 1, i < 2 ? RED_D : RED);
        g.fill(x - 1, y, x + w + 1, y + 3, GOLD);
        g.fill(x - 1, y + 3, x + w + 1, y + 4, GOLD_D);
        art(g, castle ? CASTLE : CROWN, x + (w - 14) / 2, y + 9, 2);
    }

    private String[] iconFor(String label, boolean isClose) {
        String l = label.toLowerCase();
        if (isClose || l.contains("voltar") || l.contains("adeus") || l.contains("encerrar") || l.contains("‹")) return ARROW;
        if (l.contains("alian") || l.contains("aliar") || l.contains("pact")) return HAND;
        if (l.contains("missão") || l.contains("missao") || l.contains("status") || l.contains("posto") || l.contains("comando") || l.contains("imposto")) return SCROLL;
        if (l.contains("reino") || l.contains("coroa") || l.contains("proclam") || l.contains("rebel") || l.contains("rebeli") || l.contains("romper")) return CROWN;
        if (l.contains("desafi") || l.contains("conquist")) return TROPHY;
        if (l.contains("muralha") || l.contains("capital") || l.contains("vila") || l.contains("vizinh") || l.contains("castelo") || l.contains("territ") || l.contains("o que é")) return CASTLE;
        return BUBBLE;
    }

    // ───────────── retrato 3D do NPC (modelo do mob) ─────────────
    /** Desenha o modelo 3D da entidade dentro do quadro do retrato (mesmo pipeline do inventario). */
    @SuppressWarnings("unchecked")
    private void drawEntity3D(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size) {
        if (this.minecraft == null || this.minecraft.level == null) return;
        Entity e = null;
        if (entityUuid != null && !entityUuid.isEmpty()) {
            try { e = this.minecraft.level.getEntity(UUID.fromString(entityUuid)); } catch (IllegalArgumentException ignored) {}
        }
        if (e == null && this.minecraft.player != null) {
            EntityHitResult hit = null;
            if (this.minecraft.hitResult instanceof EntityHitResult h) hit = h;
            if (hit != null) e = hit.getEntity();
        }
        if (!(e instanceof LivingEntity living)) return;
        try {
            EntityRenderer<LivingEntity, EntityRenderState> renderer =
                (EntityRenderer<LivingEntity, EntityRenderState>) this.minecraft.getEntityRenderDispatcher().getRenderer(living);
            EntityRenderState state = renderer.createRenderState(living, 1.0F);
            if (state instanceof LivingEntityRenderState lrs) {
                lrs.bodyRot = 180.0F + lrs.yRot;
                lrs.xRot = 0.0F;
                if (lrs.scale > 0.0F) {
                    lrs.boundingBoxWidth /= lrs.scale;
                    lrs.boundingBoxHeight /= lrs.scale;
                }
                lrs.scale = 1.0F;
            }
            Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf tilt = new Quaternionf().rotateX(20.0F * 0.017453292F);
            rot.mul(tilt);
            Vector3f translate = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F, 0.0F);
            g.entity(state, (float) size, translate, rot, tilt, x0, y0, x1, y1);
        } catch (Exception ex) {
            // se o modelo nao puder ser desenhado, fica o retrato em pixel art
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // imagem de fundo da tela inteira (arte do castelo) + escurecimento para o texto respirar
        if (kind == Kind.GUIDE) {
            drawBackdrop(g);
            g.fill(0, 0, this.width, this.height, 0x99000000);
            super.extractRenderState(g, mouseX, mouseY, delta);
            drawBook(g, mouseX, mouseY);
            return;
        } else {
            g.fill(0, 0, this.width, this.height, 0x44000000);   // dialogo: so escurece o mundo
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        int t = 8;
        int cx = px + pw / 2;

        drawTalkBody(g, mouseX, mouseY);
    }


    /** Livro aberto: capa, duas páginas de pergaminho, lombada, cantos e marcadores. */
    private void drawBook(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cx = bx + bwid / 2;
        // sombra
        g.fill(bx + 6, by2 + 8, bx + bwid + 6, by2 + bhei + 8, 0x66000000);
        // capa de couro
        g.fill(bx, by2, bx + bwid, by2 + bhei, 0xFF4A2C18);
        g.outline(bx, by2, bwid, bhei, 0xFF2A1709);
        g.fill(bx + 2, by2 + 2, bx + bwid - 2, by2 + bhei - 2, 0xFF6B4023);
        // cantos de metal
        for (int[] c : new int[][]{{bx + 2, by2 + 2}, {bx + bwid - 10, by2 + 2}, {bx + 2, by2 + bhei - 10}, {bx + bwid - 10, by2 + bhei - 10}}) {
            g.fill(c[0], c[1], c[0] + 8, c[1] + 8, 0xFF8C6A12);
            g.fill(c[0] + 2, c[1] + 2, c[0] + 6, c[1] + 6, 0xFFD4A21F);
        }
        // páginas de pergaminho
        int py0 = by2 + 8, ph0 = bhei - 16;
        int lw = (bwid - 40) / 2;
        int lx0 = bx + 14, rx0 = bx + bwid - 14 - lw;
        g.fill(lx0, py0, lx0 + lw, py0 + ph0, 0xFFE8D7AE);
        g.fill(rx0, py0, rx0 + lw, py0 + ph0, 0xFFE8D7AE);
        // manchas de idade
        for (int i = 0; i < 14; i++) {
            int sx = lx0 + (hash(i, 3, 7) % Math.max(1, lw - 8));
            int sy = py0 + (hash(i, 9, 5) % Math.max(1, ph0 - 8));
            g.fill(sx, sy, sx + 3, sy + 2, 0x22A8894F);
        }
        // lombada central
        g.fill(cx - 5, py0 - 4, cx + 5, py0 + ph0 + 4, 0xFF8C6A12);
        g.fill(cx - 1, py0 - 4, cx + 1, py0 + ph0 + 4, 0xFF5A4410);
        // marcador de fita
        g.fill(cx - 14, py0 - 8, cx - 8, py0 + 40, 0xFF9E2222);

        // título da página direita
        int ty = py0 + 8;
        for (String l : titleLines) { g.text(this.font, l, rx0 + 10, ty, 0xFF5A3A10, false); ty += lh; }
        g.fill(rx0 + 10, ty + 2, rx0 + lw - 10, ty + 3, 0xFFB79A4E);
        // conteúdo
        int cy = ty + 8;
        for (String l : bodyLines) {
            if (cy > py0 + ph0 - 14) break;
            g.text(this.font, l, rx0 + 10, cy, 0xFF2E2418, false);
            cy += lh;
        }
        // índice na página esquerda
        int iy = py0 + 8;
        g.text(this.font, "Índice", lx0 + 10, iy, 0xFF5A3A10, false);
        iy += lh + 2;
        g.fill(lx0 + 10, iy, lx0 + lw - 10, iy + 1, 0xFFB79A4E);
        iy += 6;
        for (int i = 0; i < optRects.size(); i++) {
            int[] b = optRects.get(i);
            boolean hover = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            String lab = fit(opts.get(i)[0], lw - 30);
            int ly2 = b[1] + (b[3] - this.font.lineHeight) / 2;
            if (hover) {
                g.fill(lx0 + 6, b[1], lx0 + lw - 6, b[1] + b[3], 0x33B79A4E);
                g.text(this.font, "»", lx0 + 8, ly2, 0xFF7A5210, false);
            }
            g.text(this.font, lab, lx0 + 20, ly2, hover ? 0xFF3A1E08 : 0xFF4A3A24, false);
        }
        // numeração e rodapé
        String pg = (pageNo * 2 + 1) + " · " + (pageNo * 2 + 2);
        g.text(this.font, pg, cx - this.font.width(pg) / 2, by2 + bhei - 20, 0xFFD8C089, false);
    }

    private void drawTalkBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int pad = 10, t = 8;
        int innerX = px, innerY = panelTop, innerW = pw, innerH = ph;
        // janela cinza-claro do Minecraft com borda dupla (referencia)
        g.fill(innerX, innerY, innerX + innerW, innerY + innerH, 0xFFC6C6C6);
        g.fill(innerX + 1, innerY + 1, innerX + innerW - 1, innerY + innerH - 1, 0xFFDDDDDD);
        g.outline(innerX, innerY, innerW, innerH, 0xFF3A3A3A);
        g.outline(innerX + 2, innerY + 2, innerW - 4, innerH - 4, 0xFF8A8A8A);
        // cantos em metal envelhecido
        for (int[] c : new int[][]{{innerX + 1, innerY + 1}, {innerX + innerW - 7, innerY + 1},
                                   {innerX + 1, innerY + innerH - 7}, {innerX + innerW - 7, innerY + innerH - 7}}) {
            g.fill(c[0], c[1], c[0] + 6, c[1] + 6, 0xFF6E5A2E);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 5, c[1] + 5, 0xFFB79A4E);
        }

        // retrato do NPC a esquerda, dentro de um quadro
        if (portraitW > 0) {
            int fw = portraitW + pad * 2 - 4;
            int fx = innerX + 4, fy = innerY + 4;
            int fh = Math.min(innerH - 8, Math.max(88, innerH - 8));
            g.fill(fx, fy, fx + fw, fy + fh, 0xFF8A8A8A);
            g.fill(fx + 2, fy + 2, fx + fw - 2, fy + fh - 2, 0xFF23201A);
            drawEntity3D(g, fx + 4, fy + 4, fx + fw - 4, fy + fh - 4, 40);
        }

        // nome em destaque + linha divisoria
        String n = fit(name, tbW);
        g.text(this.font, n, tbX, titleY, 0xFF2A2A2A, false);
        g.fill(tbX, titleY + this.font.lineHeight + 2, tbX + tbW, titleY + this.font.lineHeight + 3, 0xFF6E6E6E);
        String r = fit(role, tbW);
        g.text(this.font, r, tbX + this.font.width(n) + 8, titleY, 0xFF7A5A18, false);

        // fala (tinta escura sobre o cinza)
        int y = bodyY;
        int colLeft = tbX;
        int colW = (optRects.isEmpty() ? tbX + tbW : optRects.get(0)[0] - 12) - tbX;
        for (String l : histLines) { g.text(this.font, l, colLeft, y, 0xFF6E6E6E, false); y += lh; }
        if (!histLines.isEmpty()) { g.fill(colLeft, y + 1, colLeft + colW, y + 2, 0xFFA8A8A8); y += 5; }
        if (showTitle()) { g.text(this.font, fit(title, colW), colLeft, y, 0xFF7A5A18, false); y += lh + 2; }
        for (String l : bodyLines) { g.text(this.font, l, colLeft, y, 0xFF1E1E1E, false); y += lh; }

        // opcoes: texto puro, com "»" na selecionada (como na referencia)
        for (int i = 0; i < optRects.size(); i++) {
            int[] b = optRects.get(i);
            boolean close = i == closeIdx;
            String label = close ? "Encerrar conversa" : opts.get(i)[0];
            boolean hover = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
            String num = (i + 1) + " ";
            String lab = fit(label, b[2] - 16);
            int lx = b[0] + (b[2] - this.font.width(num + lab)) / 2;
            int ly = b[1] + (b[3] - this.font.lineHeight) / 2;
            if (hover) {
                g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x33000000);
                g.text(this.font, "»", lx - 9, ly, 0xFFB06A10, false);
            }
            g.text(this.font, num, lx, ly, hover ? 0xFFB06A10 : 0xFF5A5A5A, false);
            g.text(this.font, lab, lx + this.font.width(num), ly, hover ? 0xFFB06A10 : 0xFF2E2E2E, false);
        }
    }
}
