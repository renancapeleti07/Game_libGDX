package com.seuusuario.jogo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Lista as fases, mostra progresso (melhor %) e bloqueio. */
public class LevelSelectScreen extends ScreenAdapter {

    static final float W = 800f, H = 480f;
    static final float CARD_X = 80f, CARD_W = 640f, CARD_H = 56f, CARD_TOP = 416f, CARD_STEP = 64f;

    final MainGame game;
    final OrthographicCamera cam = new OrthographicCamera();
    final Viewport viewport = new FitViewport(W, H, cam);
    final Vector2 touch = new Vector2();
    final Color tmp = new Color(), tmp2 = new Color();
    int selected;
    float t;
    boolean leaving;

    public LevelSelectScreen(MainGame game) {
        this.game = game;
        // comeca na primeira fase ainda nao completa
        selected = 0;
        for (int i = 0; i < MainGame.LEVEL_COUNT; i++) {
            if (game.isUnlocked(i)) {
                selected = i;
                if (game.levelBest[i] < 100) break;
            }
        }
    }

    @Override
    public void show() {
        game.useMenuTrack();
        game.restartMusic();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    float cardY(int i) { return CARD_TOP - CARD_H - i * CARD_STEP; }

    boolean hit(float x, float y, float w, float h) {
        return touch.x >= x && touch.x <= x + w && touch.y >= y && touch.y <= y + h;
    }

    void startLevel(int i) {
        if (!game.isUnlocked(i)) return;
        leaving = true;
        game.setScreen(new LevelScreen(game, i));
    }

    void back() {
        leaving = true;
        game.setScreen(new MenuScreen(game));
    }

    @Override
    public void render(float delta) {
        t += Math.min(delta, 1f / 30f);
        handleInput();
        if (leaving) return;
        draw();
    }

    void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { back(); return; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W))
            selected = (selected + MainGame.LEVEL_COUNT - 1) % MainGame.LEVEL_COUNT;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S))
            selected = (selected + 1) % MainGame.LEVEL_COUNT;
        for (int i = 0; i < MainGame.LEVEL_COUNT && i < 9; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) { selected = i; startLevel(i); return; }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            startLevel(selected);
            return;
        }
        if (Gdx.input.justTouched()) {
            touch.set(Gdx.input.getX(), Gdx.input.getY());
            viewport.unproject(touch);
            for (int i = 0; i < MainGame.LEVEL_COUNT; i++) {
                if (hit(CARD_X, cardY(i), CARD_W, CARD_H)) {
                    selected = i;
                    startLevel(i);
                    return;
                }
            }
            if (hit(20f, 14f, 110f, 30f)) back();
        }
    }

    void text(String s, float x, float y, float scale, float r, float g, float b, float a, float width, int align) {
        game.font.getData().setScale(scale);
        game.font.setColor(r, g, b, a);
        game.font.draw(game.batch, s, x, y, width, align, false);
    }

    void draw() {
        ShapeRenderer sr = game.sr;

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
        tmp.fromHsv(hue, 0.65f, 0.28f);
        tmp2.fromHsv((hue + 40f) % 360f, 0.75f, 0.10f);
        tmp.a = 1f; tmp2.a = 1f;
        sr.rect(0, 0, W, H, tmp2, tmp2, tmp, tmp);

        for (int i = 0; i < 18; i++) {
            float fx = Art.mod(i * 131.7f - t * (14f + (i % 4) * 6f), W + 60f) - 30f;
            float fy = 20f + Art.mod(i * 57.3f, 440f);
            float s = 6f + (i % 4) * 5f;
            sr.setColor(1f, 1f, 1f, 0.06f);
            Art.rotRect(sr, fx, fy, 0, 0, s, s, t * 25f * (i % 3 + 1));
        }

        for (int i = 0; i < MainGame.LEVEL_COUNT; i++) {
            float y = cardY(i);
            boolean unlocked = game.isUnlocked(i);
            boolean sel = i == selected;
            boolean over = hit(CARD_X, y, CARD_W, CARD_H);
            if (over && Gdx.input.getDeltaX() != 0) selected = i;

            if (sel) { sr.setColor(1f, 1f, 1f, 1f); sr.rect(CARD_X - 3f, y - 3f, CARD_W + 6f, CARD_H + 6f); }
            tmp.fromHsv(LevelData.HUES[i], unlocked ? 0.7f : 0.15f, unlocked ? (sel ? 0.55f : 0.35f) : 0.22f);
            tmp.a = 1f;
            sr.setColor(tmp);
            sr.rect(CARD_X, y, CARD_W, CARD_H);

            // caixa do numero
            tmp.fromHsv(LevelData.HUES[i], unlocked ? 0.8f : 0.1f, unlocked ? 0.85f : 0.35f);
            tmp.a = 1f;
            sr.setColor(tmp);
            sr.rect(CARD_X, y, CARD_H, CARD_H);

            // barra de progresso
            float bx = CARD_X + CARD_W - 170f, by = y + 10f;
            sr.setColor(0f, 0f, 0f, 0.4f);
            sr.rect(bx, by, 150f, 8f);
            if (unlocked) {
                float p = game.levelBest[i] / 100f;
                if (game.levelBest[i] >= 100) sr.setColor(1f, 0.9f, 0.3f, 1f);
                else sr.setColor(0.3f, 1f, 0.5f, 1f);
                sr.rect(bx, by, 150f * p, 8f);
            }
        }
        sr.end();

        game.batch.begin();
        text("ESCOLHA A FASE", 0, 466, 2.2f, 1f, 1f, 1f, 1f, W, Align.center);
        for (int i = 0; i < MainGame.LEVEL_COUNT; i++) {
            float y = cardY(i);
            boolean unlocked = game.isUnlocked(i);
            float a = unlocked ? 1f : 0.45f;
            text("" + (i + 1), CARD_X, y + 38f, 1.8f, 0f, 0f, 0f, 1f, CARD_H, Align.center);
            text(LevelData.NAMES[i], CARD_X + CARD_H + 14f, y + 38f, 1.2f, 1f, 1f, 1f, a, 0f, Align.left);
            text(LevelData.MECHANICS[i], CARD_X + CARD_H + 14f, y + 18f, 0.75f, 1f, 1f, 1f, 0.75f * a, 0f, Align.left);
            if (!unlocked) {
                text("BLOQUEADA", CARD_X + CARD_W - 170f, y + 40f, 0.9f, 1f, 0.6f, 0.6f, 1f, 150f, Align.right);
            } else if (game.levelBest[i] >= 100) {
                text("COMPLETA", CARD_X + CARD_W - 170f, y + 40f, 0.9f, 1f, 0.9f, 0.3f, 1f, 150f, Align.right);
            } else {
                text("Melhor: " + game.levelBest[i] + "%", CARD_X + CARD_W - 170f, y + 40f, 0.9f, 1f, 1f, 1f, 1f, 150f, Align.right);
            }
        }
        text("< Voltar (ESC)", 20f, 38f, 0.9f, 1f, 1f, 1f, 0.85f, 0f, Align.left);
        text("Setas / mouse: escolher    ENTER / clique / 1-" + MainGame.LEVEL_COUNT + ": jogar",
             0, 38f, 0.85f, 1f, 1f, 1f, 0.8f, W, Align.center);
        game.batch.end();
        game.font.getData().setScale(1f);
    }
}