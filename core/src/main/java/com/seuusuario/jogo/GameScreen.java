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

/**
 * Jogo: cubo (pula), orbes amarelos (pulo extra no ar), marcadores (onde pular)
 * e portais que transformam o cubo em nave (segure ESPACO para subir).
 */
public class GameScreen extends ScreenAdapter {

    static final float W = 800f, H = 480f;
    static final float GROUND = 80f, CEIL = 420f;
    static final float SIZE = 40f, SPIKE = 40f;
    static final float SHIP_W = 40f, SHIP_H = 28f;
    static final float SPEED = 420f, GRAVITY = -2400f, JUMP_V = 800f, ORB_V = 780f;
    static final float CAM_OFFSET = 250f;

    static final Color ORB_COLOR = new Color(1f, 0.92f, 0.15f, 1f);
    static final Color GREEN = new Color(0.2f, 1f, 0.45f, 1f);
    static final Color ORANGE = new Color(1f, 0.55f, 0.1f, 1f);
    static final Color CYAN = new Color(0.3f, 1f, 1f, 1f);

    enum State { PLAYING, DEAD }

    static class Block { float x, y, w, h; Block(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; } }
    static class Spike { float x, y; Spike(float x, float y) { this.x = x; this.y = y; } }
    static class Orb { float x, y; boolean used; Orb(float x, float y) { this.x = x; this.y = y; } }
    static class Marker { float x, y; Marker(float x, float y) { this.x = x; this.y = y; } }
    static class Zone { float x0, x1; }
    static class Particle { float x, y, vx, vy, life, size; Color col; }

    final MainGame game;
    final OrthographicCamera cam = new OrthographicCamera();
    final Viewport viewport = new FitViewport(W, H, cam);
    final Matrix4 hudMatrix = new Matrix4().setToOrtho2D(0, 0, W, H);
    final Color tmp = new Color(), tmp2 = new Color();

    State state = State.PLAYING;
    boolean leaving, lockInput = true, shipMode, onGround;
    float px, py, vy, rot;
    float time, deadTimer, nextSpawn, jumpBuffer, trailTimer, flash, shake, hintTimer;
    String hintText = "";
    int patternsSinceShip, nextShipAfter;

    final Array<Block> blocks = new Array<>();
    final Array<Spike> spikes = new Array<>();
    final Array<Orb> orbs = new Array<>();
    final Array<Marker> markers = new Array<>();
    final Array<Zone> zones = new Array<>();
    final Array<Particle> particles = new Array<>();

