package com.seuusuario.jogo;

import java.util.Random;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Dados das fases. Cada fase e montada com um "cursor" (cx) que anda para a direita:
 * voce chama metodos como spikes(3), gap(400), platform(120, 60)... e o cursor avanca.
 *
 * Para criar uma fase nova: veja a secao "COMO CRIAR UMA FASE" no README.
 */
public class LevelData {

    // Modos de jogo
    public static final int CUBE = 0, SHIP = 1, WAVE = 2, CUBE_CEIL = 3;   // CUBE_CEIL = cubo com teto (gravidade)

    public static final float GROUND = 80f, CEIL = 420f, SPIKE = 40f;
    static final float JUMP_V = 800f, GRAV = 2400f;   // iguais aos do LevelScreen (usados para posicionar orbes)

    // ---- Informacoes das fases (uma entrada por fase; mantenha MainGame.LEVEL_COUNT igual ao tamanho) ----
    public static final String[] NAMES = {"Cubo Classico", "Voo de Nave", "Gravidade", "Onda", "Grande Final"};
    public static final String[] MECHANICS = {
        "Cubo: pule espinhos, plataformas, orbes e pads",
        "Nave: segure para subir, solte para descer",
        "Gravidade: portais invertem chao e teto",
        "Onda: segure sobe na diagonal, solte desce",
        "Todas as mecanicas juntas!"
    };
    public static final String[] TIPS = {
        "Aperte ESPACO para pular. Siga os aneis ciano e use orbes e pads!",
        "NAVE! Segure ESPACO para subir, solte para descer.",
        "Portais amarelos invertem a gravidade, azuis voltam ao normal.",
        "ONDA! Segure ESPACO = sobe na diagonal, solte = desce.",
        "Cubo, nave, gravidade e onda. Boa sorte!"
    };
    public static final int[] TRACKS = {0, 1, 2, 0, 2};                   // musica de cada fase (0, 1 ou 2)
    public static final float[] SPEEDS = {420f, 460f, 440f, 420f, 460f};  // velocidade (px/s)
    public static final float[] HUES = {210f, 315f, 160f, 280f, 20f};     // cor do fundo
    public static final int[] DEFAULT_MODES = {CUBE, SHIP, CUBE_CEIL, WAVE, CUBE};

    // ---- Tipos de objeto ----
    public static class Block { public float x, y, w, h; Block(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; } }
    public static class Spike { public float x, y; public boolean flip; Spike(float x, float y, boolean flip) { this.x = x; this.y = y; this.flip = flip; } }
    public static class Orb { public float x, y; public boolean used; Orb(float x, float y) { this.x = x; this.y = y; } }
    public static class Marker { public float x, y; Marker(float x, float y) { this.x = x; this.y = y; } }
    public static class Pad { public float x, y; public boolean top; Pad(float x, float y, boolean top) { this.x = x; this.y = y; this.top = top; } }
    public static class Zone { public float x0, x1; public int mode; }
    public static class Grav { public float x; public boolean flip; Grav(float x, boolean flip) { this.x = x; this.flip = flip; } }

    // ---- Conteudo da fase ----
    public final String name, mechanic, tip;
    public final int track, defaultMode;
    public final float speed, hue;
    public float length;   // x do final da fase

    public final Array<Block> blocks = new Array<>();
    public final Array<Spike> spikes = new Array<>();
    public final Array<Orb> orbs = new Array<>();
    public final Array<Marker> markers = new Array<>();
    public final Array<Pad> pads = new Array<>();
    public final Array<Zone> zones = new Array<>();
    public final Array<Grav> gravs = new Array<>();

    // ---- estado do construtor ----
    float cx;                  // cursor
    boolean top;               // true = tudo que for criado fica no teto (gravidade invertida)
    float corrCenter = 250f;   // centro do corredor (nave / onda)
    final Random rnd;
    Zone cur;

    LevelData(int index, long seed, float startX) {
        name = NAMES[index];
        mechanic = MECHANICS[index];
        tip = TIPS[index];
        track = TRACKS[index];
        speed = SPEEDS[index];
        hue = HUES[index];
        defaultMode = DEFAULT_MODES[index];
        rnd = new Random(seed);
        cx = startX;
    }

    public static LevelData create(int index) {
        switch (index) {
            case 0: return level1();
            case 1: return level2();
            case 2: return level3();
            case 3: return level4();
            default: return level5();
        }
    }

