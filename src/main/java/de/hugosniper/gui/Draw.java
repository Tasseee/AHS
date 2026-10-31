package de.hugosniper.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Kleine Zeichenhilfen. Abgerundete Ecken werden ueber Scanline-Fills angenaehert (nur fill(), keine Shader). */
public final class Draw {
    private Draw() {}

    /** Abgerundetes Rechteck; r wird auf die halbe kleinste Kantenlaenge begrenzt. */
    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        // Mittelteil
        g.fill(x, y + r, x + w, y + h - r, color);
        // Obere und untere Kappen: pro Zeile Einrueckung nach Kreisformel
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Karte mit Rand: aeusseres Rechteck in Randfarbe, inneres in Fuellfarbe. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int border) {
        roundedRect(g, x, y, w, h, r, border);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Vertikaler Farbverlauf ueber fill-Streifen (grob, aber billig). */
    public static void verticalGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        int steps = Math.max(1, Math.min(h, 48));
        for (int i = 0; i < steps; i++) {
            int y1 = y + h * i / steps, y2 = y + h * (i + 1) / steps;
            g.fill(x, y1, x + w, y2, Palette.mix(top, bottom, i / (float) steps));
        }
    }

    /** Kleine Status-Pille mit Text. Gibt die Breite zurueck. */
    public static int chip(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, String text, int color) {
        int tw = font.width(text);
        int w = tw + 10;
        roundedRect(g, x, y, w, 12, 5, Palette.withAlpha(color, 0x38));
        roundedRect(g, x, y, 2, 12, 1, color);
        g.drawString(font, text, x + 6, y + 2, color);
        return w;
    }

    /** Text mit Ellipse, wenn er nicht in maxW passt. */
    public static String fit(net.minecraft.client.gui.Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String e = "...";
        int ew = font.width(e);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(sb.toString() + s.charAt(i)) + ew > maxW) break;
            sb.append(s.charAt(i));
        }
        return sb + e;
    }
}