    public GameScreen(MainGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        game.restartMusic();
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
    void startRun() {
        px = 0; py = GROUND; vy = 0; rot = 0;
        onGround = true; shipMode = false; state = State.PLAYING;
        blocks.clear(); spikes.clear(); orbs.clear(); markers.clear(); zones.clear(); particles.clear();
        nextSpawn = 900f;
        patternsSinceShip = 0;
        nextShipAfter = MathUtils.random(5, 8);
        jumpBuffer = 0; flash = 0; shake = 0; hintTimer = 0;
        lockInput = true;
    }

    void die() {
        state = State.DEAD;
        deadTimer = 0;
        shake = 0.4f;
        game.music.stop();
        game.deathSnd.play(0.7f);
        game.submitScore((int) (px / 100f));
        burst(px + SIZE / 2f, py + SIZE / 2f, MainGame.PALETTE[game.color1], 18, 520f);
        burst(px + SIZE / 2f, py + SIZE / 2f, MainGame.PALETTE[game.color2], 12, 420f);
    }

    void switchMode(boolean ship) {
        shipMode = ship;
        vy = 0f;
        rot = 0f;
        onGround = false;
        flash = 0.4f;
        hintTimer = 2.5f;
        hintText = ship ? "NAVE!  Segure ESPACO para subir" : "CUBO!  Aperte ESPACO para pular";
        game.portalSnd.play(0.6f);
        burst(px + SIZE / 2f, py + SIZE / 2f, ship ? GREEN : ORANGE, 24, 420f);
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

    boolean inShipZone(float x) {
        for (Zone z : zones) if (x >= z.x0 && x <= z.x1) return true;
        return false;
    }

    // ------------------------------------------------------------------
    //  Atualização
    // ------------------------------------------------------------------
    void update(float dt) {
        time += dt;
        flash = Math.max(0f, flash - dt * 1.5f);
        shake = Math.max(0f, shake - dt);
        hintTimer = Math.max(0f, hintTimer - dt);
        if (lockInput && !rawHeld()) lockInput = false;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaving = true;
            game.setScreen(new MenuScreen(game));
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

        if (state == State.PLAYING) {
            updatePlaying(dt);
        } else {
            deadTimer += dt;
            if (deadTimer > 0.5f && jumpJustPressed()) {
                game.restartMusic();
                startRun();
            }
        }
    }

    void updatePlaying(float dt) {
        if (jumpJustPressed()) jumpBuffer = 0.12f;
        else jumpBuffer = Math.max(0f, jumpBuffer - dt);

        px += SPEED * dt;

        boolean inZone = inShipZone(px + SIZE / 2f);
        if (inZone != shipMode) switchMode(inZone);

        if (shipMode) updateShip(dt);
        else updateCube(dt);
        if (state != State.PLAYING) return;

        // espinhos
        float ax, ay, aw, ah;
        if (shipMode) { ax = px + 4f; aw = 32f; ay = py + 5f; ah = 18f; }
        else { ax = px + 5f; aw = SIZE - 10f; ay = py + 5f; ah = SIZE - 10f; }
        for (Spike s : spikes) {
            float hx = s.x + SPIKE * 0.3f, hw = SPIKE * 0.4f, hy = s.y, hh = SPIKE * 0.55f;
            if (ax < hx + hw && ax + aw > hx && ay < hy + hh && ay + ah > hy) {
                die();
                return;
            }
        }

        // rastro
        trailTimer -= dt;
        if (trailTimer <= 0f) {
            trailTimer = 0.02f;
            Particle p = new Particle();
            p.x = px + 2f;
            p.y = shipMode ? py + 6f + MathUtils.random(0f, 12f) : py + MathUtils.random(2f, SIZE - 2f);
            p.vx = -140f - MathUtils.random(0f, 60f);
            p.vy = MathUtils.random(-30f, 30f);
            p.life = 0.35f;
            p.size = MathUtils.random(4f, 8f);
            p.col = MainGame.PALETTE[game.color1];
            particles.add(p);
        }

        // gera fase a frente
        while (nextSpawn < px + 900f) {
            if (patternsSinceShip >= nextShipAfter) {
                float end = spawnShipZone(nextSpawn);
                nextSpawn = end + 520f;
                patternsSinceShip = 0;
                nextShipAfter = MathUtils.random(6, 9);
            } else {
                float end = spawnPattern(nextSpawn);
                nextSpawn = end + MathUtils.random(320f, 480f);
                patternsSinceShip++;
            }
        }

        // remove o que ficou para tras
        float left = px - 400f;
        for (int i = blocks.size - 1; i >= 0; i--) if (blocks.get(i).x + blocks.get(i).w < left) blocks.removeIndex(i);
        for (int i = spikes.size - 1; i >= 0; i--) if (spikes.get(i).x + SPIKE < left) spikes.removeIndex(i);
        for (int i = orbs.size - 1; i >= 0; i--) if (orbs.get(i).x < left) orbs.removeIndex(i);
        for (int i = markers.size - 1; i >= 0; i--) if (markers.get(i).x < left) markers.removeIndex(i);
        for (int i = zones.size - 1; i >= 0; i--) if (zones.get(i).x1 < left) zones.removeIndex(i);
    }

    void updateCube(float dt) {
        float prevBottom = py;

        // orbe amarelo: precisa de um toque novo (buffer de 0.12s)
        if (jumpBuffer > 0f) {
            float cx = px + SIZE / 2f, cy = py + SIZE / 2f;
            for (Orb o : orbs) {
                if (o.used) continue;
                float dx = cx - o.x, dy = cy - o.y;
                if (dx * dx + dy * dy < 46f * 46f) {
                    o.used = true;
                    vy = ORB_V;
                    onGround = false;
                    jumpBuffer = 0f;
                    game.orbSnd.play(0.6f);
                    burst(o.x, o.y, ORB_COLOR, 14, 260f);
                    break;
                }
            }
        }

        if (onGround && jumpHeld()) {
            vy = JUMP_V;
            onGround = false;
            game.jumpSnd.play(0.5f);
        }

        vy += GRAVITY * dt;
        py += vy * dt;
        onGround = false;

        if (py <= GROUND) { py = GROUND; vy = 0f; onGround = true; }

        for (Block b : blocks) {
            if (px < b.x + b.w && px + SIZE > b.x && py < b.y + b.h && py + SIZE > b.y) {
                if (vy <= 0f && prevBottom >= b.y + b.h - 10f) {
                    py = b.y + b.h; vy = 0f; onGround = true;
                } else {
                    die();
                    return;
                }
            }
        }

        if (!onGround) {
            rot -= 540f * dt;
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

        // pilares: desliza por cima/por baixo, morre se bater de frente
        for (Block b : blocks) {
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

    // ------------------------------------------------------------------
    //  Geracao da fase
    // ------------------------------------------------------------------
    /** Cria um trecho de obstaculos a partir de x e devolve o x final. */
    float spawnPattern(float x) {
        int max = Math.min(6, 2 + (int) (px / 1500f));
        switch (MathUtils.random(0, max)) {
            case 0: // espinho simples
                spikes.add(new Spike(x, GROUND));
                return x + SPIKE;
            case 1: // espinho duplo
                spikes.add(new Spike(x, GROUND));
                spikes.add(new Spike(x + SPIKE, GROUND));
                return x + SPIKE * 2f;
            case 2: { // plataforma com marcador "pule aqui"
                float h = 20f * MathUtils.random(2, 5);       // 40..100
                float w = 40f * MathUtils.random(3, 5);       // 120..200
                float bx = x + 220f;
                blocks.add(new Block(bx, GROUND, w, h));
                markers.add(new Marker(bx - 160f, GROUND + 85f));
                return bx + w;
            }
            case 3: // espinho triplo
                for (int i = 0; i < 3; i++) spikes.add(new Spike(x + i * SPIKE, GROUND));
                return x + SPIKE * 3f;
            case 4: // escada: bloco 40 -> espinho -> bloco 80
                blocks.add(new Block(x, GROUND, 80f, SIZE));
                spikes.add(new Spike(x + 80f, GROUND));
                blocks.add(new Block(x + 120f, GROUND, 80f, SIZE * 2f));
                markers.add(new Marker(x + 60f, GROUND + SIZE + 85f));
                return x + 200f;
            case 5: // espinho, depois bloco
                spikes.add(new Spike(x, GROUND));
                blocks.add(new Block(x + 220f, GROUND, 120f, SIZE));
                return x + 340f;
            default: { // tapete de espinhos: precisa do orbe amarelo
                int n = MathUtils.random(7, 9);
                float sx = x + 200f;
                for (int i = 0; i < n; i++) spikes.add(new Spike(sx + i * SPIKE, GROUND));
                orbs.add(new Orb(sx + n * SPIKE / 2f, GROUND + 135f));
                markers.add(new Marker(sx - 20f, GROUND + 85f));
                return sx + n * SPIKE;
            }
        }
    }

    /** Cria uma secao de nave (portal, pilares, portal de saida) e devolve o x final. */
    float spawnShipZone(float x) {
        Zone z = new Zone();
        z.x0 = x;
        float cx = x + 500f;
        float center = MathUtils.random(190f, 300f);
        int cols = MathUtils.random(8, 12);
        for (int i = 0; i < cols; i++) {
            float w = MathUtils.random(60f, 90f);
            float gap = MathUtils.random(150f, 190f);
            center = MathUtils.clamp(center + MathUtils.random(-90f, 90f), GROUND + gap / 2f + 10f, CEIL - gap / 2f - 10f);
            float botTop = center - gap / 2f, topBot = center + gap / 2f;
            int type = MathUtils.random(0, 5);
            if (type == 4) {
                blocks.add(new Block(cx, GROUND, w, botTop - GROUND));
            } else if (type == 5) {
                blocks.add(new Block(cx, topBot, w, CEIL - topBot));
            } else {
                blocks.add(new Block(cx, GROUND, w, botTop - GROUND));
                blocks.add(new Block(cx, topBot, w, CEIL - topBot));
            }
            cx += w + MathUtils.random(220f, 300f);
        }
        z.x1 = cx + 150f;
        zones.add(z);
        return z.x1;
    }

    // ------------------------------------------------------------------
    //  Desenho
    // ------------------------------------------------------------------
    @Override
    public void render(float delta) {
        update(Math.min(delta, 1f / 30f));
        if (leaving) return;
        draw();
    }

    void portal(ShapeRenderer sr, float x, Color c, float pulse) {
        sr.setColor(c.r, c.g, c.b, 0.18f + 0.12f * pulse);
        sr.ellipse(x - 45f, GROUND, 90f, CEIL - GROUND);
        sr.setColor(c.r, c.g, c.b, 0.9f);
        sr.ellipse(x - 20f, GROUND, 40f, CEIL - GROUND);
        sr.setColor(0.03f, 0.03f, 0.08f, 0.95f);
        sr.ellipse(x - 11f, GROUND + 8f, 22f, CEIL - GROUND - 16f);
    }

    void text(String s, float x, float y, float scale, float a, float width, int align) {
        game.font.getData().setScale(scale);
        game.font.setColor(1f, 1f, 1f, a);
        game.font.draw(game.batch, s, x, y, width, align, false);
    }

    void draw() {
        ShapeRenderer sr = game.sr;
        Color c1 = MainGame.PALETTE[game.color1];
        Color c2 = MainGame.PALETTE[game.color2];

        float bpm = MainGame.TRACK_BPM[game.track];
        float beatPos = (state == State.PLAYING && game.music.isPlaying()) ? game.music.getPosition() * bpm / 60f : time * bpm / 60f;
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

        // fundo (cor muda com a musica escolhida e com a distancia)
        float hue = (MainGame.TRACK_HUE[game.track] + px * 0.02f) % 360f;
        tmp.fromHsv(hue, 0.65f, 0.30f + 0.18f * pulse);
        tmp2.fromHsv((hue + 40f) % 360f, 0.75f, 0.12f + 0.08f * pulse);
        tmp.a = 1f; tmp2.a = 1f;
        sr.rect(camLeft - 30f, -30f, W + 60f, H + 60f, tmp2, tmp2, tmp, tmp);

        // colunas com parallax
        float off = camLeft * 0.3f;
        int baseIdx = MathUtils.floor(off / 160f);
        float shift = Art.mod(off, 160f);
        sr.setColor(1f, 1f, 1f, 0.05f + 0.04f * pulse);
        for (int i = -1; i < 7; i++) {
            float h = 120f + 130f * Math.abs(MathUtils.sin((baseIdx + i) * 1.7f));
            sr.rect(camLeft + i * 160f - shift, GROUND, 90f, h);
        }

        // quadradinhos flutuantes
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

        // zonas de nave: teto e portais
        for (Zone z : zones) {
            if (z.x1 < camLeft - 100f || z.x0 > camLeft + W + 100f) continue;
            sr.setColor(0.04f, 0.04f, 0.08f, 1f);
            sr.rect(z.x0, CEIL, z.x1 - z.x0, H - CEIL + 40f);
            sr.setColor(1f, 1f, 1f, 0.9f);
            sr.rect(z.x0, CEIL - 1f, z.x1 - z.x0, 4f);
            portal(sr, z.x0, GREEN, pulse);
            portal(sr, z.x1, ORANGE, pulse);
        }

        // blocos
        for (Block b : blocks) {
            if (b.x > camLeft + W + 50f || b.x + b.w < camLeft - 50f) continue;
            sr.setColor(1f, 1f, 1f, 1f);
            sr.rect(b.x, b.y, b.w, b.h);
            sr.setColor(0.08f, 0.08f, 0.14f, 1f);
            sr.rect(b.x + 3f, b.y + 3f, b.w - 6f, b.h - 6f);
            sr.setColor(1f, 1f, 1f, 0.12f + 0.2f * pulse);
            sr.rect(b.x + 3f, b.y + 3f, b.w - 6f, b.h - 6f);
        }

        // espinhos
        for (Spike s : spikes) {
            if (s.x > camLeft + W + 50f || s.x + SPIKE < camLeft - 50f) continue;
            sr.setColor(1f, 1f, 1f, 1f);
            sr.triangle(s.x, s.y, s.x + SPIKE, s.y, s.x + SPIKE / 2f, s.y + SPIKE);
            sr.setColor(0.05f, 0.05f, 0.08f, 1f);
            sr.triangle(s.x + 8f, s.y + 3f, s.x + SPIKE - 8f, s.y + 3f, s.x + SPIKE / 2f, s.y + SPIKE - 12f);
        }

        // aneis (marcadores e orbes), em modo de linha
        sr.set(ShapeRenderer.ShapeType.Line);
        for (Marker m : markers) {
            if (m.x > camLeft + W + 50f || m.x < camLeft - 50f) continue;
            float r = 12f + 3f * MathUtils.sin(time * 6f);
            sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.9f);
            Art.ring(sr, m.x, m.y, r, 3);
            sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.5f);
            Art.ring(sr, m.x, m.y, r - 6f, 2);
        }
        for (Orb o : orbs) {
            if (o.x > camLeft + W + 50f || o.x < camLeft - 50f) continue;
            float r = 22f + 2f * MathUtils.sin(time * 8f);
            sr.setColor(1f, 1f, 1f, o.used ? 0.15f : 0.95f);
            Art.ring(sr, o.x, o.y, r, 3);
        }
        sr.set(ShapeRenderer.ShapeType.Filled);

        // discos dos orbes e setas dos marcadores
        for (Orb o : orbs) {
            if (o.x > camLeft + W + 50f || o.x < camLeft - 50f) continue;
            float a = o.used ? 0.15f : 1f;
            sr.setColor(ORB_COLOR.r, ORB_COLOR.g, ORB_COLOR.b, (0.15f + 0.15f * pulse) * a);
            sr.circle(o.x, o.y, 30f);
            sr.setColor(ORB_COLOR.r, ORB_COLOR.g, ORB_COLOR.b, a);
            sr.circle(o.x, o.y, 14f);
            sr.setColor(1f, 1f, 1f, a);
            sr.circle(o.x, o.y, 6f);
        }
        for (Marker m : markers) {
            if (m.x > camLeft + W + 50f || m.x < camLeft - 50f) continue;
            sr.setColor(CYAN.r, CYAN.g, CYAN.b, 0.5f + 0.4f * MathUtils.sin(time * 6f));
            sr.triangle(m.x - 6f, m.y - 22f, m.x + 6f, m.y - 22f, m.x, m.y - 32f);
        }

        // particulas
        for (Particle p : particles) {
            sr.setColor(p.col.r, p.col.g, p.col.b, Math.min(1f, p.life * 2.5f));
            sr.rect(p.x, p.y, p.size, p.size);
        }

        // jogador
        if (state == State.PLAYING) {
            if (shipMode) Art.ship(sr, px + SIZE / 2f, py + 10f, rot, c1, c2, game.icon, jumpHeld(), time);
            else Art.cube(sr, px + SIZE / 2f, py + SIZE / 2f, SIZE, rot, c1, c2, game.icon);
        }

        // flash de transicao
        if (flash > 0f) {
            sr.setColor(1f, 1f, 1f, flash * 0.6f);
            sr.rect(camLeft - 30f, -30f, W + 60f, H + 60f);
        }
        sr.end();

        // HUD
        game.batch.setProjectionMatrix(hudMatrix);
        game.batch.begin();
        text("Score: " + (int) (px / 100f) + "   Best: " + game.best, 16f, H - 14f, 1.3f, 1f, 0f, Align.left);
        text("Musica: " + MainGame.TRACK_NAMES[game.track], 0f, H - 14f, 0.9f, 0.7f, W - 16f, Align.right);
        if (hintTimer > 0f && state == State.PLAYING) {
            text(hintText, 0f, H - 70f, 1.4f, Math.min(1f, hintTimer), W, Align.center);
        }
        if (state == State.DEAD) {
            text("Voce morreu!  Score: " + (int) (px / 100f), 0f, H / 2f + 70f, 1.8f, 1f, W, Align.center);
            text("ESPACO / CLIQUE = tentar de novo     ESC = menu", 0f, H / 2f + 20f, 1.2f, 1f, W, Align.center);
        }
        game.batch.end();
        game.font.getData().setScale(1f);
    }
}