    // ==================================================================
    //  FERRAMENTAS DO CONSTRUTOR
    // ==================================================================
    /** Anda o cursor para a direita (espaco vazio). */
    void gap(float d) { cx += d; }

    /** true: os proximos objetos ficam no teto (use depois de flip(true)). */
    void side(boolean onTop) { top = onTop; }

    float baseY() { return top ? CEIL : GROUND; }
    float centerY(float offset) { return top ? CEIL - offset : GROUND + offset; }

    /** n espinhos lado a lado. */
    void spikes(int n) {
        for (int i = 0; i < n; i++) spikes.add(new Spike(cx + i * SPIKE, baseY(), top));
        cx += n * SPIKE;
    }

    /** Bloco (largura w, altura h). Da para pousar em cima; bater de lado mata. */
    void block(float w, float h) {
        blocks.add(new Block(cx, top ? CEIL - h : GROUND, w, h));
        cx += w;
    }

    /** Anel ciano (dica de onde pular) em cursor+dx, a 'offset' de altura da base. */
    void marker(float dx, float offset) {
        markers.add(new Marker(cx + dx, centerY(offset)));
    }

    /** Plataforma com marcador "pule aqui" antes dela. Deixe um gap(480) antes! */
    void platform(float w, float h) {
        marker(-(speed * 0.333f + 20f), 85f);
        block(w, h);
    }

    /** Escada: bloco 40 -> espinho -> bloco 80. */
    void stairs() {
        marker(60f, 125f);
        block(80f, 40f);
        spikes(1);
        block(80f, 80f);
    }

    /** Tapete de espinhos largo demais para um pulo so: use o orbe amarelo no meio (aperte de novo no ar). */
    void carpetOrb(int n) {
        float half = n * SPIKE / 2f;
        float t0 = (half + 20f) / speed;                       // tempo ate o meio do tapete (pulo logo antes)
        float h = JUMP_V * t0 - 0.5f * GRAV * t0 * t0;         // altura do cubo nesse momento
        float off = MathUtils.clamp(h + 20f, 60f, 260f);
        marker(-20f, 85f);
        orbs.add(new Orb(cx + half, centerY(off)));
        spikes(n);
    }

    /** Pad (trampolim) seguido de uma torre alta que so se passa pulando no pad. */
    void padTower(float towerH) {
        pads.add(new Pad(cx, top ? CEIL - 12f : GROUND, top));
        cx += 40f;
        gap(80f);
        block(80f, towerH);
    }

    /** Portal de gravidade em cursor. up=true inverte (cubo cai para o teto); false volta ao normal. */
    void flip(boolean up) {
        gravs.add(new Grav(cx, up));
    }

    /** Comeca uma zona de modo (SHIP, WAVE, CUBE_CEIL, CUBE) no cursor. Termine com endZone(). */
    void beginZone(int mode) {
        cur = new Zone();
        cur.x0 = cx;
        cur.mode = mode;
        zones.add(cur);
    }

    void endZone() {
        cur.x1 = cx;
        cur = null;
    }

    /** Pilares da nave: abertura gapH, variacao maxima step, espacamento e largura aleatorios (mas fixos por fase). */
    void pillars(int cols, float gapH, float step, float spacingMin, float spacingMax, float wMin, float wMax) {
        for (int i = 0; i < cols; i++) {
            float w = wMin + rnd.nextFloat() * (wMax - wMin);
            corrCenter = MathUtils.clamp(corrCenter + (rnd.nextFloat() * 2f - 1f) * step,
                    GROUND + gapH / 2f + 10f, CEIL - gapH / 2f - 10f);
            float botTop = corrCenter - gapH / 2f, topBot = corrCenter + gapH / 2f;
            int type = rnd.nextInt(6);
            if (type != 5) blocks.add(new Block(cx, GROUND, w, botTop - GROUND));   // 0-4: pilar de baixo
            if (type != 4) blocks.add(new Block(cx, topBot, w, CEIL - topBot));     // 0-3,5: pilar de cima
            cx += w + spacingMin + rnd.nextFloat() * (spacingMax - spacingMin);
        }
    }

