package com.seuusuario.jogo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Joga uma fase de LevelData: cubo, nave, onda e gravidade invertida, ate o final. */
public class LevelScreen extends ScreenAdapter {

    static final float W = 800f, H = 480f;
    static final float GROUND = LevelData.GROUND, CEIL = LevelData.CEIL, SPIKE = LevelData.SPIKE;
    static final float SIZE = 40f, SHIP_H = 28f, WAVE_S = 12f;
    static final float GRAVITY = -2400f, JUMP_V = 800f, ORB_V = 780f, PAD_V = 1100f;
    static final float CAM_OFFSET = 250f;

    static final Color ORB_COLOR = new Color(1f, 0.92f, 0.15f, 1f);
    static final Color GREEN = new Color(0.2f, 1f, 0.45f, 1f);
    static final Color ORANGE = new Color(1f, 0.55f, 0.1f, 1f);
    static final Color MAGENTA = new Color(1f, 0.25f, 0.8f, 1f);
    static final Color BLUE = new Color(0.25f, 0.5f, 1f, 1f);
    static final Color YELLOW = new Color(1f, 0.9f, 0.2f, 1f);
    static final Color CYAN = new Color(0.3f, 1f, 1f, 1f);

    enum State { PLAYING, DEAD, WON }

    static class Particle { float x, y, vx, vy, life, size; Color col; }

    final MainGame game;
    final int index;
    final LevelData level;
    final OrthographicCamera cam = new OrthographicCamera();
    final Viewport viewport = new FitViewport(W, H, cam);
    final Matrix4 hudMatrix = new Matrix4().setToOrtho2D(0, 0, W, H);
    final Color tmp = new Color(), tmp2 = new Color();
    final Array<Particle> particles = new Array<>();

    State state = State.PLAYING;
    int mode, gravDir = 1, attempts;
    boolean leaving, lockInput = true, onGround;
    float px, py, vy, rot;
    float time, deadTimer, wonTimer, jumpBuffer, trailTimer, flash, shake, hintTimer;
    String hintText = "";

    public LevelScreen(MainGame game, int index) {
        this.game = game;
        this.index = index;
        this.level = LevelData.create(index);
    }

