package br.nobrevilas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Perimetro da capital com o kit do jogador: 4 torres de canto (13x13), segmentos de muralha (5x15) emendados pelas pontas
 * e UM portao. Cada peca e assentada numa altura propria (sem esticar nada), com no maximo 1 bloco de degrau entre
 * pecas vizinhas e fundacao de pedra por baixo. Local e deslocamento escolhidos pelo terreno mais plano/seco.
 * O trabalho e dividido em tarefas executadas poucas por tick.
 */
final class CapitalBuilder {
    private CapitalBuilder() {}

    static final int TOWER = 13, SEG = 15;

    private record Inst(String name, Rotation rot, int minX, int minZ, int w, int d, int side, int idx) {}

    private static int surf(ServerLevel l, int x, int z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    private static boolean wet(ServerLevel l, int x, int z) {
        return !l.getFluidState(new BlockPos(x, surf(l, x, z), z)).isEmpty();
    }

    private static double perimeterCost(ServerLevel l, int x0, int z0, int p) {
        List<Integer> hs = new ArrayList<>();
        int wets = 0;
        for (int t = 0; t <= p; t += 5) {
            int[][] pts = {{x0 + 6 + t, z0 + 6}, {x0 + 6 + t, z0 + 6 + p}, {x0 + 6, z0 + 6 + t}, {x0 + 6 + p, z0 + 6 + t}};
            for (int[] q : pts) {
                hs.add(surf(l, q[0], q[1]));
                if (wet(l, q[0], q[1])) wets++;
            }
        }
        Collections.sort(hs);
        int med = hs.get(hs.size() / 2);
        double c = 0;
        for (int h : hs) c += Math.abs(h - med);
        return c + wets * 20.0;
    }

    private static int median(ServerLevel l, int minX, int minZ, int w, int d) {
        List<Integer> hs = new ArrayList<>();
        for (int x = minX; x < minX + w; x += 2) for (int z = minZ; z < minZ + d; z += 2) hs.add(surf(l, x, z));
        Collections.sort(hs);
        return hs.get(hs.size() / 2);
    }

    /** @return lista de tarefas, ou null se algum chunk da area ainda nao esta carregado. */
    static List<Runnable> plan(ServerLevel l, VillageData v) {
        int r = v.radius;
        // medidas reais das pecas do kit
        int tw = Math.max(1, StructureKit.size(l, "torre", Rotation.NONE)[0]);
        int gw = Math.max(1, StructureKit.size(l, "muralha", Rotation.NONE)[0]);
        int gh = Math.max(1, StructureKit.size(l, "muralha", Rotation.NONE)[2]);
        int k = 6;
        while (2 * tw + (k - 1) * gh < 2 * (r + 10) + 2 && k < 12) k++;
        int p = 2 * tw + (k - 1) * gh, half = p / 2;
        int allowed = Math.max(0, Math.min(24, half - r - 6));
        int ext = half + allowed + tw + 4;
        for (int dx = -ext; dx <= ext + 15; dx += 16)
            for (int dz = -ext; dz <= ext + 15; dz += 16)
                if (!l.hasChunkAt(new BlockPos(v.x + Math.min(dx, ext), 64, v.z + Math.min(dz, ext)))) return null;

        int bx = 0, bz = 0;
        double best = Double.MAX_VALUE;
        for (int ox = -allowed; ox <= allowed; ox += 8) {
            for (int oz = -allowed; oz <= allowed; oz += 8) {
                double c = perimeterCost(l, v.x + ox - half, v.z + oz - half, p) + 0.2 * (Math.abs(ox) + Math.abs(oz));
                if (c < best) { best = c; bx = ox; bz = oz; }
            }
        }
        int x0 = v.x + bx - half, z0 = v.z + bz - half;

        // anel de pecas no sentido horario: torre NO, norte, torre NE, leste, torre SE, sul (invertido), torre SO, oeste (invertido)
        int th = tw;
        int x1 = x0 + th - 1, z1 = z0 + th - 1;
        List<Inst> ring = new ArrayList<>();
        ring.add(new Inst("torre", Rotation.COUNTERCLOCKWISE_90, x0, z0, tw, tw, -1, 0));
        for (int j = 0; j < k; j++) ring.add(new Inst("muralha", Rotation.COUNTERCLOCKWISE_90, x1 + 1 + gh * j, z0, gh, gw, 0, j));
        ring.add(new Inst("torre", Rotation.NONE, x0 + p - th, z0, tw, tw, -1, 0));
        for (int j = 0; j < k; j++) ring.add(new Inst("muralha", Rotation.NONE, x0 + p - th, z1 + 1 + gh * j, gw, gh, 1, j));
        ring.add(new Inst("torre", Rotation.NONE, x0 + p - th, z0 + p - th, tw, tw, -1, 0));
        for (int j = k - 1; j >= 0; j--) ring.add(new Inst("muralha", Rotation.COUNTERCLOCKWISE_90, x1 + 1 + gh * j, z0 + p - th, gh, gw, 2, j));
        ring.add(new Inst("torre", Rotation.CLOCKWISE_90, x0, z0 + p - th, tw, tw, -1, 0));
        for (int j = k - 1; j >= 0; j--) ring.add(new Inst("muralha", Rotation.NONE, x0, z1 + 1 + gh * j, gw, gh, 3, j));
        int m = ring.size();

        // portao: o do meio exato de cada lado (centralizado, nao escolhido pelo terreno)
        // lado 2 = sul (invertido). Cada lado tem k segmentos de muralha com indices j=0..k-1;
        // o segmento central (idx == k/2) de um lado fixo vira o portao.
        int[] raw = new int[m];
        for (int i = 0; i < m; i++) raw[i] = median(l, ring.get(i).minX(), ring.get(i).minZ(), ring.get(i).w(), ring.get(i).d()) + 1;
        int gateAt = -1;
        for (int i = 0; i < m; i++) {
            Inst in = ring.get(i);
            if (!in.name().equals("muralha") || in.idx() != k / 2) continue;
            gateAt = i;   // primeiro segmento central de cada lado; o lado sul vem depois na ordem
        }
        if (gateAt >= 0) { Inst g = ring.get(gateAt); ring.set(gateAt, new Inst("portao", g.rot(), g.minX(), g.minZ(), g.w(), g.d(), g.side(), g.idx())); }

        // alturas: nunca abaixo do terreno medio de cada peca e com degrau maximo de 1 entre vizinhas
        int[] y = new int[m];
        for (int i = 0; i < m; i++) {
            int e = Integer.MIN_VALUE;
            for (int j = 0; j < m; j++) {
                int dist = Math.min(Math.abs(i - j), m - Math.abs(i - j));
                e = Math.max(e, raw[j] - dist);
            }
            y[i] = e;
        }

        List<Runnable> jobs = new ArrayList<>();
        jobs.add(() -> WallBuilder.removeOld(l, v));
        // torres, depois muralhas, depois o portao
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < m; i++) {
                Inst in = ring.get(i);
                boolean mine = pass == 0 ? in.name().equals("torre") : pass == 1 ? in.name().equals("muralha") : in.name().equals("portao");
                if (!mine) continue;
                final int yy = y[i];
                jobs.add(() -> {
                    foundation(l, in, yy);
                    StructureKit.place(l, in.name(), in.rot(), in.minX(), yy, in.minZ());
                });
            }
        }
        if (gateAt >= 0) {
            Inst g = ring.get(gateAt);
            final int gy = y[gateAt] - 1;
            jobs.add(() -> approaches(l, g, gy));
        }
        NobreVilas.LOG.info("Capital {}: {} pecas, lado {} blocos, portao no lado {}", v.name, m, p, gateAt >= 0 ? ring.get(gateAt).side() : -1);
        return jobs;
    }

    /** Fundacao de tijolos de deepslate por baixo da peca ate o terreno (maximo 30 blocos). */
    private static void foundation(ServerLevel l, Inst in, int y) {
        for (int x = in.minX(); x < in.minX() + in.w(); x++) {
            for (int z = in.minZ(); z < in.minZ() + in.d(); z++) {
                int s = surf(l, x, z);
                for (int yy = y - 1; yy > s && yy > y - 30; yy--) l.setBlock(new BlockPos(x, yy, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
            }
        }
    }

    /** Rampas curtas e suaves nos dois lados do portao, no nivel do piso, sem cavar trincheiras. */
    private static void approaches(ServerLevel l, Inst g, int floorY) {
        boolean alongX = g.rot() == Rotation.COUNTERCLOCKWISE_90;      // muralha ao longo de X: abertura atravessa Z
        if (alongX) {
            int cx = g.minX() + g.w() / 2;
            WallBuilder.ramp(l, cx, g.minZ(), 0, -1, 1, 0, 1, floorY, 3);                       // norte
            WallBuilder.ramp(l, cx, g.minZ() + g.d() - 1, 0, 1, 1, 0, 1, floorY, 3);           // sul
        } else {
            int cz = g.minZ() + g.d() / 2;
            WallBuilder.ramp(l, g.minX(), cz, -1, 0, 0, 1, 1, floorY, 3);                       // oeste
            WallBuilder.ramp(l, g.minX() + g.w() - 1, cz, 1, 0, 0, 1, 1, floorY, 3);           // leste
        }
    }
}