    /** Fileira de espinhos no chao e/ou no teto (nave e onda). */
    void shipSpikes(int n, boolean floor, boolean ceil) {
        for (int i = 0; i < n; i++) {
            if (floor) spikes.add(new Spike(cx + i * SPIKE, GROUND, false));
            if (ceil) spikes.add(new Spike(cx + i * SPIKE, CEIL, true));
        }
        cx += n * SPIKE;
    }

    private void addColumn(float w, float gapH) {
        float botTop = corrCenter - gapH / 2f, topBot = corrCenter + gapH / 2f;
        blocks.add(new Block(cx, GROUND, w, botTop - GROUND));
        blocks.add(new Block(cx, topBot, w, CEIL - topBot));
        cx += w;
    }

    /** Tunel continuo: o centro varia ate 'step' por coluna (onda). */
    void corridor(int cols, float colW, float gapH, float step) {
        for (int i = 0; i < cols; i++) {
            corrCenter = MathUtils.clamp(corrCenter + (rnd.nextFloat() * 2f - 1f) * step,
                    GROUND + gapH / 2f + 10f, CEIL - gapH / 2f - 10f);
            addColumn(colW, gapH);
        }
    }

    /** Tunel em zigue-zague: sobe e desce 'step' a cada coluna (onda). */
    void zigzag(int cols, float colW, float gapH, float step) {
        boolean up = corrCenter < (GROUND + CEIL) / 2f;
        for (int i = 0; i < cols; i++) {
            corrCenter = MathUtils.clamp(corrCenter + (up ? step : -step),
                    GROUND + gapH / 2f + 10f, CEIL - gapH / 2f - 10f);
            addColumn(colW, gapH);
            up = !up;
        }
    }

    /** Define o final da fase: 'after' pixels depois do cursor. */
    void end(float after) {
        length = cx + after;
    }

    // ==================================================================
    //  FASE 1: CUBO CLASSICO  (espinhos, plataformas, orbes, pads)
    // ==================================================================
    static LevelData level1() {
        LevelData L = new LevelData(0, 11, 800f);
        L.spikes(1); L.gap(380);
        L.spikes(2); L.gap(340);
        L.spikes(1); L.gap(260); L.spikes(1); L.gap(480);
        L.platform(120, 40); L.gap(380);
        L.spikes(3); L.gap(480);
        L.platform(160, 60); L.gap(340);
        L.spikes(2); L.gap(300); L.spikes(2); L.gap(480);
        L.stairs(); L.gap(400);
        L.carpetOrb(7); L.gap(420);
        L.spikes(1); L.gap(300); L.spikes(1); L.gap(300); L.spikes(1); L.gap(480);
        L.platform(200, 80); L.gap(360);
        L.padTower(160); L.gap(420);
        L.spikes(3); L.gap(340); L.spikes(3); L.gap(480);
        L.platform(120, 100); L.gap(380);
        L.carpetOrb(9); L.gap(420);
        L.padTower(180); L.gap(420);
        L.stairs(); L.gap(400);
        L.spikes(2); L.gap(300); L.spikes(1); L.gap(300); L.spikes(3); L.gap(420);
        L.carpetOrb(8); L.gap(420);
        L.platform(160, 80); L.gap(360);
        L.spikes(2); L.gap(300); L.spikes(2);
        L.end(900);
        return L;
    }

    // ==================================================================
    //  FASE 2: VOO DE NAVE  (a fase inteira e em modo nave)
    // ==================================================================
    static LevelData level2() {
        LevelData L = new LevelData(1, 22, 900f);
        L.pillars(6, 200, 60, 260, 320, 60, 80);
        L.gap(300);
        L.shipSpikes(6, true, false); L.gap(350);
        L.pillars(6, 180, 80, 240, 300, 60, 90);
        L.shipSpikes(5, false, true); L.gap(350);
        L.shipSpikes(8, true, true); L.gap(350);
        L.pillars(8, 165, 100, 230, 290, 60, 90);
        L.shipSpikes(4, true, true); L.gap(300);
        L.pillars(8, 155, 110, 220, 280, 70, 90);
        L.shipSpikes(6, true, false); L.gap(250);
        L.pillars(8, 150, 120, 220, 270, 70, 100);
        L.end(900);
        return L;
    }

