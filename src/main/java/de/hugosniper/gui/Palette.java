package de.hugosniper.gui;

/** Farbpalette (ARGB). Alpha IMMER gesetzt, da neuere Minecraft-Versionen Farben ohne Alpha unsichtbar zeichnen. */
public final class Palette {
    private Palette() {}

    public static final int BG_DEEP     = 0xFF060C1F; // nachtblau
    public static final int BG          = 0xFF0A1530;
    public static final int SIDEBAR     = 0xFF0C1A3A;
    public static final int CARD        = 0xFF11204A;
    public static final int CARD_HOVER  = 0xFF16295C;
    public static final int BORDER      = 0xFF1D3470;
    public static final int ACCENT      = 0xFF2F7BFF; // leuchtend blau
    public static final int ACCENT_DIM  = 0xFF1B4DB3;
    public static final int CYAN        = 0xFF22D3EE; // dezenter Cyan-Akzent
    public static final int TEXT        = 0xFFE8F0FF;
    public static final int MUTED       = 0xFF8DA2CC;
    public static final int GREEN       = 0xFF34D399;
    public static final int AMBER       = 0xFFFBBF24;
    public static final int RED         = 0xFFF87171;

    /** Mischt zwei ARGB-Farben (t = 0..1). */
    public static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int ra = (int) (aa + (ba - aa) * t), rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t), rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Farbe mit neuem Alpha (0..255). */
    public static int withAlpha(int argb, int alpha) {
        return ((alpha & 255) << 24) | (argb & 0x00FFFFFF);
    }
}
