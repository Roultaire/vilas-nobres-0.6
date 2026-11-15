package br.nobrevilas;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Interface de dialogos (tecla N): usa os DIALOGOS NATIVOS do Minecraft (/dialog show), sem tela customizada.
 * N no ar = Guia do addon. N olhando para um NPC = conversa com perguntas e respostas (alianca, rebeliao etc.).
 */
final class Ui {
    private Ui() {}

    // ───────────── construcao de dialogos ─────────────
    record Btn(String label, String cmd, String tip, boolean dynamic, boolean keep) {}

    static Btn b(String label, String cmd) { return new Btn(label, cmd, null, false, false); }
    static Btn bt(String label, String cmd, String tip) { return new Btn(label, cmd, tip, false, false); }
    /** Botao que continua a conversa com o MESMO NPC (a tela nao se recria). */
    static Btn bk(String label, String cmd) { return new Btn(label, cmd, null, false, true); }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String btnSnbt(Btn b) {
        return "{label:\"" + esc(b.label()) + "\",width:280," + (b.tip() != null ? "tooltip:\"" + esc(b.tip()) + "\"," : "")
            + "action:{type:\"" + (b.dynamic() ? "minecraft:dynamic/run_command\",template:\"" : "minecraft:run_command\",command:\"")
            + esc(b.cmd()) + "\"}}";
    }

    // quem esta falando (preenchido antes de montar cada dialogo)
    private static String spName = "Guia do Nobre Vilas", spRole = "Livro de conhecimento", spColor = "gold", spEntityUuid = "", spIcon = "";

    private static void speaker(String n, String r, String c) { spName = n; spRole = r; spColor = c; spEntityUuid = ""; spIcon = ""; }
    private static void speaker(String n, String r, String c, String uuid) { spName = n; spRole = r; spColor = c; spEntityUuid = uuid == null ? "" : uuid; spIcon = ""; }
    /** Com icone de pixel art (usado pelo guia, que nao tem NPC para mostrar em 3D). */
    private static void speaker(String n, String r, String c, String uuid, String icon) {
        spName = n; spRole = r; spColor = c; spEntityUuid = uuid == null ? "" : uuid; spIcon = icon == null ? "" : icon;
    }

