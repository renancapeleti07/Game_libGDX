package com.seuusuario.jogo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Tela inicial: personaliza o cubo (2 cores + icone) e escolhe entre 3 musicas. */
public class MenuScreen extends ScreenAdapter {

    static final float W = 800f, H = 480f;

    // Layout
    static final float SW_X = 52f, SW_SIZE = 28f, SW_STEP = 36f;
    static final float ROW1_Y = 166f, ROW2_Y = 114f, ICON_Y = 52f, ICON_SIZE = 40f, ICON_STEP = 52f;
    static final float CARD_X = 460f, CARD_W = 300f, CARD_H = 56f;
    static final float[] CARD_Y = {322f, 252f, 182f};
    static final float PLAY_X = 460f, PLAY_Y = 70f, PLAY_W = 300f, PLAY_H = 80f;

    final MainGame game;
    final OrthographicCamera cam = new OrthographicCamera();
    final Viewport viewport = new FitViewport(W, H, cam);
    final Vector2 touch = new Vector2();
    final Color tmp = new Color();
    final Color tmp2 = new Color();
    float t;
    boolean leaving;

    public MenuScreen(MainGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        game.restartMusic();   // toca a musica escolhida como previa
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    // ------------------------------------------------------------------
    @Override
    public void render(float delta) {
        t += Math.min(delta, 1f / 30f);
        handleInput();
        if (leaving) return;
        draw();
    }

    boolean hit(float x, float y, float w, float h) {
        return touch.x >= x && touch.x <= x + w && touch.y >= y && touch.y <= y + h;
    }

    void play() {
        leaving = true;
        game.saveSettings();
        game.setScreen(new GameScreen(game));
    }

    void selectTrack(int i) {
        if (i == game.track) return;
        game.loadTrack(i);
        game.restartMusic();
        game.saveSettings();
    }

    void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            play();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectTrack(0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) selectTrack(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectTrack(2);

        if (Gdx.input.justTouched()) {
            touch.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(touch);

            for (int i = 0; i < MainGame.PALETTE.length; i++) {
                if (hit(SW_X + i * SW_STEP, ROW1_Y, SW_SIZE, SW_SIZE)) game.color1 = i;
                if (hit(SW_X + i * SW_STEP, ROW2_Y, SW_SIZE, SW_SIZE)) game.color2 = i;
            }
            for (int i = 0; i < MainGame.ICON_COUNT; i++) {
                if (hit(SW_X + i * ICON_STEP, ICON_Y, ICON_SIZE, ICON_SIZE)) game.icon = i;
            }
            for (int i = 0; i < 3; i++) {
                if (hit(CARD_X, CARD_Y[i], CARD_W, CARD_H)) selectTrack(i);
            }
            if (hit(PLAY_X, PLAY_Y, PLAY_W, PLAY_H)) play();
            game.saveSettings();
        }
    }

    // ------------------------------------------------------------------
    void text(String s, float x, float y, float scale, float r, float g, float b, float a, float width, int align) {
        game.font.getData().setScale(scale);
        game.font.setColor(r, g, b, a);
        game.font.draw(game.batch, s, x, y, width, align, false);
    }

    void draw() {
        ShapeRenderer sr = game.sr;
        Color c1 = MainGame.PALETTE[game.color1];
        Color c2 = MainGame.PALETTE[game.color2];

        float bpm = MainGame.TRACK_BPM[game.track];
        float beatPos = game.music.isPlaying() ? game.music.getPosition() * bpm / 60f : t * bpm / 60f;
        float pulse = 1f - (beatPos - MathUtils.floor(beatPos));
        pulse *= pulse;

        // mouse para efeito de hover
        touch.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(touch);

        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
        cam.update();
        sr.setProjectionMatrix(cam.combined);
        game.batch.setProjectionMatrix(cam.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float hue = (MainGame.TRACK_HUE[game.track] + t * 6f) % 360f;

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // fundo
        tmp.fromHsv(hue, 0.65f, 0.30f + 0.15f * pulse);
        tmp2.fromHsv((hue + 40f) % 360f, 0.75f, 0.12f + 0.06f * pulse);
        tmp.a = 1f; tmp2.a = 1f;
        sr.rect(0, 0, W, H, tmp2, tmp2, tmp, tmp);

        // quadradinhos flutuando
        for (int i = 0; i < 18; i++) {
            float fx = Art.mod(i * 131.7f - t * (14f + (i % 4) * 6f), W + 60f) - 30f;
            float fy = 20f + Art.mod(i * 57.3f, 440f);
            float s = 6f + (i % 4) * 5f;
            sr.setColor(1f, 1f, 1f, 0.06f + 0.06f * pulse);
            Art.rotRect(sr, fx, fy, 0, 0, s, s, t * 25f * (i % 3 + 1));
        }

        // paineis
        sr.setColor(0f, 0f, 0f, 0.38f);
        sr.rect(40, 235, 380, 150);
        sr.rect(40, 40, 380, 180);

        // preview: cubo pulando
        float phase = (t * 3f) % MathUtils.PI;
        float hop = MathUtils.sin(phase) * 40f;
        sr.setColor(1f, 1f, 1f, 0.5f);
        sr.rect(52, 259, 356, 3);
        Art.cube(sr, 130, 262 + 35 + hop, 70f, -(phase / MathUtils.PI) * 180f, c1, c2, game.icon);
        // preview: nave
        Art.ship(sr, 315, 315 + MathUtils.sin(t * 2f) * 10f, MathUtils.sin(t * 2f + 1f) * 12f, c1, c2, game.icon, true, t);

        // paleta cor 1 e cor 2
        for (int i = 0; i < MainGame.PALETTE.length; i++) {
            Color c = MainGame.PALETTE[i];
            float x = SW_X + i * SW_STEP;
            if (i == game.color1) { sr.setColor(Color.WHITE); sr.rect(x - 3, ROW1_Y - 3, SW_SIZE + 6, SW_SIZE + 6); }
            sr.setColor(c);
            sr.rect(x, ROW1_Y, SW_SIZE, SW_SIZE);
            if (i == game.color2) { sr.setColor(Color.WHITE); sr.rect(x - 3, ROW2_Y - 3, SW_SIZE + 6, SW_SIZE + 6); }
            sr.setColor(c);
            sr.rect(x, ROW2_Y, SW_SIZE, SW_SIZE);
        }

        // botoes de icone
        for (int i = 0; i < MainGame.ICON_COUNT; i++) {
            float x = SW_X + i * ICON_STEP;
            sr.setColor(1f, 1f, 1f, i == game.icon ? 1f : 0.25f);
            sr.rect(x - 3, ICON_Y - 3, ICON_SIZE + 6, ICON_SIZE + 6);
            sr.setColor(0.08f, 0.08f, 0.14f, 1f);
            sr.rect(x, ICON_Y, ICON_SIZE, ICON_SIZE);
            Art.cube(sr, x + ICON_SIZE / 2f, ICON_Y + ICON_SIZE / 2f, 28f, 0f, c1, c2, i);
        }

        // cartoes de musica
        for (int i = 0; i < 3; i++) {
            boolean sel = i == game.track;
            boolean over = hit(CARD_X, CARD_Y[i], CARD_W, CARD_H);
            if (sel) { sr.setColor(Color.WHITE); sr.rect(CARD_X - 3, CARD_Y[i] - 3, CARD_W + 6, CARD_H + 6); }
            tmp.fromHsv(MainGame.TRACK_HUE[i], 0.7f, sel ? 0.55f : (over ? 0.40f : 0.28f));
            tmp.a = 1f;
            sr.setColor(tmp);
            sr.rect(CARD_X, CARD_Y[i], CARD_W, CARD_H);
            if (sel) { // equalizador
                for (int k = 0; k < 6; k++) {
                    float hgt = 6f + (0.4f + 0.6f * Math.abs(MathUtils.sin(t * 5f + k * 1.3f))) * (10f + 22f * pulse);
                    sr.setColor(1f, 1f, 1f, 0.9f);
                    sr.rect(CARD_X + CARD_W - 90f + k * 13f, CARD_Y[i] + 10f, 8f, hgt);
                }
            }
        }

        // botao jogar
        boolean overPlay = hit(PLAY_X, PLAY_Y, PLAY_W, PLAY_H);
        sr.setColor(Color.WHITE);
        sr.rect(PLAY_X - 3, PLAY_Y - 3, PLAY_W + 6, PLAY_H + 6);
        sr.setColor(0.12f + (overPlay ? 0.08f : 0f) + 0.08f * pulse, 0.75f + (overPlay ? 0.1f : 0f), 0.28f, 1f);
        sr.rect(PLAY_X, PLAY_Y, PLAY_W, PLAY_H);
        sr.end();

        // textos
        game.batch.begin();
        tmp.fromHsv((hue + 60f) % 360f, 0.35f, 1f);
        text("GEOMETRY DASH", 0, 466, 2.6f, tmp.r, tmp.g, tmp.b, 1f, W, Align.center);
        text("Recorde: " + game.best, 0, 424, 1.1f, 1f, 1f, 1f, 0.85f, W, Align.center);

        text("SEU CUBO E NAVE", 52, 376, 0.9f, 1f, 1f, 1f, 0.8f, 0, Align.left);
        text("COR 1", SW_X, ROW1_Y + 48, 0.9f, 1f, 1f, 1f, 0.8f, 0, Align.left);
        text("COR 2", SW_X, ROW2_Y + 48, 0.9f, 1f, 1f, 1f, 0.8f, 0, Align.left);
        text("ICONE", SW_X, ICON_Y + 58, 0.9f, 1f, 1f, 1f, 0.8f, 0, Align.left);

        text("MUSICA  (teclas 1, 2, 3)", CARD_X, 398, 0.9f, 1f, 1f, 1f, 0.8f, 0, Align.left);
        for (int i = 0; i < 3; i++) {
            text(MainGame.TRACK_NAMES[i], CARD_X + 16, CARD_Y[i] + 38, 1.2f, 1f, 1f, 1f, 1f, 0, Align.left);
            text((int) MainGame.TRACK_BPM[i] + " BPM", CARD_X + 16, CARD_Y[i] + 19, 0.8f, 1f, 1f, 1f, 0.7f, 0, Align.left);
        }

        text("JOGAR", PLAY_X, PLAY_Y + PLAY_H / 2f + 12f, 2.2f, 1f, 1f, 1f, 1f, PLAY_W, Align.center);
        text("ENTER / ESPACO", PLAY_X, PLAY_Y - 8, 0.8f, 1f, 1f, 1f, 0.7f, PLAY_W, Align.center);

        text("Cubo: ESPACO pula   |   Orbe amarelo: aperte no ar   |   Nave: segure ESPACO   |   ESC: menu",
             0, 22, 0.75f, 1f, 1f, 1f, 0.7f, W, Align.center);
        game.batch.end();
        game.font.getData().setScale(1f);
    }
}
