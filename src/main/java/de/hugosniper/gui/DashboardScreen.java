package de.hugosniper.gui;

import de.hugosniper.auction.FilterRow;
import de.hugosniper.auction.Limits;
import de.hugosniper.auction.RowStatus;
import de.hugosniper.auction.SniperEngine;
import de.hugosniper.config.HugoConfig;
import de.hugosniper.filter.FilterRule;
import de.hugosniper.util.ActivityLog;
import de.hugosniper.util.Money;
import de.hugosniper.util.TimeFmt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Das /ahs-Dashboard: Kopfzeile, Seitenleiste, Hauptbereich (4 Tabs), Fussleiste. */
public final class DashboardScreen extends ThemedScreen {

    public enum Tab {
        DASHBOARD("Dashboard", "Dash"), FILTERS("Item-Filter", "Filter"), SETTINGS("Einstellungen", "Einst."), LOG("Protokoll", "Log");
        final String label, shortLabel;
        Tab(String l, String s) { label = l; shortLabel = s; }
    }

    private static final int HEADER_H = 44;
    private static final int FOOTER_H = 34;
    private static final int CARD_H = 52;
    private static final int FILTER_ROW_H = 32;
    private static final int SETTING_ROW_H = 26;

    private Tab tab;
    private int scroll;
    private int sidebarW;
    private int confirmDelete = -1;
    private final SniperEngine eng = SniperEngine.get();

    public DashboardScreen() { this(Tab.DASHBOARD); }

    public DashboardScreen(Tab tab) {
        super(Component.literal("HugoSniper"));
        this.tab = tab;
    }

    // ------------------------------------------------------------------ Geometrie

    private int mainX() { return sidebarW; }
    private int mainW() { return width - sidebarW; }
    private int contentTop() { return HEADER_H + 10; }
    private int contentBottom() { return height - FOOTER_H - 6; }
    private int contentH() { return Math.max(20, contentBottom() - contentTop()); }

    private int contentHeight() {
        return switch (tab) {
            case DASHBOARD -> eng.config.filters.size() * (CARD_H + 6);
            case FILTERS -> eng.config.filters.size() * (FILTER_ROW_H + 4);
            case SETTINGS -> SETTING_COUNT * SETTING_ROW_H;
            case LOG -> eng.log.size() * 11;
        };
    }

    private int maxScroll() { return Math.max(0, contentHeight() - contentH()); }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        beginInit();
        sidebarW = width < 420 ? 74 : 112;
        scroll = Math.min(scroll, maxScroll());

        int y = HEADER_H + 10;
        for (Tab t : Tab.values()) {
            final Tab target = t;
            ghost(6, y, sidebarW - 12, 22, width < 420 ? t.shortLabel : t.label, Palette.ACCENT, tab == t, () -> switchTab(target));
            y += 26;
        }

        // Fussleiste
        int mx = mainX() + 8, mw = mainW() - 16, gap = 6;
        int bw = (mw - gap * 4) / 5;
        int fy = height - FOOTER_H + 6;
        boolean narrow = mw < 470;
        ghost(mx, fy, bw, 22,
                () -> eng.isActive() ? "Pause" : "Start",
                () -> eng.isActive() ? Palette.AMBER : Palette.GREEN, true, () -> eng.toggle());
        ghost(mx + (bw + gap), fy, bw, 22, narrow ? "Scan" : "Aktualisieren", Palette.ACCENT, false, eng::requestSingleScan);
        ghost(mx + (bw + gap) * 2, fy, bw, 22, narrow ? "+ Filter" : "Filter hinzufuegen", Palette.CYAN, false,
                () -> minecraft.setScreen(new FilterEditScreen(this, null)));
        ghost(mx + (bw + gap) * 3, fy, bw, 22, narrow ? "Einst." : "Einstellungen", Palette.ACCENT, false, () -> switchTab(Tab.SETTINGS));
        ghost(mx + (bw + gap) * 4, fy, bw, 22, narrow ? "Log" : "Protokoll", Palette.ACCENT, false, () -> switchTab(Tab.LOG));