    @Override
    public void show() {
        game.useTrack(level.track);
        startRun();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------
    boolean rawHeld() {
        return Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isTouched();
    }

    boolean jumpHeld() { return !lockInput && rawHeld(); }

    boolean jumpJustPressed() {
        return Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.justTouched();
    }

    // ------------------------------------------------------------------
    //  Estado
    // ------------------------------------------------------------------
    static boolean isCubeMode(int m) { return m == LevelData.CUBE || m == LevelData.CUBE_CEIL; }

    static float heightOf(int m) {
        if (m == LevelData.SHIP) return SHIP_H;
        if (m == LevelData.WAVE) return WAVE_S;
        return SIZE;
    }

    int modeAt(float x) {
        for (LevelData.Zone z : level.zones) if (x >= z.x0 && x < z.x1) return z.mode;
        return level.defaultMode;
    }

    boolean flippedAt(float x) {
        boolean f = false;
        for (LevelData.Grav g : level.gravs) if (g.x <= x) f = g.flip;
        return f;
    }

    /** Converte y (fundo do objeto de altura h) para o "espaco espelhado" quando a gravidade esta invertida. */
    float mir(float y, float h) {
        return gravDir == 1 ? y : GROUND + CEIL - y - h;
    }

    void startRun() {
        attempts++;
        px = 0; py = GROUND; vy = 0; rot = 0;
        mode = level.defaultMode;
        gravDir = 1;
        onGround = true;
        state = State.PLAYING;
        for (LevelData.Orb o : level.orbs) o.used = false;
        particles.clear();
        jumpBuffer = 0; flash = 0; shake = 0; trailTimer = 0;
        hintText = level.tip;
        hintTimer = 3.5f;
        game.restartMusic();
    }

    int percent() {
        return Math.min(99, (int) (px / level.length * 100f));
    }

    void die() {
        state = State.DEAD;
        deadTimer = 0;
        shake = 0.4f;
        game.music.stop();
        game.deathSnd.play(0.7f);
        game.submitLevel(index, percent());
        float cx = px + SIZE / 2f, cy = py + heightOf(mode) / 2f;
        burst(cx, cy, MainGame.PALETTE[game.color1], 18, 520f);
        burst(cx, cy, MainGame.PALETTE[game.color2], 12, 420f);
    }

    void win() {
        state = State.WON;
        wonTimer = 0;
        flash = 0.6f;
        game.submitLevel(index, 100);
        game.portalSnd.play(0.8f);
        float cx = px + SIZE / 2f, cy = py + heightOf(mode) / 2f;
        for (int i = 0; i < 8; i++) {
            burst(cx, cy, MainGame.PALETTE[MathUtils.random(0, MainGame.PALETTE.length - 2)], 10, 600f);
        }
    }

    String modeHint(int m) {
        switch (m) {
            case LevelData.SHIP: return "NAVE!  Segure para subir, solte para descer";
            case LevelData.WAVE: return "ONDA!  Segure = sobe, solte = desce";
            case LevelData.CUBE_CEIL: return "CUBO COM TETO  Atencao aos portais de gravidade";
            default: return "CUBO!  Aperte ESPACO para pular";
        }
    }

    void switchMode(int m) {
        float cy = py + heightOf(mode) / 2f;     // mantem o centro
        mode = m;
        py = cy - heightOf(m) / 2f;
        vy = 0f;
        rot = 0f;
        onGround = false;
        flash = 0.4f;
        hintText = modeHint(m);
        hintTimer = 2.5f;
        game.portalSnd.play(0.6f);
        burst(px + SIZE / 2f, cy, modeColor(m), 24, 420f);
    }

    void switchGravity(int dir) {
        gravDir = dir;
        vy = 0f;
        onGround = false;
        flash = 0.3f;
        hintText = dir == -1 ? "GRAVIDADE INVERTIDA!" : "GRAVIDADE NORMAL";
        hintTimer = 1.5f;
        game.portalSnd.play(0.6f);
        burst(px + SIZE / 2f, py + SIZE / 2f, dir == -1 ? YELLOW : BLUE, 20, 400f);
    }

    static Color modeColor(int m) {
        switch (m) {
            case LevelData.SHIP: return GREEN;
            case LevelData.WAVE: return MAGENTA;
            case LevelData.CUBE_CEIL: return CYAN;
            default: return ORANGE;
        }
    }

    void burst(float x, float y, Color c, int n, float maxSpeed) {
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y;
            float a = MathUtils.random(0f, MathUtils.PI2), s = MathUtils.random(maxSpeed * 0.3f, maxSpeed);
            p.vx = MathUtils.cos(a) * s;
            p.vy = MathUtils.sin(a) * s;
            p.life = MathUtils.random(0.4f, 0.9f);
            p.size = MathUtils.random(4f, 10f);
            p.col = c;
            particles.add(p);
        }
    }

    // ------------------------------------------------------------------
    //  Atualizacao
    // ------------------------------------------------------------------
    @Override
    public void render(float delta) {
        update(Math.min(delta, 1f / 30f));
        if (leaving) return;
        draw();
    }

    void backToSelect() {
        leaving = true;
        game.setScreen(new LevelSelectScreen(game));
    }

    void update(float dt) {
        time += dt;
        flash = Math.max(0f, flash - dt * 1.5f);
        shake = Math.max(0f, shake - dt);
        hintTimer = Math.max(0f, hintTimer - dt);
        if (lockInput && !rawHeld()) lockInput = false;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            backToSelect();
            return;
        }

