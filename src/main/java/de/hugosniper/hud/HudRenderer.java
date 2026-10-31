package de.hugosniper.hud;

import de.hugosniper.auction.FilterRow;
import de.hugosniper.auction.RowStatus;
import de.hugosniper.auction.SniperEngine;
import de.hugosniper.filter.FilterRule;
import de.hugosniper.gui.Draw;
import de.hugosniper.gui.Palette;
import de.hugosniper.util.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Kompaktes Trading-HUD (oben links, per /ahs hud ein-/ausschaltbar). Zeichnet nur ein paar Fills + Text pro Frame. */
public final class HudRenderer {
    private HudRenderer() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        SniperEngine eng = SniperEngine.get();
        if (!eng.config.hudEnabled || mc.player == null || mc.options.hideGui) return;
        if (mc.screen instanceof de.hugosniper.gui.DashboardScreen) return;

        Font font = mc.font;
        int x = eng.config.hudX, y = eng.config.hudY;
        int w = 148;

        // Hoehe: Kopf (26) + Detailzeile (12) + Info (24) + bis zu 3 Treffer (je 12)
        int shown = 0;
        for (var e : eng.rows.entrySet()) {
            RowStatus s = e.getValue().status;
            if (s == RowStatus.FOUND || s == RowStatus.CHECK || s == RowStatus.BUY_MENU) shown++;
        }
        shown = Math.min(shown, 3);
        int h = 26 + 12 + 24 + (shown > 0 ? 4 + shown * 12 : 0);

        Draw.card(g, x, y, w, h, 5, Palette.withAlpha(Palette.BG, 0xE6), Palette.withAlpha(Palette.BORDER, 0xFF));
        Draw.roundedRect(g, x + 1, y + 1, w - 2, 2, 1, Palette.ACCENT);

        // Kopf: Statuspunkt + Name
        int dot = switch (eng.mode()) { case ACTIVE -> Palette.GREEN; case PAUSED -> Palette.AMBER; case ERROR -> Palette.RED; };
        Draw.roundedRect(g, x + 8, y + 10, 6, 6, 3, dot);
        g.drawString(font, "Hugo", x + 19, y + 8, Palette.TEXT);
        g.drawString(font, "Sniper", x + 19 + font.width("Hugo"), y + 8, Palette.CYAN);
        String st = switch (eng.mode()) { case ACTIVE -> "AKTIV"; case PAUSED -> "PAUSE"; case ERROR -> "FEHLER"; };
        g.drawString(font, st, x + w - 8 - font.width(st), y + 8, dot);

        // Detailzeile
        g.drawString(font, Draw.fit(font, eng.modeDetail(), w - 16), x + 8, y + 26, Palette.MUTED);

        // Info
        String info = eng.config.refreshIntervalMs + " ms  |  " + eng.lastFoundCount() + " Treffer  |  Auto "
                + (eng.config.autoBuyEnabled ? "AN" : "AUS");
        g.drawString(font, Draw.fit(font, info, w - 16), x + 8, y + 38, Palette.TEXT);
        String last = "Letzter Scan " + de.hugosniper.util.TimeFmt.hms(eng.lastScanEpoch());
        g.drawString(font, last, x + 8, y + 50, Palette.MUTED);

        // Treffer
        int ry = y + 66;
        int n = 0;
        for (FilterRule f : eng.config.filters) {
            FilterRow r = eng.rows.get(f);
            if (r == null) continue;
            if (r.status != RowStatus.FOUND && r.status != RowStatus.CHECK && r.status != RowStatus.BUY_MENU) continue;
            if (n++ >= 3) break;
            int c = r.status == RowStatus.CHECK ? Palette.AMBER : Palette.GREEN;
            String line = f.displayName + " " + Money.format(Math.max(r.detectedTotal, 0));
            g.drawString(font, Draw.fit(font, line, w - 16), x + 8, ry, c);
            ry += 12;
        }
    }
}