        switch (tab) {
            case FILTERS -> buildFilterButtons();
            case SETTINGS -> buildSettings();
            default -> { }
        }
    }

    private void switchTab(Tab t) {
        eng.saveConfig();
        tab = t;
        scroll = 0;
        confirmDelete = -1;
        rebuildWidgets();
    }

    @Override
    public void removed() {
        eng.saveConfig();
    }

    // ------------------------------------------------------------------ Filter-Tab (Buttons)

    private void buildFilterButtons() {
        List<FilterRule> filters = eng.config.filters;
        int x = mainX() + 10, w = mainW() - 20;
        for (int i = 0; i < filters.size(); i++) {
            int ry = contentTop() + i * (FILTER_ROW_H + 4) - scroll;
            if (ry < contentTop() || ry + FILTER_ROW_H > contentBottom()) continue;
            final int idx = i;
            final FilterRule f = filters.get(i);
            int bx = x + w - 6;
            int bw = w < 400 ? 22 : 64;
            // von rechts nach links: Loeschen, An/Aus, Bearbeiten
            bx -= 22;
            ghost(bx, ry + 6, 22, 20, () -> confirmDelete == idx ? "?" : "X", () -> confirmDelete == idx ? Palette.RED : Palette.BORDER,
                    confirmDelete == idx, () -> {
                        if (confirmDelete == idx) {
                            eng.config.filters.remove(idx);
                            eng.syncRows();
                            eng.saveConfig();
                            eng.log.info("Filter geloescht: " + f.displayName);
                            confirmDelete = -1;
                            scroll = Math.min(scroll, maxScroll());
                        } else {
                            confirmDelete = idx;
                        }
                        rebuildWidgets();
                    });
            bx -= bw + 4;
            ghost(bx, ry + 6, bw, 20, () -> f.enabled ? (w < 400 ? "An" : "Aktiv") : (w < 400 ? "Aus" : "Aus"),
                    () -> f.enabled ? Palette.GREEN : Palette.MUTED, f.enabled, () -> {
                        f.enabled = !f.enabled;
                        eng.syncRows();
                        eng.saveConfig();
                    });
            bx -= bw + 4;
            ghost(bx, ry + 6, bw, 20, w < 400 ? "Edit" : "Bearbeiten", Palette.ACCENT, false,
                    () -> minecraft.setScreen(new FilterEditScreen(this, f)));
        }
    }

    // ------------------------------------------------------------------ Einstellungen

    private static final int SETTING_COUNT = 12;

    private EditBox numberBox(int x, int y, int w, long current, long min, long max, java.util.function.LongConsumer apply) {
        EditBox eb = new EditBox(font, x, y, w, 18, Component.empty());
        eb.setMaxLength(12);
        eb.setValue(String.valueOf(current));
        eb.setResponder(s -> {
            try {
                long v = Long.parseLong(s.trim().replace(".", "").replace(",", ""));
                if (v >= min && v <= max) apply.accept(v);
            } catch (NumberFormatException ignored) { /* ungueltige Eingabe: Wert bleibt unveraendert */ }
        });
        addRenderableWidget(eb);
        return eb;
    }

    private EditBox listBox(int x, int y, int w, List<String> current, Consumer<List<String>> apply) {
        EditBox eb = new EditBox(font, x, y, w, 18, Component.empty());
        eb.setMaxLength(200);
        eb.setValue(String.join(", ", current));
        eb.setResponder(s -> {
            List<String> out = new ArrayList<>();
            for (String p : s.split(",")) { String t = p.trim().toLowerCase(java.util.Locale.ROOT); if (!t.isEmpty()) out.add(t); }
            if (!out.isEmpty()) apply.accept(out);
        });
        addRenderableWidget(eb);
        return eb;
    }

    private int settingY(int i) { return contentTop() + i * SETTING_ROW_H - scroll; }
    private boolean settingVisible(int i) { int y = settingY(i); return y >= contentTop() && y + SETTING_ROW_H <= contentBottom(); }

    private void buildSettings() {
        HugoConfig c = eng.config;
        int x = mainX() + 10, w = mainW() - 20;
        int ctrlX = x + Math.min(210, w / 2), ctrlW = x + w - ctrlX;

        // 0: Refresh-Presets + Eingabe
        if (settingVisible(0)) {
            int y = settingY(0) + 2;
            int[] presets = {500, 1000, 1500, 2000};
            int pw = Math.max(30, (ctrlW - 70 - presets.length * 3) / presets.length);
            int px = ctrlX;
            for (int p : presets) {
                final int v = p;
                ghost(px, y, pw, 18, String.valueOf(p), Palette.ACCENT, c.refreshIntervalMs == p, () -> { c.refreshIntervalMs = v; rebuildWidgets(); });
                px += pw + 3;
            }
            numberBox(px, y, Math.max(40, x + w - px), c.refreshIntervalMs, Limits.MIN_REFRESH_MS, Limits.MAX_REFRESH_MS, v -> c.refreshIntervalMs = (int) v);
        }
        // 1: Interaktionsverzoegerung
        if (settingVisible(1)) numberBox(ctrlX, settingY(1) + 2, Math.min(80, ctrlW), c.interactionDelayMs, Limits.MIN_INTERACTION_MS, Limits.MAX_INTERACTION_MS, v -> c.interactionDelayMs = (int) v);
        // 2: Auto-Kauf
        if (settingVisible(2)) ghost(ctrlX, settingY(2) + 2, Math.min(110, ctrlW), 18, () -> c.autoBuyEnabled ? "Auto-Kauf: AN" : "Auto-Kauf: AUS",
                () -> c.autoBuyEnabled ? Palette.RED : Palette.MUTED, c.autoBuyEnabled, () -> {
                    c.autoBuyEnabled = !c.autoBuyEnabled;
                    eng.log.warn("Auto-Kauf " + (c.autoBuyEnabled ? "AKTIVIERT" : "deaktiviert"));
                });
        // 3: max. Auto-Gesamtpreis
        if (settingVisible(3)) numberBox(ctrlX, settingY(3) + 2, Math.min(110, ctrlW), c.maxAutoTotalPrice, 1, Limits.HARD_MAX_AUTO_TOTAL, v -> c.maxAutoTotalPrice = v);
        // 4: Preis im Kaufmenue verlangen
        if (settingVisible(4)) ghost(ctrlX, settingY(4) + 2, Math.min(110, ctrlW), 18, () -> c.requirePriceInBuyMenu ? "Streng: AN" : "Streng: AUS",
                () -> c.requirePriceInBuyMenu ? Palette.GREEN : Palette.AMBER, c.requirePriceInBuyMenu, () -> c.requirePriceInBuyMenu = !c.requirePriceInBuyMenu);
        // 5: aktiver Refresh
        if (settingVisible(5)) ghost(ctrlX, settingY(5) + 2, Math.min(110, ctrlW), 18, () -> c.activeRefreshEnabled ? "Aktiv: AN" : "Passiv",
                () -> c.activeRefreshEnabled ? Palette.AMBER : Palette.MUTED, c.activeRefreshEnabled, () -> c.activeRefreshEnabled = !c.activeRefreshEnabled);
        // 6: HUD
        if (settingVisible(6)) ghost(ctrlX, settingY(6) + 2, Math.min(110, ctrlW), 18, () -> c.hudEnabled ? "HUD: AN" : "HUD: AUS",
                () -> c.hudEnabled ? Palette.GREEN : Palette.MUTED, c.hudEnabled, () -> c.hudEnabled = !c.hudEnabled);
        // 7-10: Hinweiswoerter
        if (settingVisible(7)) listBox(ctrlX, settingY(7) + 2, ctrlW, c.auctionTitleHints, v -> c.auctionTitleHints = v);
        if (settingVisible(8)) listBox(ctrlX, settingY(8) + 2, ctrlW, c.buyButtonHints, v -> c.buyButtonHints = v);
        if (settingVisible(9)) listBox(ctrlX, settingY(9) + 2, ctrlW, c.confirmHints, v -> c.confirmHints = v);
        if (settingVisible(10)) listBox(ctrlX, settingY(10) + 2, ctrlW, c.priceLineHints, v -> c.priceLineHints = v);
        // 11: zuletzt gesehener Fenstertitel uebernehmen
        if (settingVisible(11)) ghost(ctrlX, settingY(11) + 2, Math.min(190, ctrlW), 18, "Fenstertitel als AH uebernehmen", Palette.CYAN, false, () -> {
            String t = eng.lastSeenContainerTitle().trim().toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty()) { eng.log.warn("Noch kein Fenstertitel gesehen - oeffne erst /ah"); return; }
            if (!c.auctionTitleHints.contains(t)) c.auctionTitleHints.add(t);
            eng.log.info("AH-Titelhinweis hinzugefuegt: '" + t + "'");
            rebuildWidgets();
        });
    }

    private static final String[] SETTING_LABELS = {
            "Refresh-Intervall (ms)", "Interaktionspause (ms)", "Automatischer Kauf", "Max. Auto-Gesamtpreis",
            "Preis im Kaufmenue verlangen", "Server-Refresh-Klick", "HUD anzeigen", "AH-Titel enthaelt",
            "Buy-Button enthaelt", "Bestaetigungs-Hinweise", "Preiszeilen enthalten", "Zuletzt gesehenes Fenster"
    };

    // ------------------------------------------------------------------ Scrollen

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int old = scroll;
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - scrollY * 24));
        if (scroll != old && (tab == Tab.FILTERS || tab == Tab.SETTINGS)) rebuildWidgets();
        return true;
    }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Draw.verticalGradient(g, 0, 0, width, height, Palette.BG_DEEP, Palette.BG);
        drawSidebar(g);
        drawHeader(g);

        g.enableScissor(mainX(), contentTop() - 2, width, contentBottom() + 2);
        switch (tab) {
            case DASHBOARD -> drawDashboard(g, mouseX, mouseY);
            case FILTERS -> drawFilterRows(g);
            case SETTINGS -> drawSettingLabels(g);
            case LOG -> drawLog(g);
        }
        g.disableScissor();

        // Fussleiste
        g.fill(mainX(), height - FOOTER_H, width, height, Palette.withAlpha(Palette.SIDEBAR, 0xFF));
        g.fill(mainX(), height - FOOTER_H, width, height - FOOTER_H + 1, Palette.BORDER);

        drawUiButtons(g);
        super.render(g, mouseX, mouseY, partialTick); // Widgets (unsichtbare Buttons, Eingabefelder)
    }

    private void drawSidebar(GuiGraphics g) {
        g.fill(0, 0, sidebarW, height, Palette.SIDEBAR);
        g.fill(sidebarW - 1, 0, sidebarW, height, Palette.BORDER);
        // Logo / Textmarke
        Draw.roundedRect(g, 8, 8, 28, 28, 7, Palette.ACCENT);
        Draw.roundedRect(g, 10, 10, 24, 24, 6, Palette.ACCENT_DIM);
        g.drawString(font, Component.literal("H").withStyle(ChatFormatting.BOLD), 19, 18, Palette.TEXT);
        Draw.roundedRect(g, 30, 30, 8, 3, 1, Palette.CYAN);
        if (sidebarW > 90) {
            g.drawString(font, Component.literal("Hugo").withStyle(ChatFormatting.BOLD), 42, 11, Palette.TEXT);
            g.drawString(font, Component.literal("Sniper").withStyle(ChatFormatting.BOLD), 42, 22, Palette.CYAN);
        }
        // Statusfuss
        int dot = statusColor();
        Draw.roundedRect(g, 10, height - 20, 6, 6, 3, dot);
        g.drawString(font, statusText(), 20, height - 21, dot);
    }

    private int statusColor() {
        return switch (eng.mode()) { case ACTIVE -> Palette.GREEN; case PAUSED -> Palette.AMBER; case ERROR -> Palette.RED; };
    }

    private String statusText() {
        return switch (eng.mode()) { case ACTIVE -> "Aktiv"; case PAUSED -> "Pausiert"; case ERROR -> "Fehler"; };
    }

    private void drawHeader(GuiGraphics g) {
        int x = mainX(), w = mainW();
        g.fill(x, 0, x + w, HEADER_H, Palette.withAlpha(Palette.SIDEBAR, 0xFF));
        g.fill(x, HEADER_H - 1, x + w, HEADER_H, Palette.BORDER);
        g.fill(x, HEADER_H - 2, x + Math.min(w, 120), HEADER_H - 1, Palette.ACCENT);

        // Metrik-Karten (je nach Breite)
        String[][] items = {
                {"Status", statusText()},
                {"Intervall", eng.config.refreshIntervalMs + " ms"},
                {"Treffer", String.valueOf(eng.lastFoundCount())},
                {"Letzter Scan", TimeFmt.hms(eng.lastScanEpoch())}
        };
        int slots = w >= 520 ? 4 : w >= 360 ? 3 : 2;
        int cw = Math.min(110, (w - 16 - (slots - 1) * 6) / slots);
        int cx = x + 8;
        for (int i = 0; i < slots; i++) {
            Draw.card(g, cx, 6, cw, 30, 5, Palette.CARD, Palette.BORDER);
            g.drawString(font, items[i][0], cx + 6, 10, Palette.MUTED);
            int col = i == 0 ? statusColor() : i == 2 && eng.lastFoundCount() > 0 ? Palette.CYAN : Palette.TEXT;
            g.drawString(font, Draw.fit(font, items[i][1], cw - 12), cx + 6, 22, col);
            cx += cw + 6;
        }
    }

    private void drawDashboard(GuiGraphics g, int mouseX, int mouseY) {
        int x = mainX() + 10, w = mainW() - 20;
        List<FilterRule> filters = eng.config.filters;
        if (filters.isEmpty()) {
            g.drawString(font, "Noch keine Filter. Unten 'Filter hinzufuegen' waehlen.", x, contentTop() + 6, Palette.MUTED);
            return;
        }
        for (int i = 0; i < filters.size(); i++) {
            int y = contentTop() + i * (CARD_H + 6) - scroll;
            if (y + CARD_H < contentTop() - 2 || y > contentBottom() + 2) continue;
            drawFilterCard(g, filters.get(i), x, y, w);
        }
        if (eng.mode() != SniperEngine.Mode.ACTIVE) {
            // Hinweis, solange nichts aktiv ist
        }
    }

    private int statusColor(RowStatus s) {
        return switch (s) {
            case FOUND -> Palette.GREEN;
            case CHECK -> Palette.AMBER;
            case BUY_MENU -> Palette.CYAN;
            case SKIPPED -> Palette.MUTED;
            case ERROR -> Palette.RED;
            case DISABLED -> Palette.MUTED;
            case SEARCHING -> Palette.ACCENT;
        };
    }

    private void drawFilterCard(GuiGraphics g, FilterRule f, int x, int y, int w) {
        FilterRow row = eng.rows.getOrDefault(f, new FilterRow());
        RowStatus st = f.enabled ? row.status : RowStatus.DISABLED;
        int col = statusColor(st);
        Draw.card(g, x, y, w, CARD_H, 6, Palette.CARD, st == RowStatus.FOUND || st == RowStatus.BUY_MENU ? Palette.withAlpha(col, 0xFF) : Palette.BORDER);
        Draw.roundedRect(g, x + 1, y + 8, 2, CARD_H - 16, 1, col);

        // Icon
        Draw.roundedRect(g, x + 8, y + 7, 22, 22, 4, Palette.BG);
        net.minecraft.world.item.ItemStack icon = Icons.stackFor(f.registryId);
        if (!icon.isEmpty()) g.renderItem(icon, x + 11, y + 10);

        // Name + ID
        int chipW = font.width(st.label) + 10;
        g.drawString(font, Draw.fit(font, f.displayName, w - 40 - chipW - 12), x + 36, y + 8, Palette.TEXT);
        g.drawString(font, Draw.fit(font, f.registryId, w - 44), x + 36, y + 20, Palette.MUTED);
        Draw.chip(g, font, x + w - chipW - 8, y + 7, st.label, col);

        // Werte
        String[] labels = {"Max/Stk", "Angebot", "Menge", "Preis/Stk"};
        String[] values = {
                Money.format(f.maxUnitPrice),
                row.detectedTotal >= 0 ? Money.format(row.detectedTotal) : "-",
                row.detectedCount >= 0 ? String.valueOf(row.detectedCount) : "-",
                row.detectedUnit >= 0 ? Money.format(row.detectedUnit) : "-"
        };
        int vx = x + 36, colW = Math.max(40, (w - 44) / 4);
        for (int i = 0; i < 4; i++) {
            g.drawString(font, labels[i], vx + i * colW, y + 32, Palette.MUTED);
            int vc = i == 3 && row.detectedUnit >= 0 && row.detectedUnit <= f.maxUnitPrice ? Palette.GREEN : Palette.TEXT;
            g.drawString(font, Draw.fit(font, values[i], colW - 6), vx + i * colW, y + 42 - 1, vc);
        }
    }

    private void drawFilterRows(GuiGraphics g) {
        int x = mainX() + 10, w = mainW() - 20;
        List<FilterRule> filters = eng.config.filters;
        if (filters.isEmpty()) {
            g.drawString(font, "Noch keine Filter.", x, contentTop() + 6, Palette.MUTED);
            return;
        }
        for (int i = 0; i < filters.size(); i++) {
            int y = contentTop() + i * (FILTER_ROW_H + 4) - scroll;
            if (y + FILTER_ROW_H < contentTop() - 2 || y > contentBottom() + 2) continue;
            FilterRule f = filters.get(i);
            Draw.card(g, x, y, w, FILTER_ROW_H, 5, Palette.CARD, Palette.BORDER);
            net.minecraft.world.item.ItemStack icon = Icons.stackFor(f.registryId);
            if (!icon.isEmpty()) g.renderItem(icon, x + 8, y + 8);
            int textW = w - (w < 400 ? 22 * 3 + 20 : 64 * 2 + 22 + 30) - 34;
            g.drawString(font, Draw.fit(font, f.displayName, textW), x + 30, y + 6, f.enabled ? Palette.TEXT : Palette.MUTED);
            String sub = f.registryId + "  max " + Money.format(f.maxUnitPrice) + "/Stk" + (f.autoBuy ? "  [Auto]" : "");
            g.drawString(font, Draw.fit(font, sub, textW), x + 30, y + 18, Palette.MUTED);
        }
    }

    private void drawSettingLabels(GuiGraphics g) {
        int x = mainX() + 10, w = mainW() - 20;
        for (int i = 0; i < SETTING_COUNT; i++) {
            int y = settingY(i);
            if (y + SETTING_ROW_H < contentTop() - 2 || y > contentBottom() + 2) continue;
            g.drawString(font, SETTING_LABELS[i], x, y + 7, Palette.TEXT);
            g.fill(x, y + SETTING_ROW_H - 2, x + w, y + SETTING_ROW_H - 1, Palette.withAlpha(Palette.BORDER, 0x80));
        }
        // Warnhinweis
        if (eng.config.autoBuyEnabled) {
            int y = settingY(2);
            if (y >= contentTop() && y < contentBottom()) {
                g.drawString(font, "Harte Grenze: " + Money.format(Limits.HARD_MAX_AUTO_TOTAL), x, y + 16, Palette.RED);
            }
        }
        int y11 = settingY(11);
        if (y11 >= contentTop() && y11 + 22 < contentBottom()) {
            g.drawString(font, Draw.fit(font, "'" + eng.lastSeenContainerTitle() + "'", 180), x, y11 + 17, Palette.MUTED);
        }
    }

    private void drawLog(GuiGraphics g) {
        int x = mainX() + 10, w = mainW() - 20;
        List<ActivityLog.Entry> entries = eng.log.snapshotNewestFirst();
        if (entries.isEmpty()) { g.drawString(font, "Noch keine Ereignisse.", x, contentTop() + 4, Palette.MUTED); return; }
        for (int i = 0; i < entries.size(); i++) {
            int y = contentTop() + i * 11 - scroll;
            if (y + 11 < contentTop() - 2 || y > contentBottom() + 2) continue;
            ActivityLog.Entry e = entries.get(i);
            int c = switch (e.level()) { case INFO -> Palette.TEXT; case OK -> Palette.GREEN; case WARN -> Palette.AMBER; case ERROR -> Palette.RED; };
            g.drawString(font, TimeFmt.hms(e.epochMs()), x, y, Palette.MUTED);
            g.drawString(font, Draw.fit(font, e.text(), w - 56), x + 52, y, c);
        }
    }
}