        for (int i = particles.size - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.vy -= 900f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0f) particles.removeIndex(i);
        }

        switch (state) {
            case PLAYING:
                updatePlaying(dt);
                break;
            case DEAD:
                deadTimer += dt;
                if ((deadTimer > 0.45f && jumpJustPressed()) || deadTimer > 1.1f) startRun();
                break;
            default: // WON
                wonTimer += dt;
                if (wonTimer > 0.8f && (jumpJustPressed() || Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
                    if (index + 1 < MainGame.LEVEL_COUNT) {
                        leaving = true;
                        game.setScreen(new LevelScreen(game, index + 1));
                    } else {
                        backToSelect();
                    }
                }
                break;
        }
    }

    void updatePlaying(float dt) {
        if (jumpJustPressed()) jumpBuffer = 0.12f;
        else jumpBuffer = Math.max(0f, jumpBuffer - dt);

        px += level.speed * dt;

        // modo e gravidade dependem da posicao
        float mid = px + SIZE / 2f;
        int newMode = modeAt(mid);
        if (newMode != mode) switchMode(newMode);
        if (!isCubeMode(mode)) {
            gravDir = 1;
        } else {
            int newDir = flippedAt(mid) ? -1 : 1;
            if (newDir != gravDir) switchGravity(newDir);
        }

        if (mode == LevelData.SHIP) updateShip(dt);
        else if (mode == LevelData.WAVE) updateWave(dt);
        else updateCube(dt);
        if (state != State.PLAYING) return;

        // espinhos
        float ax, ay, aw, ah;
        if (mode == LevelData.SHIP) { ax = px + 4f; aw = 32f; ay = py + 5f; ah = 18f; }
        else if (mode == LevelData.WAVE) { ax = px + (SIZE - WAVE_S) / 2f + 1f; aw = WAVE_S - 2f; ay = py + 1f; ah = WAVE_S - 2f; }
        else { ax = px + 5f; aw = SIZE - 10f; ay = py + 5f; ah = SIZE - 10f; }
        for (LevelData.Spike s : level.spikes) {
            if (s.x > px + 120f || s.x + SPIKE < px - 60f) continue;
            float hx = s.x + SPIKE * 0.3f, hw = SPIKE * 0.4f, hh = SPIKE * 0.55f;
            float hy = s.flip ? s.y - hh : s.y;
            if (ax < hx + hw && ax + aw > hx && ay < hy + hh && ay + ah > hy) {
                die();
                return;
            }
        }

        // rastro
        trailTimer -= dt;
        if (mode == LevelData.WAVE || trailTimer <= 0f) {
            trailTimer = 0.02f;
            Particle p = new Particle();
            if (mode == LevelData.WAVE) {
                p.x = px + SIZE / 2f - 6f;
                p.y = py + WAVE_S / 2f - 3f;
                p.vx = -40f; p.vy = 0f;
                p.life = 0.45f; p.size = 6f;
            } else {
                p.x = px + 2f;
                p.y = mode == LevelData.SHIP ? py + 6f + MathUtils.random(0f, 12f) : py + MathUtils.random(2f, SIZE - 2f);
                p.vx = -140f - MathUtils.random(0f, 60f);
                p.vy = MathUtils.random(-30f, 30f);
                p.life = 0.35f; p.size = MathUtils.random(4f, 8f);
            }
            p.col = MainGame.PALETTE[game.color1];
            particles.add(p);
        }

        // chegou ao final?
        if (px + SIZE / 2f >= level.length) win();
    }

    void updateCube(float dt) {
        float pm = mir(py, SIZE);        // fundo do cubo no espaco espelhado (gravidade sempre "para baixo")
        float vm = vy * gravDir;
        float prevBottom = pm;

        // orbe amarelo: precisa de um toque novo (buffer de 0.12s)
        if (jumpBuffer > 0f) {
            float cx = px + SIZE / 2f, cy = py + SIZE / 2f;
            for (LevelData.Orb o : level.orbs) {
                if (o.used) continue;
                float dx = cx - o.x, dy = cy - o.y;
                if (dx * dx + dy * dy < 46f * 46f) {
                    o.used = true;
                    vm = ORB_V;
                    onGround = false;
                    jumpBuffer = 0f;
                    game.orbSnd.play(0.6f);
                    burst(o.x, o.y, ORB_COLOR, 14, 260f);
                    break;
                }
            }
        }

        // pad: lanca para cima automaticamente
        for (LevelData.Pad p : level.pads) {
            float pyM = mir(p.y, 12f);
            if (px < p.x + 40f && px + SIZE > p.x && pm < pyM + 12f && pm + SIZE > pyM && vm < PAD_V * 0.9f) {
                vm = PAD_V;
                onGround = false;
                game.orbSnd.play(0.5f);
                burst(p.x + 20f, p.y + 6f, YELLOW, 10, 260f);
            }
        }

        if (onGround && jumpHeld()) {
            vm = JUMP_V;
            onGround = false;
            game.jumpSnd.play(0.5f);
        }

        vm += GRAVITY * dt;
        pm += vm * dt;
        onGround = false;

        if (pm <= GROUND) { pm = GROUND; vm = 0f; onGround = true; }
        if (mode == LevelData.CUBE_CEIL && pm + SIZE > CEIL) { pm = CEIL - SIZE; if (vm > 0f) vm = 0f; }

        for (LevelData.Block b : level.blocks) {
            if (b.x > px + 120f || b.x + b.w < px - 60f) continue;
            float bm = mir(b.y, b.h);
            if (px < b.x + b.w && px + SIZE > b.x && pm < bm + b.h && pm + SIZE > bm) {
                if (vm <= 0f && prevBottom >= bm + b.h - 10f) {
                    pm = bm + b.h; vm = 0f; onGround = true;
                } else {
                    die();
                    return;
                }
            }
        }

        py = mir(pm, SIZE);
        vy = vm * gravDir;

        if (!onGround) {
            rot -= 540f * dt * gravDir;
        } else {
            float target = Math.round(rot / 90f) * 90f;
            rot += (target - rot) * Math.min(1f, 20f * dt);
        }
    }

    void updateShip(float dt) {
        float prevPy = py;
        boolean up = jumpHeld();
        vy += (up ? 2400f : -1900f) * dt;
        vy = MathUtils.clamp(vy, -560f, 560f);
        py += vy * dt;

        if (py < GROUND) { py = GROUND; if (vy < 0f) vy = 0f; }
        if (py + SHIP_H > CEIL) { py = CEIL - SHIP_H; if (vy > 0f) vy = 0f; }

        for (LevelData.Block b : level.blocks) {
            if (b.x > px + 120f || b.x + b.w < px - 60f) continue;
            float hx = px + 4f, hw = 32f, hy = py + 5f, hh = 18f;
            if (hx < b.x + b.w && hx + hw > b.x && hy < b.y + b.h && hy + hh > b.y) {
                float prevHy = prevPy + 5f;
                if (prevHy >= b.y + b.h - 10f) {
                    py = b.y + b.h - 5f;
                    if (vy < 0f) vy = 0f;
                } else if (prevHy + hh <= b.y + 10f) {
                    py = b.y - hh - 5f;
                    if (vy > 0f) vy = 0f;
                } else {
                    die();
                    return;
                }
            }
        }

        float target = MathUtils.clamp(vy * 0.07f, -40f, 40f);
        rot += (target - rot) * Math.min(1f, 12f * dt);
    }

    void updateWave(float dt) {
        boolean up = jumpHeld();
        vy = (up ? 1f : -1f) * level.speed;      // 45 graus
        py += vy * dt;

        boolean slide = false;
        if (py < GROUND) { py = GROUND; slide = true; }
        if (py + WAVE_S > CEIL) { py = CEIL - WAVE_S; slide = true; }

        float hx = px + (SIZE - WAVE_S) / 2f;
        for (LevelData.Block b : level.blocks) {
            if (b.x > px + 120f || b.x + b.w < px - 60f) continue;
            if (hx < b.x + b.w && hx + WAVE_S > b.x && py < b.y + b.h && py + WAVE_S > b.y) {
                die();
                return;
            }
        }

        float target = slide ? 0f : (up ? 45f : -45f);
        rot += (target - rot) * Math.min(1f, 30f * dt);
    }

    // ------------------------------------------------------------------
    //  Desenho
    // ------------------------------------------------------------------
    void portal(ShapeRenderer sr, float x, Color c, float pulse) {
        sr.setColor(c.r, c.g, c.b, 0.18f + 0.12f * pulse);
        sr.ellipse(x - 45f, GROUND, 90f, CEIL - GROUND);
        sr.setColor(c.r, c.g, c.b, 0.9f);
        sr.ellipse(x - 20f, GROUND, 40f, CEIL - GROUND);
        sr.setColor(0.03f, 0.03f, 0.08f, 0.95f);
        sr.ellipse(x - 11f, GROUND + 8f, 22f, CEIL - GROUND - 16f);
    }

    void ceilingSeg(ShapeRenderer sr, float x0, float x1) {
        sr.setColor(0.04f, 0.04f, 0.08f, 1f);
        sr.rect(x0, CEIL, x1 - x0, H - CEIL + 60f);
        sr.setColor(1f, 1f, 1f, 0.9f);
        sr.rect(x0, CEIL - 1f, x1 - x0, 4f);
    }

    void text(String s, float x, float y, float scale, float r, float g, float b, float a, float width, int align) {
        game.font.getData().setScale(scale);
        game.font.setColor(r, g, b, a);
        game.font.draw(game.batch, s, x, y, width, align, false);
    }

    void draw() {
        ShapeRenderer sr = game.sr;
        Color c1 = MainGame.PALETTE[game.color1];
        Color c2 = MainGame.PALETTE[game.color2];

        float bpm = MainGame.TRACK_BPM[level.track];
        float beatPos = (state != State.DEAD && game.music.isPlaying()) ? game.music.getPosition() * bpm / 60f : time * bpm / 60f;
        float pulse = 1f - (beatPos - MathUtils.floor(beatPos));
        pulse *= pulse;

        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
        float sx = shake > 0f ? MathUtils.random(-1f, 1f) * shake * 14f : 0f;
        float sy = shake > 0f ? MathUtils.random(-1f, 1f) * shake * 14f : 0f;
        cam.position.set(px + CAM_OFFSET + sx, H / 2f + sy, 0f);
        cam.update();
        float camLeft = cam.position.x - W / 2f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // fundo
        float hue = (level.hue + px * 0.01f) % 360f;
        tmp.fromHsv(hue, 0.65f, 0.30f + 0.18f * pulse);
        tmp2.fromHsv((hue + 40f) % 360f, 0.75f, 0.12f + 0.08f * pulse);
        tmp.a = 1f; tmp2.a = 1f;
        sr.rect(camLeft - 30f, -30f, W + 60f, H + 60f, tmp2, tmp2, tmp, tmp);

        float off = camLeft * 0.3f;
        int baseIdx = MathUtils.floor(off / 160f);
        float shift = Art.mod(off, 160f);
        sr.setColor(1f, 1f, 1f, 0.05f + 0.04f * pulse);
        for (int i = -1; i < 7; i++) {
            float h = 120f + 130f * Math.abs(MathUtils.sin((baseIdx + i) * 1.7f));
            sr.rect(camLeft + i * 160f - shift, GROUND, 90f, h);
        }
        for (int i = 0; i < 14; i++) {
            float fx = camLeft + Art.mod(i * 131.7f - camLeft * 0.15f, W + 100f) - 50f;
            float fy = 110f + Art.mod(i * 57.3f, 330f);
            float s = 6f + (i % 4) * 4f;
            sr.setColor(1f, 1f, 1f, 0.07f + 0.08f * pulse);
            Art.rotRect(sr, fx, fy, 0, 0, s, s, time * 30f * (i % 3 + 1));
        }

        // chao
        sr.setColor(0.04f, 0.04f, 0.08f, 1f);
        sr.rect(camLeft - 30f, -30f, W + 60f, GROUND + 30f);
        sr.setColor(1f, 1f, 1f, 0.9f);
        sr.rect(camLeft - 30f, GROUND - 3f, W + 60f, 4f);
        sr.setColor(1f, 1f, 1f, 0.12f);
        float tick = Art.mod(camLeft, 80f);
        for (int i = 0; i < 12; i++) sr.rect(camLeft - tick + i * 80f, 0f, 3f, GROUND - 3f);

        // teto
        if (level.defaultMode != LevelData.CUBE) {
            ceilingSeg(sr, camLeft - 30f, camLeft + W + 30f);
        } else {
            for (LevelData.Zone z : level.zones) {
                if (z.mode != LevelData.CUBE && z.x1 > camLeft - 50f && z.x0 < camLeft + W + 50f) ceilingSeg(sr, z.x0, z.x1);
            }
        }

        // portais de modo
        for (LevelData.Zone z : level.zones) {
            if (z.x1 < camLeft - 100f || z.x0 > camLeft + W + 100f) continue;
            portal(sr, z.x0, modeColor(z.mode), pulse);
            boolean chained = false;
            for (LevelData.Zone o : level.zones) if (o != z && Math.abs(o.x0 - z.x1) < 2f) chained = true;
            if (!chained) portal(sr, z.x1, ORANGE, pulse);
        }
        // portais de gravidade
        for (LevelData.Grav g : level.gravs) {
            if (g.x < camLeft - 100f || g.x > camLeft + W + 100f) continue;
            portal(sr, g.x, g.flip ? YELLOW : BLUE, pulse);
        }

        // linha de chegada
        float fx = level.length;
        if (fx > camLeft - 150f && fx < camLeft + W + 150f) {
            sr.setColor(1f, 0.9f, 0.3f, 0.15f + 0.1f * pulse);
            sr.rect(fx - 60f, GROUND, 120f, CEIL - GROUND);
            int rows = (int) ((CEIL - GROUND) / 20f);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < 2; c++) {
                    float v = ((r + c) & 1) == 0 ? 1f : 0.05f;
                    sr.setColor(v, v, v, 1f);
                    sr.rect(fx + c * 20f - 20f, GROUND + r * 20f, 20f, 20f);
                }
            }
        }

        // blocos
        for (LevelData.Block b : level.blocks) {
            if (b.x > camLeft + W + 50f || b.x + b.w < camLeft - 50f) continue;
            sr.setColor(1f, 1f, 1f, 1f);
            sr.rect(b.x, b.y, b.w, b.h);
            sr.setColor(0.08f, 0.08f, 0.14f, 1f);
            sr.rect(b.x + 3f, b.y + 3f, b.w - 6f, b.h - 6f);
            sr.setColor(1f, 1f, 1f, 0.12f + 0.2f * pulse);
            sr.rect(b.x + 3f, b.y + 3f, b.w - 6f, b.h - 6f);
        }

        // pads
        for (LevelData.Pad p : level.pads) {
            if (p.x > camLeft + W + 50f || p.x + 40f < camLeft - 50f) continue;
            sr.setColor(YELLOW.r, YELLOW.g, YELLOW.b, 0.2f + 0.25f * pulse);
            sr.rect(p.x, p.top ? p.y - 16f : p.y + 12f, 40f, 16f);
            sr.setColor(1f, 1f, 1f, 1f);
            sr.rect(p.x - 1f, p.y - 1f, 42f, 14f);
            sr.setColor(YELLOW);
            sr.rect(p.x + 2f, p.y + 2f, 36f, 8f);
        }

        // espinhos
        for (LevelData.Spike s : level.spikes) {
            if (s.x > camLeft + W + 50f || s.x + SPIKE < camLeft - 50f) continue;
            float d = s.flip ? -1f : 1f;
            sr.setColor(1f, 1f, 1f, 1f);
            sr.triangle(s.x, s.y, s.x + SPIKE, s.y, s.x + SPIKE / 2f, s.y + d * SPIKE);
            sr.setColor(0.05f, 0.05f, 0.08f, 1f);
            sr.triangle(s.x + 8f, s.y + d * 3f, s.x + SPIKE - 8f, s.y + d * 3f, s.x + SPIKE / 2f, s.y + d * (SPIKE - 12f));
        }

        // aneis (marcadores e orbes)
        sr.set(ShapeRenderer.ShapeType.Line);
        for (LevelData.Marker m : level.markers) {
            if (m.x > camLeft + W + 50f || m.x < camLeft - 50f) continue;
            float r = 12f + 3f * MathUtils.sin(time * 6f);
            sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.9f);
            Art.ring(sr, m.x, m.y, r, 3);
            sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.5f);
            Art.ring(sr, m.x, m.y, r - 6f, 2);
        }
        for (LevelData.Orb o : level.orbs) {
            if (o.x > camLeft + W + 50f || o.x < camLeft - 50f) continue;
            float r = 22f + 2f * MathUtils.sin(time * 8f);
            sr.setColor(1f, 1f, 1f, o.used ? 0.15f : 0.95f);
            Art.ring(sr, o.x, o.y, r, 3);
        }
        sr.set(ShapeRenderer.ShapeType.Filled);

        for (LevelData.Orb o : level.orbs) {
            if (o.x > camLeft + W + 50f || o.x < camLeft - 50f) continue;
            float a = o.used ? 0.15f : 1f;
            sr.setColor(ORB_COLOR.r, ORB_COLOR.g, ORB_COLOR.b, (0.15f + 0.15f * pulse) * a);
            sr.circle(o.x, o.y, 30f);
            sr.setColor(ORB_COLOR.r, ORB_COLOR.g, ORB_COLOR.b, a);
            sr.circle(o.x, o.y, 14f);
            sr.setColor(1f, 1f, 1f, a);
            sr.circle(o.x, o.y, 6f);
        }
        for (LevelData.Marker m : level.markers) {
            if (m.x > camLeft + W + 50f || m.x < camLeft - 50f) continue;
            // seta apontando para o chao (ou para o teto nos marcadores de cima)
            float dir = m.y > (GROUND + CEIL) / 2f ? 1f : -1f;
            sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.5f + 0.4f * MathUtils.sin(time * 6f));
            sr.triangle(m.x - 6f, m.y + dir * 22f, m.x + 6f, m.y + dir * 22f, m.x, m.y + dir * 32f);
        }

        // particulas
        for (Particle p : particles) {
            sr.setColor(p.col.r, p.col.g, p.col.b, Math.min(1f, p.life * 2.5f));
            sr.rect(p.x, p.y, p.size, p.size);
        }

        // jogador
        if (state != State.DEAD) {
            if (mode == LevelData.SHIP) Art.ship(sr, px + SIZE / 2f, py + 10f, rot, c1, c2, game.icon, jumpHeld(), time);
            else if (mode == LevelData.WAVE) Art.wave(sr, px + SIZE / 2f, py + WAVE_S / 2f, rot, c1, c2);
            else Art.cube(sr, px + SIZE / 2f, py + SIZE / 2f, SIZE, rot, c1, c2, game.icon);
        }

        if (flash > 0f) {
            sr.setColor(1f, 1f, 1f, flash * 0.6f);
            sr.rect(camLeft - 30f, -30f, W + 60f, H + 60f);
        }
        sr.end();

        // ---- HUD ----
        float pct = state == State.WON ? 1f : MathUtils.clamp(px / level.length, 0f, 1f);
        sr.setProjectionMatrix(hudMatrix);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(1f, 1f, 1f, 0.25f);
        sr.rect(250f, H - 26f, 300f, 8f);
        sr.setColor(c1.r, c1.g, c1.b, 1f);
        sr.rect(250f, H - 26f, 300f * pct, 8f);
        sr.end();

        game.batch.setProjectionMatrix(hudMatrix);
        game.batch.begin();
        text("Fase " + (index + 1) + ": " + level.name, 16f, H - 12f, 1f, 1f, 1f, 1f, 0.9f, 0f, Align.left);
        text("Tentativa " + attempts, 16f, H - 32f, 0.8f, 1f, 1f, 1f, 0.7f, 0f, Align.left);
        text((int) (pct * 100f) + "%", 560f, H - 14f, 1f, 1f, 1f, 1f, 1f, 0f, Align.left);
        if (hintTimer > 0f && state == State.PLAYING) {
            text(hintText, 0f, H - 70f, 1.3f, 1f, 1f, 1f, Math.min(1f, hintTimer), W, Align.center);
        }
        if (state == State.DEAD) {
            text("Tentativa " + attempts + "   -   " + percent() + "%", 0f, H / 2f + 40f, 1.6f, 1f, 1f, 1f, 1f, W, Align.center);
        }
        if (state == State.WON) {
            text("FASE COMPLETA!", 0f, H / 2f + 80f, 2.6f, 1f, 0.9f, 0.3f, 1f, W, Align.center);
            text("Tentativas: " + attempts, 0f, H / 2f + 30f, 1.3f, 1f, 1f, 1f, 1f, W, Align.center);
            String next = index + 1 < MainGame.LEVEL_COUNT ? "ESPACO / ENTER = proxima fase" : "ESPACO / ENTER = voltar (voce zerou o jogo!)";
            text(next + "     ESC = fases", 0f, H / 2f - 10f, 1.1f, 1f, 1f, 1f, 0.9f, W, Align.center);
        }
        game.batch.end();
        game.font.getData().setScale(1f);
    }
}