    static void show(VillageManager m, ServerPlayer p, String title, String color, String body, String inputs, List<Btn> actions) {
        if (ServerPlayNetworking.canSend(p, NpcTalkPayload.TYPE)) {          // cliente com o mod: tela propria de conversa
            send(m, p, title, color, body, actions);
            return;
        }
        List<String> acts = new ArrayList<>();
        for (Btn b : actions) acts.add(btnSnbt(b));
        String snbt = "{type:\"minecraft:multi_action\",title:{text:\"" + esc(title) + "\",color:\"" + color + "\",bold:true},"
            + "body:[{type:\"minecraft:plain_message\",contents:\"" + esc(body) + "\",width:320}],"
            + (inputs == null ? "" : "inputs:[" + inputs + "],")
            + "can_close_with_escape:true,pause:false,after_action:\"close\",columns:1,"
            + "exit_action:{label:\"Fechar\",width:280},"
            + "actions:[" + String.join(",", acts) + "]}";
        try {
            var src = m.server().createCommandSourceStack().withSuppressedOutput();
            m.server().getCommands().getDispatcher().execute("dialog show " + p.getScoreboardName() + " " + snbt, src);
        } catch (Exception e) {
            NobreVilas.LOG.warn("Falha ao abrir dialogo ({}). Usando o chat.", e.toString());
            p.sendSystemMessage(Component.literal("═ " + title + " ═").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (String line : body.split("\n")) p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.WHITE));
            for (Btn x : actions) p.sendSystemMessage(Component.literal(" › " + x.label() + "  →  /" + x.cmd()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Monta e envia a fala para a tela do cliente. Se o NPC e o mesmo, manda "KEEP" (a tela nao se recria). */
    private static void send(VillageManager m, ServerPlayer p, String title, String color, String body, List<Btn> actions) {
        StringBuilder opts = new StringBuilder();
        for (Btn b : actions) {
            if (opts.length() > 0) opts.append('\u0002');
            opts.append(b.label()).append('\u0001').append(b.cmd());
        }
        String head = spName + "\u0001" + spRole + "\u0001" + spColor + "\u0001" + title + "\u0001" + color + "\u0001" + spIcon;
        // conversa de NPC sem UUID (opcao generica): mantem o NPC que ja esta na tela
        String keepUuid = spEntityUuid;
        if (keepUuid.isEmpty() && spIcon.isEmpty()) {
            String prev = lastSent.get(p.getUUID());
            if (prev != null && !prev.isEmpty()) keepUuid = prev;
        }
        spEntityUuid = keepUuid;
        // o NPC viaja em todo pacote: o cliente atualiza a MESMA tela e nunca fecha nem cai no guia
        lastSent.put(p.getUUID(), keepUuid);
        ServerPlayNetworking.send(p, new NpcTalkPayload(head, body, opts.toString(), spEntityUuid));
    }

    /** Ultimo NPC enviado a cada jogador (para a tela nao ser recriada a cada opcao). */
    private static final Map<UUID, String> lastSent = new HashMap<>();

    /** Fecha a tela de conversa do jogador (NPC se despediu). */
    static void close(ServerPlayer p) {
        lastSent.remove(p.getUUID());
        if (ServerPlayNetworking.canSend(p, NpcByePayload.TYPE)) ServerPlayNetworking.send(p, new NpcByePayload(""));
    }

    static void show(VillageManager m, ServerPlayer p, String title, String color, String body, List<Btn> actions) {
        show(m, p, title, color, body, null, actions);
    }

    // ───────────── guia do addon (N no ar) ─────────────
    static int guide(CommandSourceStack src, String page) throws CommandSyntaxException {
        guide(NobreVilas.manager(), src.getPlayerOrException(), page);
        return 1;
    }

    /** Icone (pixel art da tela) e cor de cada tema do guia. */
    private static final Map<String, String[]> GUIDE_STYLE = new HashMap<>();
    static {
        GUIDE_STYLE.put("menu",      new String[]{"Guia do Nobre Vilas", "Livro de conhecimento", "gold", "livro"});
        GUIDE_STYLE.put("visao",     new String[]{"O que é o Nobre Vilas", "Introdução", "gold", "livro"});
        GUIDE_STYLE.put("postos",    new String[]{"Postos e Títulos", "Hierarquia", "gold", "coroa"});
        GUIDE_STYLE.put("familias",  new String[]{"Territórios e Famílias", "Casas nobres", "gold", "estandarte"});
        GUIDE_STYLE.put("muralhas",  new String[]{"Muralhas, Farol e Castelos", "Defesa", "gold", "torre"});
        GUIDE_STYLE.put("conquista", new String[]{"Conquista", "Guerra", "red", "espada"});
        GUIDE_STYLE.put("rebeliao",  new String[]{"Rebelião e Império", "Guerra", "red", "espada"});
        GUIDE_STYLE.put("aliancas",  new String[]{"Alianças e Traição", "Diplomacia", "aqua", "pergaminho"});
        GUIDE_STYLE.put("impostos",  new String[]{"Impostos e Moedas", "Economia", "green", "moeda"});
        GUIDE_STYLE.put("comandos",  new String[]{"Comandos úteis", "Referência", "gray", "livro"});
        GUIDE_STYLE.put("status",    new String[]{"Meu status", "Ficha", "aqua", "pergaminho"});
        GUIDE_STYLE.put("capital",   new String[]{"A Capital", "Coração do reino", "gold", "coroa"});
    }

    static void guide(VillageManager m, ServerPlayer p, String page) {
        String[] st = GUIDE_STYLE.getOrDefault(page, GUIDE_STYLE.get("menu"));
        speaker(st[0], st[1], st[2], "", st[3]);
        Btn back = b("‹ Voltar ao guia", "vila ui guia menu");
        switch (page) {
            case "visao" -> show(m, p, "O que é o Nobre Vilas", "gold",
                "◆ O MUNDO\nO mundo ganha uma sociedade feudal. Cada vila tem nome, tipo (Portuária ou Colonial), uma família nobre, um governante na mansão, soldados e muralhas.\n\n"
                + "◆ VOCÊ\nComeça como plebeu e pode conquistar vilas, subir de posto, firmar alianças, romper com reis e até virar Imperador.\n\n"
                + "◆ COMO JOGAR\n• N olhando para um NPC → conversa\n• N no ar → este guia\n• /vila info → detalhes da vila onde você está", List.of(back));
            case "postos" -> show(m, p, "Postos e Títulos", "gold",
                "◆ A ESCADA\nPlebeu → Cavaleiro → Barão → Visconde → Conde → Marquês → Duque → Arquiduque → Príncipe → Rei → Imperador\n\n"
                + "◆ VILAS NECESSÁRIAS\n1 Cavaleiro · 2 Barão · 3 Visconde · 5 Conde · 7 Marquês · 10 Duque · 14 Arquiduque · 19 Príncipe\n\n"
                + "◆ REGRAS\nRei: só rompendo com o reino (Duque ou mais).\nImperador: tomando a capital do reino antigo.", List.of(back));
            case "familias" -> show(m, p, "Territórios e Famílias", "gold",
                "◆ OS TERRITÓRIOS\nVila → Condado → Marquesado → Ducado → Principado → Reino\n\n"
                + "◆ AS CASAS\nCada reino tem uma Casa Real: o rei e a rainha consorte são dela, os príncipes também; duques podem ser ramos da casa; condes e marqueses têm casas próprias.\n\n"
                + "◆ AS SEDES\nA primeira vila descoberta de cada território vira a sede dele. A do reino é a Capital.", List.of(back));
            case "muralhas" -> show(m, p, "Muralhas, Farol e Castelos", "gold",
                "◆ O FAROL\nUm feixe de luz marca cada vila: ciano = portuária, laranja = colonial.\n\n"
                + "◆ A MURALHA\nEvolui com o posto de quem governa: madeira, pedra, tijolos, deepslate, blackstone, quartzo.\nA capital do reino tem muralha de pedra escura com torres de canto e portão no meio de cada lado — ela aparece sozinha quando você chega a 300 blocos do centro.\n\n"
                + "◆ O CASTELO E A GUARDA\nA capital também ganha castelo. Os soldados equipam melhor conforme o posto do senhor.", List.of(back));
            case "conquista" -> show(m, p, "Conquista", "red",
                "◆ COMO TOMAR UMA VILA\nO governante mora na mansão (ou no castelo). Ataque-o: os soldados o defendem. Se ele cair, a vila é sua.\n\n"
                + "◆ LIMITE\nVocê só ataca governantes de até 1 posto acima do seu.\n\n"
                + "◆ NA PRÁTICA\nAperte N olhando para ele e escolha “Desafiar o governante”.", List.of(back));
            case "rebeliao" -> show(m, p, "Rebelião e Império", "red",
                "Com posto de Duque ou mais, fale com um governante (N) e escolha 'Romper com o reino'. Dê um nome ao seu novo reino e vire Rei.\n\nTodas as vilas do reino antigo marcham contra você. Para acabar com a guerra, tome a Capital do reino antigo: você vira Imperador e o reino antigo passa ao seu império.", List.of(back));
            case "aliancas" -> show(m, p, "Alianças e Traição", "aqua",
                "Fale com um governante (N) e proponha uma aliança, com o nobre ou com o reino inteiro. Custa moedas.\n\nAliados dão desconto nas trocas com os aldeões da vila aliada.\n\nAliar-se a outro reino enquanto você 'mora' em um reino NPC é traição: o seu reino declara guerra.", List.of(back));
            case "impostos" -> show(m, p, "Impostos e Moedas", "green",
                "Todo dia de jogo você recebe impostos das suas vilas, de 5 a 100 moedas conforme o posto, +10% por vila extra.\n\nA moeda padrão é a esmeralda, e pode ser trocada no config.json por um item de outro mod.\n\nConquistas também rendem espólios.", List.of(back));
            case "comandos" -> show(m, p, "Comandos úteis", "gray",
                "◆ VILA\n/vila info · detalhes da vila\n/vila localizar · vila mais próxima\n/vila lista · vilas visitadas\n/vila rastrear · bússola de vilas\n/vila rank · ranking\n/vila status · seu posto\n/vila livro · livro-guia\n\n"
                + "◆ ADMIN (criativo)\n/vila admin subir · descer\n/vila admin capital · monta a muralha da capital\n/vila admin castelo · refaz o castelo\n/vila socorro · refaz as guarnições\n\n"
                + "A maioria das ações também está nos diálogos (tecla N).", List.of(back));
            case "status" -> status(m, p, "vila ui guia menu");
            default -> show(m, p, "Guia do Nobre Vilas", "gold",
                "Bem-vindo ao Nobre Vilas. Escolha um tema abaixo.\n\nDica: aperte N olhando para um NPC para conversar com ele; N no ar abre este guia.",
                List.of(b("O que é o Nobre Vilas", "vila ui guia visao"), b("Postos e Títulos", "vila ui guia postos"),
                    b("Territórios e Famílias", "vila ui guia familias"), b("Muralhas, Farol e Castelos", "vila ui guia muralhas"),
                    b("Conquista", "vila ui guia conquista"), b("Rebelião e Império", "vila ui guia rebeliao"),
                    b("Alianças e Traição", "vila ui guia aliancas"), b("Impostos e Moedas", "vila ui guia impostos"),
                    b("Comandos úteis", "vila ui guia comandos"), b("Meu status", "vila ui guia status")));
        }
    }

    private static void status(VillageManager m, ServerPlayer p, String backCmd) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        int tier = m.playerTier(id);
        List<VillageData> mine = m.owned(id);
        StringBuilder sb = new StringBuilder();
        sb.append(Ranks.title(tier)).append(" ").append(p.getScoreboardName()).append("\n");
        sb.append("Vilas: ").append(mine.size()).append(" · Imposto diário: ").append(m.taxFor(id)).append(" moedas\n");
        sb.append("Moedas no inventário: ").append(Economy.balance(m, p)).append("\n");
        sb.append("Reino: ").append(pd.realm != null ? pd.realm : pd.homeKingdom).append("\n");
        for (VillageData v : mine) sb.append(" • ").append(v.name).append(" (poder ").append(m.power(v)).append(")\n");
        sb.append("Aliados: ").append(pd.allyVillages.size() + pd.allyKingdoms.size()).append("\nEm guerra com: ")
            .append(pd.enemies.isEmpty() ? "ninguém" : String.join(", ", pd.enemies)).append("\n");
        if (tier < Ranks.COUNT_FOR.length) sb.append("Próximo posto com ").append(Ranks.COUNT_FOR[tier]).append(" vilas.");
        show(m, p, "Seu status", "gold", sb.toString(), List.of(b("‹ Voltar", backCmd)));
    }

    // ───────────── conversa com NPCs (N olhando para um NPC) ─────────────
    /** Modos de abertura da tela: nova (TALK) ou continuando o mesmo NPC (KEEP). */
    static final String TALK = "talk", KEEP = "keep";

    static int talk(CommandSourceStack src, String alvo, String mode, String topic) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        VillageManager m = NobreVilas.manager();
        Entity e = null;
        try { e = m.server().overworld().getEntity(UUID.fromString(alvo)); } catch (IllegalArgumentException ignored) {}
        // NPC sumiu ou o jogador se afastou: fecha a conversa em vez de cair no guia (era o bug do "vai pro guia")
        if (e == null || e.distanceToSqr(p) > 144.0) {
            close(p);
            return 1;
        }
        // NPC que nao conversa (ovelha, cavalo, guarda do Villager Guards...): so uma linha de sistema
        String tipo = e.getType().toString();
        if (!tipo.contains("villager") && !tipo.contains("iron_golem")) {
            p.sendSystemMessage(Component.literal("§7" + mobLabel(e) + " não fala com forasteiros."));
            return 1;
        }


        String type = e.getType().toString();
        String lordVid = m.villageIdOfLord(e.getUUID());
        VillageData v = lordVid != null ? m.get(lordVid) : m.villageAt(e.getX(), e.getZ());
        String u = e.getUUID().toString();

        if (lordVid != null && v != null) {
            int t = m.tierOf(v);
            speaker(v.lord, Ranks.title(t) + " · " + v.typeLabel() + " de " + v.name, t >= 8 ? "red" : "gold", u);
            m.freeze(e, p);
            lord(m, p, v, u, topic);
        } else if (type.contains("wandering_trader")) {
            speaker("Mercador viajante", "Comerciante", "yellow", u);
            m.freeze(e, p);
            trader(m, p);
        } else if (v != null && (type.contains("guard") || type.contains("iron_golem"))) {
            boolean isCaptain = u.equals(v.lordUuid) || u.equals(v.captainUuid);
            speaker(isCaptain ? v.captain : "Soldado de " + v.name, isCaptain ? "Capitão da Guarda · " + v.typeLabel() : "Guarda · " + v.typeLabel(), isCaptain ? "gold" : "gray", u);
            m.freeze(e, p);
            guard(m, p, v, u, topic);
        } else if (v != null && type.contains("villager")) {
            speaker("Aldeão de " + v.name, "Habitante · " + v.typeLabel(), "yellow", u);
            m.freeze(e, p);
            commoner(m, p, v, u, topic);
        } else if (type.contains("iron_golem")) {
            speaker("Golem de " + v.name, "Protetor da vila", "gray", u);
            m.freeze(e, p);
            commoner(m, p, v, u, topic);
        }
        return 1;
    }

    private static String talkCmd(String u, String topic) { return "vila ui falar " + u + " " + topic; }

    // ───────────── vida da vila: fala de fundo ─────────────
    private static final Random IDLE_RND = new Random();

    /** Mostra a fala de fundo de um NPC que esta a vista do jogador (nao abre conversa, so o balao). */
    private static void idleTalk(VillageManager m, ServerPlayer p, String uuid) {
        String nome = "Aldeão da vila";
        VillageData v = m.villageAt(p.getX(), p.getZ());
        if (v != null) nome = "Aldeão de " + v.name;
        speaker(nome, "Habitante", "yellow", uuid);
        lastSent.remove(p.getUUID());          // garante que a tela seja criada com este NPC
        show(m, p, nome, "yellow", VillageManager.IDLE_LINES[IDLE_RND.nextInt(VillageManager.IDLE_LINES.length)], List.of(b("‹ Fechar", "vila ui guia")));
    }

    /** Nome amigavel do mob (ovelha, cavalo, vaca...). */
    private static final Map<String, String> MOB_NAMES = Map.ofEntries(
        Map.entry("sheep", "Ovelha"), Map.entry("cow", "Vaca"), Map.entry("pig", "Porco"),
        Map.entry("chicken", "Galinha"), Map.entry("horse", "Cavalo"), Map.entry("donkey", "Burro"),
        Map.entry("mule", "Mula"), Map.entry("llama", "Lhama"), Map.entry("wolf", "Lobo"),
        Map.entry("cat", "Gato"), Map.entry("ocelot", "Jaguatirica"), Map.entry("fox", "Raposa"),
        Map.entry("rabbit", "Coelho"), Map.entry("parrot", "Papagaio"), Map.entry("bat", "Morcego"),
        Map.entry("skeleton", "Esqueleto"), Map.entry("zombie", "Zumbi"), Map.entry("spider", "Aranha"),
        Map.entry("creeper", "Creeper"), Map.entry("enderman", "Enderman"), Map.entry("slime", "Slime"),
        Map.entry("wandering_trader", "Comerciante Viajante"), Map.entry("golem", "Golem"));

    private static String mobLabel(Entity e) {
        String t = e.getType().toString();
        int i = t.indexOf(':');
        if (i >= 0) t = t.substring(i + 1);
        // nome amigavel (o id interno aparecia cru na tela: "entity.minecraft.piglin")
        if (t.contains(".")) t = t.substring(t.lastIndexOf('.') + 1);
        String nice = MOB_NAMES.get(t);
        if (nice != null) return nice;
        t = t.replace('_', ' ');
        return t.isEmpty() ? "Criatura" : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String trader(VillageManager m, ServerPlayer p) {
        show(m, p, "Mercador viajante", "yellow",
            "Viajo de vila em vila. Dizem que as estradas ficarão cheias de caravanas em breve... por enquanto, só eu e minhas lhamas.\n\nSe quer saber como o reino funciona, aperte N no ar para abrir o guia.",
            List.of(b("Abrir o guia", "vila ui guia menu")));
        return null;
    }

    // ----- governante -----
    private static void lord(VillageManager m, ServerPlayer p, VillageData v, String u, String topic) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        int mine = m.playerTier(id), theirs = m.tierOf(v);
        Btn back = b("‹ Voltar", talkCmd(u, "menu"));
        switch (topic) {
            case "sobre" -> {
                List<VillageData> rank = m.ranking();
                int pos = rank.indexOf(v) + 1;
                String gov;
                if (v.ownerUuid != null) gov = "Governo: " + v.lord;
                else if (v.seatTier >= Ranks.KING) gov = "Eu governou " + v.name + ", a Capital do " + v.kingdom + ".";
                else if (v.seatTier >= Ranks.DUKE) gov = "Eu, " + v.lord + ", governo " + v.name + ".";
                else gov = "Governo: " + v.lord;
                show(m, p, v.name, "gold",
                    v.typeLabel() + " · " + m.subtitleFor(v) + "\n\n" + gov + "\nAdministração: " + v.officer + "\nGuarda: " + v.captain
                        + "\n\nMuralha: " + (v.isCapital() ? "Fortaleza do Reino" : Ranks.WALLS[Math.min(theirs, 10)])
                        + "\nPoder: " + m.power(v) + " · Produção: " + m.productivity(v) + "/dia\nRanking: #" + pos + " de " + rank.size(),
                    List.of(back));
            }
            case "reino" -> {
                boolean self = v.ownerUuid == null && v.seatTier >= Ranks.KING;
                show(m, p, "O reino", "gold",
                    "Condado: " + v.countRuler + " (" + v.county + ")\n"
                        + (v.marchName != null ? "Marquesado: " + v.marchRuler + " (" + v.marchName + ")\n" : "")
                        + "Ducado: " + v.dukeRuler + " (" + v.duchy + ")\n"
                        + (v.princeName != null ? "Principado: " + v.princeRuler + " (" + v.princeName + ")\n" : "")
                        + (self ? "Coroa: a minha, do " + v.kingdom
                                : "Coroa: " + v.kingRuler + " (" + v.kingdom + ")")
                        + (v.kingConsort != null && v.ownerUuid == null ? "\nCorte: " + v.kingConsort : ""),
                    List.of(back));
            }
            case "aliar", "aliarreino" -> {
                boolean kingdom = topic.equals("aliarreino");
                int cost = kingdom ? m.cfg().allyKingdomCost : m.cfg().allyVillageCost;
                boolean already = kingdom ? pd.allyKingdoms.contains(v.kingdom) : pd.allyVillages.contains(v.id);
                String home = pd.homeKingdom;
                boolean treason = home != null && !home.equals(v.kingdom) && !home.equals(pd.realm);
                String body = "Aliança com " + (kingdom ? "o " + v.kingdom : v.lord) + ".\n\nCusto: " + cost + " moedas (você tem " + Economy.balance(m, p) + ").\n"
                    + "Benefício: preços menores nas trocas com os aldeões " + (kingdom ? "de todo o reino." : "de " + v.name + ".")
                    + (treason ? "\n\n⚠ Seu reino de origem é " + home + ". Aliar-se a " + v.kingdom + " é TRAIÇÃO: as vilas do seu reino declararão guerra a você." : "")
                    + (already ? "\n\nVocê já tem esta aliança." : "");
                List<Btn> acts = new ArrayList<>();
                if (!already) acts.add(bt("Confirmar aliança (" + cost + " moedas)", kingdom ? "vila aliar reino" : "vila aliar", "Cobra o valor do seu inventário"));
                acts.add(back);
                show(m, p, "Propor aliança", "aqua", body, acts);
            }
            case "romper" -> show(m, p, "Romper aliança", "red",
                "Romper a aliança com " + v.name + " e com o " + v.kingdom + " (se houver). Os descontos acabam.",
                List.of(b("Romper agora", "vila romper"), back));
            case "desafio" -> {
                String denied = Conquest.attackDenied(m, p, v);
                show(m, p, "Desafiar o governante", "red",
                    "Para conquistar " + v.name + ", mate " + v.lord + ". Os soldados da vila o defenderão assim que o primeiro golpe for dado.\n\n"
                        + (denied == null ? "✔ Você PODE atacá-lo agora (seu posto: " + Ranks.title(mine) + ", dele: " + Ranks.title(theirs) + ")." : "✘ " + denied)
                        + "\n\nConquistar rende espólios e aumenta o seu posto.",
                    List.of(back));
            }
            case "rebelar" -> {
                if (pd.king || mine < Ranks.DUKE) {
                    show(m, p, "Romper com o reino", "red",
                        pd.king ? "Você já é " + Ranks.title(mine) + " e tem o seu próprio reino."
                            : "Só um Duque ou posto superior (10+ vilas) pode romper com o rei.\nSeu posto: " + Ranks.title(mine) + " (" + m.owned(id).size() + " vilas).",
                        List.of(back));
                } else {
                    show(m, p, "Romper com o reino", "red",
                        "Você deixará o " + (pd.homeKingdom != null ? pd.homeKingdom : "reino") + " e fundará o seu. Todas as vilas do reino antigo marcharão contra você. Tome a capital para virar Imperador.\n\nEscolha o nome do novo reino:",
                        "{type:\"minecraft:text\",key:\"nome\",label:\"Nome do reino\",width:300,max_length:24,initial:\"\"}",
                        List.of(new Btn("Proclamar o reino", "vila rebelar $(nome)", null, true, true), back));
                }
            }
            case "status" -> status(m, p, talkCmd(u, "menu"));
            default -> {
                String greet;
                boolean king = v.ownerUuid == null && v.seatTier >= Ranks.KING;
                if (king) greet = lordGreeting(m.charismaGrade(v));
                else if (m.isAllied(id, v)) greet = "Meu aliado! As portas de " + v.name + " estão sempre abertas para você.";
                else if (mine == 0) greet = "Um plebeu diante de " + Ranks.title(theirs) + "? Diga o que quer, e seja breve.";
                else if (mine >= theirs) greet = "Vossa Senhoria honra a minha casa. Em que posso servir?";
                else greet = "Um " + Ranks.title(mine) + " em minhas terras. Fale, mas com respeito.";
                List<Btn> acts = new ArrayList<>();
                acts.add(b("Falar sobre a vila", talkCmd(u, "sobre")));
                acts.add(b("Propor aliança", talkCmd(u, "aliar")));
                acts.add(b("Aliança com o reino inteiro", talkCmd(u, "aliarreino")));
                if (m.isAllied(id, v)) acts.add(b("Romper aliança", talkCmd(u, "romper")));
                acts.add(b("Perguntar sobre o reino", talkCmd(u, "reino")));
                acts.add(b("Desafiar o governante", talkCmd(u, "desafio")));
                if (mine >= Ranks.DUKE && !pd.king) acts.add(b("Romper com o reino", talkCmd(u, "rebelar")));
                acts.add(b("Meu status", talkCmd(u, "status")));
                show(m, p, v.lord, theirs >= 8 ? "red" : "gold", greet + "\n\n" + v.typeLabel() + " de " + v.name + "\nSeu posto: " + Ranks.title(mine), acts);
            }
        }
    }

    private static String charismaPraise(int g) {
        return switch (g) {
            case 2 -> "O rei é generoso, e o povo o bendiz a cada colheita.";
            case 1 -> "O rei é justo e tem o coração do povo.";
            case -1 -> "O povo anda murmurando contra a coroa... os impostos pesam e a raiva cresce.";
            case -2 -> "Ninguém aguenta mais. O povo quer a queda do rei. Só falta uma faísca.";
            default -> "O povo tolera o rei, mas sem grande entusiasmo.";
        };
    }

    private static String lordGreeting(int grade) {
        return switch (grade) {
            case 2 -> "Ah, um rosto amigo nesta terra. Meu povo fala bem de você. Em que posso servir?";
            case 1 -> "Bem-vindo. A justiça do meu reinado é conhecida. O que deseja?";
            case -1 -> "Você tem o hábito de levantar a mão contra a minha coroa. Cuidado por onde anda.";
            case -2 -> "Você ousa aparecer diante de mim? Eu, que o povo tanto odeia... por sua causa. Vá, antes que eu perca a paciência.";
            default -> "Um viajante em minha corte. Diga o que quer.";
        };
    }

    // ----- aldeao comum -----
    private static void commoner(VillageManager m, ServerPlayer p, VillageData v, String u, String topic) {
        UUID id = p.getUUID();
        PlayerData pd = m.pd(id);
        Btn back = b("‹ Voltar", talkCmd(u, "menu"));
        switch (topic) {
            case "governa" -> {
                String g;
                if (v.ownerUuid != null) g = "Esta vila pertence a " + Ranks.title(m.tierOf(v)) + " " + v.ownerName + ".";
                else if (v.isCapital()) {
                    g = v.lord + " é o nosso soberano. Esta é a Capital do " + v.kingdom + ".\n" + charismaPraise(m.charismaGrade(v));
                }
                else if (v.seatTier == Ranks.DUKE) g = v.lord + " governa " + v.name + " e todo o " + v.duchy + ".";
                else if (v.seatTier == Ranks.MARQUES) g = v.lord + " governa " + v.name + ", senhor do " + v.marchName + ".";
                else if (v.seatTier == Ranks.CONDE) g = v.lord + " governa " + v.name + ", vassalo de " + v.dukeRuler + ", do " + v.duchy + ".";
                else g = v.lord + " governa " + v.name + ", sob a autoridade de " + v.countRuler + ", do " + v.county + ".";
                g += "\nO reino é regido por " + v.kingRuler + ".";
                show(m, p, "Quem governa?", "yellow", g, List.of(back));
            }
            case "vila" -> {
                List<VillageData> rank = m.ranking();
                show(m, p, "Como está a vila?", "yellow",
                    "Somos uma " + v.typeLabel().toLowerCase() + ". Nossa muralha é " + (v.isCapital() ? "a fortaleza do reino" : Ranks.WALLS[Math.min(m.tierOf(v), 10)].toLowerCase())
                        + ".\nO poder de " + v.name + " é " + m.power(v) + ", o " + (rank.indexOf(v) + 1) + "º entre as " + rank.size() + " vilas que conheço.\nProduzimos cerca de " + m.productivity(v) + " por dia.",
                    List.of(back));
            }
            case "guerra" -> {
                String t;
                if (v.isCapital() && v.ownerUuid == null) {
                    int g = m.charismaGrade(v);
                    t = switch (g) {
                        case 2 -> "A paz reina, o rei é amado e os celeiros estão cheios.";
                        case 1 -> "Há paz, e a justiça do rei mantém tudo em ordem.";
                        case -1 -> "As vilas andam inquietas. Os impostos pesam e a raiva contra a coroa cresce.\n" + charismaPraise(g);
                        case -2 -> "Todo mundo fala em rebelião. Se eu fosse você, não apostaria na coroa.\n" + charismaPraise(g);
                        default -> "Por enquanto há paz. Que continue assim.";
                    };
                } else if (pd.enemies.contains(v.kingdom)) t = "Ouvi dizer que o " + v.kingdom + " está em guerra com VOCÊ. Cuidado por aqui, viajante.";
                else if (!pd.enemies.isEmpty()) t = "Dizem que " + String.join(", ", pd.enemies) + " marcha contra um nobre rebelde. Por aqui, a paz continua.";
                else t = "Por enquanto há paz. Que continue assim.";
                show(m, p, "Há guerra?", "yellow", t, List.of(back));
            }
            case "capital" -> {
                if (v.isCapital()) {
                    show(m, p, "Onde fica a capital?", "yellow",
                        "Você está nela! Esta é a Capital do " + v.kingdom + ". O trono de " + v.kingRuler + " fica aqui.",
                        List.of(back));
                } else {
                    VillageData cap = null;
                    for (VillageData o : m.all()) if (o.isNamed() && o.isCapital() && Objects.equals(o.kingdom, v.kingdom)) cap = o;
                    if (cap == null) for (VillageData o : m.all()) if (o.isNamed() && o.isCapital() && Objects.equals(o.kingRuler, v.kingRuler)) cap = o;
                    String t;
                    if (cap == null) t = "A capital? Nunca estive lá. Ninguém por aqui sabe dizer onde fica.";
                    else {
                        double dx = cap.x - p.getX(), dz = cap.z - p.getZ();
                        t = "A capital é " + cap.name + ", em " + cap.x + ", " + cap.z + ". " + VillageManager.arrow(dx, dz, p.getYRot())
                            + " a cerca de " + (int) Math.sqrt(dx * dx + dz * dz) + " blocos. É a mais bem guardada do reino.";
                    }
                    show(m, p, "Onde fica a capital?", "yellow", t, List.of(back));
                }
            }
            case "vizinhas" -> {
                List<VillageData> l = new ArrayList<>();
                for (VillageData o : m.all()) if (o.isNamed() && o != v) l.add(o);
                l.sort(Comparator.comparingDouble(o -> (o.x - v.x) * (double) (o.x - v.x) + (o.z - v.z) * (double) (o.z - v.z)));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, l.size()); i++) {
                    VillageData o = l.get(i);
                    double dx = o.x - p.getX(), dz = o.z - p.getZ();
                    sb.append(VillageManager.arrow(dx, dz, p.getYRot())).append(" ").append(o.name).append(" (").append(o.typeLabel()).append(") — ")
                        .append((int) Math.sqrt(dx * dx + dz * dz)).append(" blocos\n");
                }
                show(m, p, "Vilas vizinhas", "yellow", sb.length() == 0 ? "Não conheço outras vilas por aqui." : sb.toString(), List.of(back));
            }
            case "impostos" -> show(m, p, "Impostos", "yellow",
                v.ownerUuid != null && v.ownerUuid.equals(id.toString())
                    ? "Os impostos de " + v.name + " vão para o senhor, ou seja, você. Seu total diário: " + m.taxFor(id) + " moedas."
                    : "Pagamos nossos impostos ao senhor da vila. Ouvi dizer que quem governa muitas vilas recebe até 100 moedas por dia.",
                List.of(back));
            default -> show(m, p, "Aldeão de " + v.name, "yellow",
                (m.isAllied(id, v) ? "Que bom ver um aliado de nosso senhor!" : "Bom dia, viajante.") + " O que deseja saber?",
                List.of(b("Quem governa a vila?", talkCmd(u, "governa")), b("Como está a vila?", talkCmd(u, "vila")),
                    b("Há guerra?", talkCmd(u, "guerra")), b("Onde fica a capital?", talkCmd(u, "capital")),
                    b("Vilas vizinhas", talkCmd(u, "vizinhas")), b("Sobre os impostos", talkCmd(u, "impostos"))));
        }
    }

    // ----- soldado -----
    private static void guard(VillageManager m, ServerPlayer p, VillageData v, String u, String topic) {
        PlayerData pd = m.pd(p.getUUID());
        Btn back = b("‹ Voltar", talkCmd(u, "menu"));
        boolean isCaptain = u.equals(v.lordUuid) || u.equals(v.captainUuid);
        switch (topic) {
            case "comando" -> show(m, p, "Quem comanda a guarda?", "gray",
                v.ownerUuid != null ? "Respondemos a " + v.ownerName + ", senhor de " + v.name + "."
                    : (isCaptain ? "Eu comando a guarda de " + v.name + ", sob as ordens de " + v.lord + "."
                                 : v.captain + " comanda a guarda de " + v.name + ", sob as ordens de " + v.lord + "."),
                List.of(back));
            case "ameacas" -> show(m, p, "Há ameaças?", "gray",
                pd.enemies.contains(v.kingdom) ? "O " + v.kingdom + " está em guerra com você, viajante. Não dê motivos para sacarmos as armas."
                    : pd.enemies.isEmpty() ? "Tudo calmo. Patrulhamos o portão e a mansão dia e noite." : "Soldados de " + String.join(", ", pd.enemies) + " estão em marcha, mas não contra nós.",
                List.of(back));
            default -> show(m, p, (isCaptain ? v.captain : "Soldado de " + v.name), isCaptain ? "gold" : "gray",
                isCaptain ? "Alto lá! Eu, " + v.captain + ", comando esta guarda. Diga rápido o que quer."
                           : "Alto lá! Estamos de guarda. Diga rápido o que quer.",
                List.of(b("Quem comanda a guarda?", talkCmd(u, "comando")), b("Há ameaças?", talkCmd(u, "ameacas")), b("Sobre a muralha", talkCmd(u, "muralha"))));
        }
    }
}
