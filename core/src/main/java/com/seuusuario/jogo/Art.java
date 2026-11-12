package com.seuusuario.jogo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/** Funções de desenho compartilhadas (cubo, nave, anéis). Use com ShapeRenderer em modo Filled. */
public final class Art {
    private Art() {}

    static final Color DARK = new Color(0.04f, 0.04f, 0.08f, 1f);

    public static float lum(Color c) { return 0.299f * c.r + 0.587f * c.g + 0.114f * c.b; }

    public static float mod(float a, float b) { return ((a % b) + b) % b; }

    /** Retângulo w x h centrado em (cx+ox, cy+oy), girando em torno de (cx, cy). */
    public static void rotRect(ShapeRenderer sr, float cx, float cy, float ox, float oy, float w, float h, float deg) {
        sr.rect(cx + ox - w / 2f, cy + oy - h / 2f, w / 2f - ox, h / 2f - oy, w, h, 1f, 1f, deg);
    }

    /** Cubo do jogador, centrado em (cx, cy). */
    public static void cube(ShapeRenderer sr, float cx, float cy, float size, float deg, Color c1, Color c2, int icon) {
        sr.setColor(Color.WHITE);
        rotRect(sr, cx, cy, 0, 0, size, size, deg);
        sr.setColor(c1);
        rotRect(sr, cx, cy, 0, 0, size * 0.88f, size * 0.88f, deg);
        sr.setColor(c2);
        rotRect(sr, cx, cy, 0, 0, size * 0.56f, size * 0.56f, deg);

        sr.setColor(lum(c2) > 0.5f ? DARK : Color.WHITE);
        switch (icon) {
            case 0: // quadradinho
                rotRect(sr, cx, cy, 0, 0, size * 0.22f, size * 0.22f, deg);
                break;
            case 1: // carinha
                rotRect(sr, cx, cy, -size * 0.11f, size * 0.07f, size * 0.09f, size * 0.12f, deg);
                rotRect(sr, cx, cy, size * 0.11f, size * 0.07f, size * 0.09f, size * 0.12f, deg);
                rotRect(sr, cx, cy, 0, -size * 0.10f, size * 0.28f, size * 0.06f, deg);
                break;
            case 2: // cruz
                rotRect(sr, cx, cy, 0, 0, size * 0.32f, size * 0.08f, deg);
                rotRect(sr, cx, cy, 0, 0, size * 0.08f, size * 0.32f, deg);
                break;
            default: // anel
                rotRect(sr, cx, cy, 0, 0, size * 0.28f, size * 0.28f, deg);
                sr.setColor(c2);
                rotRect(sr, cx, cy, 0, 0, size * 0.16f, size * 0.16f, deg);
                break;
        }
    }

    private static float wx(float cx, float lx, float ly, float cs, float sn) { return cx + lx * cs - ly * sn; }
    private static float wy(float cy, float lx, float ly, float cs, float sn) { return cy + lx * sn + ly * cs; }

    private static void tri(ShapeRenderer sr, float cx, float cy, float cs, float sn,
                            float x1, float y1, float x2, float y2, float x3, float y3) {
        sr.triangle(wx(cx, x1, y1, cs, sn), wy(cy, x1, y1, cs, sn),
                    wx(cx, x2, y2, cs, sn), wy(cy, x2, y2, cs, sn),
                    wx(cx, x3, y3, cs, sn), wy(cy, x3, y3, cs, sn));
    }

    private static void quad(ShapeRenderer sr, float cx, float cy, float cs, float sn,
                             float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        tri(sr, cx, cy, cs, sn, x1, y1, x2, y2, x3, y3);
        tri(sr, cx, cy, cs, sn, x1, y1, x3, y3, x4, y4);
    }

    /** Nave com o cubo na cabine, centrada em (cx, cy). */
    public static void ship(ShapeRenderer sr, float cx, float cy, float deg, Color c1, Color c2, int icon,
                            boolean thrust, float time) {
        float cs = MathUtils.cosDeg(deg), sn = MathUtils.sinDeg(deg);
        if (thrust) {
            float f = 16f + 6f * MathUtils.sin(time * 70f);
            sr.setColor(1f, 0.5f, 0.1f, 1f);
            tri(sr, cx, cy, cs, sn, -22, -9, -22, 1, -22 - f, -4);
            sr.setColor(1f, 0.95f, 0.3f, 1f);
            tri(sr, cx, cy, cs, sn, -22, -6, -22, -2, -22 - f * 0.6f, -4);
        }
        // contorno branco
        sr.setColor(Color.WHITE);
        quad(sr, cx, cy, cs, sn, -25, -13, 19, -13, 19, 5, -25, 5);
        tri(sr, cx, cy, cs, sn, 19, -13, 19, 5, 34, -4);
        // casco
        sr.setColor(c1);
        quad(sr, cx, cy, cs, sn, -23, -11, 19, -11, 19, 3, -23, 3);
        tri(sr, cx, cy, cs, sn, 19, -11, 19, 3, 30, -4);
        // asa
        sr.setColor(c2);
        tri(sr, cx, cy, cs, sn, -23, -11, -5, -11, -23, -19);
        // cabine (cubo)
        cube(sr, wx(cx, -1, 10, cs, sn), wy(cy, -1, 10, cs, sn), 22f, deg, c1, c2, icon);
    }

    /** Anel (use com ShapeRenderer em modo Line). */
    public static void ring(ShapeRenderer sr, float x, float y, float r, int thick) {
        for (int k = 0; k < thick; k++) sr.circle(x, y, r - k);
    }
}