    // ==================================================================
    //  FASE 3: GRAVIDADE  (portais invertem chao e teto)
    // ==================================================================
    static LevelData level3() {
        LevelData L = new LevelData(2, 33, 800f);
        // aquecimento no chao
        L.spikes(2); L.gap(340);
        L.spikes(1); L.gap(480);
        L.platform(120, 40); L.gap(400);
        // 1a inversao: o cubo cai para o teto
        L.flip(true); L.gap(520);
        L.side(true);
        L.spikes(2); L.gap(340);
        L.spikes(3); L.gap(480);
        L.platform(120, 60); L.gap(400);
        L.carpetOrb(7); L.gap(450);
        L.side(false);
        L.flip(false); L.gap(520);
        // volta ao chao
        L.spikes(2); L.gap(320); L.spikes(2); L.gap(480);
        L.platform(160, 80); L.gap(400);
        // vai-e-vem rapido
        L.flip(true); L.gap(240);
        L.flip(false); L.gap(520);
        L.spikes(3); L.gap(360);
        // teto de novo, mais dificil
        L.flip(true); L.gap(520);
        L.side(true);
        L.spikes(1); L.gap(300); L.spikes(1); L.gap(300); L.spikes(2); L.gap(480);
        L.platform(200, 80); L.gap(400);
        L.stairs(); L.gap(400);
        L.carpetOrb(9); L.gap(450);
        L.side(false);
        L.flip(false); L.gap(520);
        // final no chao
        L.spikes(3); L.gap(340); L.spikes(2); L.gap(300); L.spikes(2);
        L.end(900);
        return L;
    }

    // ==================================================================
    //  FASE 4: ONDA  (a fase inteira e em modo onda)
    // ==================================================================
    static LevelData level4() {
        LevelData L = new LevelData(3, 44, 900f);
        L.corridor(10, 110, 250, 60);
        L.corridor(10, 110, 200, 60);
        L.zigzag(10, 110, 175, 60);
        L.corridor(8, 110, 190, 60);
        L.gap(300);
        L.shipSpikes(8, true, true); L.gap(300);
        L.corridor(12, 110, 160, 60);
        L.zigzag(12, 110, 150, 55);
        L.corridor(12, 110, 140, 50);
        L.zigzag(10, 110, 140, 50);
        L.end(800);
        return L;
    }

    // ==================================================================
    //  FASE 5: GRANDE FINAL  (cubo -> nave -> gravidade -> onda -> cubo)
    // ==================================================================
    static LevelData level5() {
        LevelData L = new LevelData(4, 55, 800f);
        // 1) cubo
        L.spikes(2); L.gap(340);
        L.platform(160, 80); L.gap(380);
        L.carpetOrb(8); L.gap(420);
        L.padTower(180); L.gap(420);
        L.spikes(3); L.gap(340); L.spikes(3); L.gap(500);

        // 2) nave
        L.beginZone(SHIP); L.gap(500);
        L.pillars(7, 185, 90, 250, 310, 60, 85);
        L.shipSpikes(6, true, true); L.gap(300);
        L.pillars(8, 165, 110, 230, 290, 60, 90);
        L.gap(150);
        L.endZone(); L.gap(550);

        // 3) gravidade
        L.beginZone(CUBE_CEIL); L.gap(500);
        L.spikes(2); L.gap(340);
        L.flip(true); L.gap(520);
        L.side(true);
        L.spikes(2); L.gap(320); L.spikes(2); L.gap(480);
        L.platform(140, 60); L.gap(400);
        L.carpetOrb(7); L.gap(450);
        L.side(false);
        L.flip(false); L.gap(520);
        L.spikes(3); L.gap(340);
        L.flip(true); L.gap(240);
        L.flip(false); L.gap(520);
        L.spikes(2); L.gap(300);
        L.endZone(); L.gap(550);

        // 4) onda
        L.beginZone(WAVE); L.gap(500);
        L.corridor(8, 110, 220, 60);
        L.zigzag(10, 110, 175, 60);
        L.corridor(10, 110, 160, 55);
        L.zigzag(8, 110, 150, 50);
        L.gap(100);
        L.endZone(); L.gap(600);

        // 5) cubo final
        L.spikes(2); L.gap(300); L.spikes(1); L.gap(480);
        L.platform(160, 80); L.gap(380);
        L.carpetOrb(9); L.gap(420);
        L.padTower(180); L.gap(420);
        L.spikes(3); L.gap(320); L.spikes(1); L.gap(300); L.spikes(3);
        L.end(900);
        return L;
    }
}