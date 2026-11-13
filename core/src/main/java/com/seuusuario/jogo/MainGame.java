package com.seuusuario.jogo;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/** Classe principal: guarda recursos compartilhados, escolhas do jogador e progresso das fases. */
public class MainGame extends Game {

    /** Coloque true para testar: libera todas as fases. */
    public static final boolean UNLOCK_ALL = false;

    public static final int LEVEL_COUNT = 5;

    public static final String[] TRACK_NAMES = {"Neon Rush", "Sunset Drive", "Cyber Chase"};
    public static final String[] TRACK_FILES = {"music0.wav", "music1.wav", "music2.wav"};
    public static final float[] TRACK_BPM = {128f, 112f, 150f};
    public static final float[] TRACK_HUE = {210f, 315f, 130f};

    public static final Color[] PALETTE = {
        new Color(0.10f, 0.90f, 1.00f, 1f),   // ciano
        new Color(0.20f, 1.00f, 0.30f, 1f),   // verde
        new Color(1.00f, 0.92f, 0.10f, 1f),   // amarelo
        new Color(1.00f, 0.55f, 0.10f, 1f),   // laranja
        new Color(1.00f, 0.20f, 0.25f, 1f),   // vermelho
        new Color(1.00f, 0.35f, 0.75f, 1f),   // rosa
        new Color(0.65f, 0.30f, 1.00f, 1f),   // roxo
        new Color(0.20f, 0.40f, 1.00f, 1f),   // azul
        new Color(1.00f, 1.00f, 1.00f, 1f),   // branco
        new Color(0.05f, 0.05f, 0.08f, 1f)    // preto
    };
    public static final int ICON_COUNT = 4;

    // Escolhas do jogador
    public int color1 = 0, color2 = 7, icon = 1, track = 0, best = 0;

    // Progresso das fases
    public int[] levelBest = new int[LEVEL_COUNT];   // melhor porcentagem (0..100)
    public int levelsUnlocked = 1;

    // Recursos compartilhados
    public ShapeRenderer sr;
    public SpriteBatch batch;
    public BitmapFont font;
    public Music music;
    public Sound jumpSnd, deathSnd, orbSnd, portalSnd;
    Preferences prefs;
    int loadedTrack = -1;

    @Override
    public void create() {
        sr = new ShapeRenderer();
        sr.setAutoShapeType(true);   // necessario para sr.set(Line/Filled) funcionar
        batch = new SpriteBatch();
        font = new BitmapFont();

        jumpSnd = Gdx.audio.newSound(Gdx.files.internal("jump.wav"));
        deathSnd = Gdx.audio.newSound(Gdx.files.internal("death.wav"));
        orbSnd = Gdx.audio.newSound(Gdx.files.internal("orb.wav"));
        portalSnd = Gdx.audio.newSound(Gdx.files.internal("portal.wav"));

        prefs = Gdx.app.getPreferences("geometrydash2");
        color1 = MathUtils.clamp(prefs.getInteger("color1", 0), 0, PALETTE.length - 1);
        color2 = MathUtils.clamp(prefs.getInteger("color2", 7), 0, PALETTE.length - 1);
        icon = MathUtils.clamp(prefs.getInteger("icon", 1), 0, ICON_COUNT - 1);
        track = MathUtils.clamp(prefs.getInteger("track", 0), 0, TRACK_FILES.length - 1);
        best = prefs.getInteger("best", 0);
        levelsUnlocked = MathUtils.clamp(prefs.getInteger("unlocked", 1), 1, LEVEL_COUNT);
        for (int i = 0; i < LEVEL_COUNT; i++) levelBest[i] = prefs.getInteger("lvbest" + i, 0);

        loadTrack(track);
        setScreen(new MenuScreen(this));
    }

    // ---------------- musica ----------------
    private void loadMusicFile(int i) {
        boolean wasPlaying = music != null && music.isPlaying();
        if (music != null) { music.stop(); music.dispose(); }
        music = Gdx.audio.newMusic(Gdx.files.internal(TRACK_FILES[i]));
        music.setLooping(true);
        music.setVolume(0.6f);
        loadedTrack = i;
        if (wasPlaying) music.play();
    }

    /** Escolhe a musica do menu / modo infinito (e lembra a escolha). */
    public void loadTrack(int i) {
        loadMusicFile(i);
        track = i;
    }

    /** Usa uma musica temporariamente (fases), sem mudar a escolha do menu. */
    public void useTrack(int i) {
        if (loadedTrack != i) loadMusicFile(i);
    }

    /** Volta para a musica escolhida no menu. */
    public void useMenuTrack() {
        useTrack(track);
    }

    public void restartMusic() {
        music.stop();
        music.play();
    }

    // ---------------- progresso ----------------
    public boolean isUnlocked(int level) {
        return UNLOCK_ALL || level < levelsUnlocked;
    }

    public void submitLevel(int level, int percent) {
        if (percent > levelBest[level]) levelBest[level] = percent;
        if (percent >= 100 && level + 1 >= levelsUnlocked) levelsUnlocked = Math.min(LEVEL_COUNT, level + 2);
        saveSettings();
    }

    public void saveSettings() {
        prefs.putInteger("color1", color1);
        prefs.putInteger("color2", color2);
        prefs.putInteger("icon", icon);
        prefs.putInteger("track", track);
        prefs.putInteger("best", best);
        prefs.putInteger("unlocked", levelsUnlocked);
        for (int i = 0; i < LEVEL_COUNT; i++) prefs.putInteger("lvbest" + i, levelBest[i]);
        prefs.flush();
    }

    public void submitScore(int score) {
        if (score > best) { best = score; saveSettings(); }
    }

    @Override
    public void dispose() {
        super.dispose();
        sr.dispose();
        batch.dispose();
        font.dispose();
        if (music != null) music.dispose();
        jumpSnd.dispose();
        deathSnd.dispose();
        orbSnd.dispose();
        portalSnd.dispose();
    }
}