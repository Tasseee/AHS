package de.hugosniper.gui;

import de.hugosniper.auction.ContainerReader;
import de.hugosniper.auction.Limits;
import de.hugosniper.auction.SniperEngine;
import de.hugosniper.filter.FilterRule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dialog zum Anlegen/Bearbeiten eines Filters. original == null => neuer Filter. */
public final class FilterEditScreen extends ThemedScreen {

    private final Screen parent;
    private final FilterRule original;
    private final FilterRule draft;
    private final SniperEngine eng = SniperEngine.get();

    private EditBox nameBox, idBox, unitBox, totalBox, qtyBox, pauseBox, traitsBox;
    private String error = "";

    private static final String[] LABELS = {
            "Anzeigename", "Registry-ID", "Max. Preis pro Stueck", "Max. Gesamtpreis (0 = aus)",
            "Max. Menge (0 = aus)", "Mindestpause (ms)", "Merkmale (Komma, optional)"
    };

    public FilterEditScreen(Screen parent, FilterRule original) {
        super(Component.literal(original == null ? "Neuer Filter" : "Filter bearbeiten"));
        this.parent = parent;
        this.original = original;
        this.draft = original == null ? new FilterRule("", "minecraft:", 1000) : original.copy();
        if (original == null) draft.minPauseMs = eng.config.interactionDelayMs;
    }

    private int panelW() { return Math.min(440, width - 16); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return Math.max(6, (height - 272) / 2); }

    @Override
    protected void init() {
        beginInit();
        int px = panelX(), pw = panelW();
        int fx = px + Math.min(190, pw / 2), fw = px + pw - 12 - fx;
        int y = panelY() + 32;
        int step = 22;

        nameBox = box(fx, y, fw, draft.displayName, 60);            y += step;
        idBox = box(fx, y, fw, draft.registryId, 80);               y += step;
        unitBox = box(fx, y, fw, String.valueOf(draft.maxUnitPrice), 14);   y += step;
        totalBox = box(fx, y, fw, String.valueOf(draft.maxTotalPrice), 14); y += step;
        qtyBox = box(fx, y, fw, String.valueOf(draft.maxQuantity), 6);      y += step;
        pauseBox = box(fx, y, fw, String.valueOf(draft.minPauseMs), 6);     y += step;
        traitsBox = box(fx, y, fw, String.join(", ", draft.requiredTraits == null ? List.<String>of() : draft.requiredTraits), 160); y += step + 2;

        int half = (pw - 24 - 6) / 2;
        ghost(px + 12, y, half, 20, () -> draft.enabled ? "Filter: aktiv" : "Filter: aus", () -> draft.enabled ? Palette.GREEN : Palette.MUTED,
                draft.enabled, () -> draft.enabled = !draft.enabled);
        ghost(px + 12 + half + 6, y, half, 20, () -> draft.autoBuy ? "Auto-Kauf: AN" : "Auto-Kauf: AUS", () -> draft.autoBuy ? Palette.RED : Palette.MUTED,
                draft.autoBuy, () -> draft.autoBuy = !draft.autoBuy);
        y += 24;
        ghost(px + 12, y, pw - 24, 20, "Item aus Hand uebernehmen", Palette.CYAN, false, this::fromHand);
        y += 26;
        ghost(px + 12, y, half, 22, "Speichern", Palette.ACCENT, true, this::save);
        ghost(px + 12 + half + 6, y, half, 22, "Abbrechen", Palette.BORDER, false, () -> minecraft.setScreen(parent));
    }

    private EditBox box(int x, int y, int w, String value, int maxLen) {
        EditBox eb = new EditBox(font, x, y, w, 18, Component.empty());
        eb.setMaxLength(maxLen);
        eb.setValue(value == null ? "" : value);
        addRenderableWidget(eb);
        return eb;
    }

    private void fromHand() {
        if (minecraft == null || minecraft.player == null) { error = "Kein Spieler"; return; }
        ItemStack st = minecraft.player.getMainHandItem();
        if (st.isEmpty()) { error = "Nichts in der Hand"; return; }
        idBox.setValue(ContainerReader.registryId(st));
        if (nameBox.getValue().isBlank()) nameBox.setValue(ContainerReader.displayName(st));
        error = "";
    }

    private static long parse(EditBox b) {
        try { return Long.parseLong(b.getValue().trim().replace(".", "").replace(",", "")); }
        catch (NumberFormatException e) { return Long.MIN_VALUE; }
    }

    private void save() {
        String id = FilterRule.normalizeId(idBox.getValue());
        if (!FilterRule.isValidId(id)) { error = "Ungueltige Registry-ID (Format namespace:name)"; return; }
        if (!Icons.exists(id)) { error = "Dieses Item existiert nicht: " + id; return; }
        long unit = parse(unitBox), total = parse(totalBox), qty = parse(qtyBox), pause = parse(pauseBox);
        if (unit <= 0) { error = "Maximalpreis pro Stueck muss > 0 sein"; return; }
        if (total < 0) { error = "Gesamtpreis: Zahl >= 0"; return; }
        if (qty < 0 || qty > 2304) { error = "Menge: Zahl 0..2304"; return; }
        if (pause < Limits.MIN_INTERACTION_MS || pause > Limits.MAX_INTERACTION_MS) {
            error = "Pause: " + Limits.MIN_INTERACTION_MS + ".." + Limits.MAX_INTERACTION_MS + " ms"; return;
        }
        List<String> traits = new ArrayList<>();
        for (String t : traitsBox.getValue().split(",")) { String s = t.trim().toLowerCase(Locale.ROOT); if (!s.isEmpty()) traits.add(s); }

        FilterRule target = original != null ? original : new FilterRule();
        target.displayName = nameBox.getValue().isBlank() ? id : nameBox.getValue().trim();
        target.registryId = id;
        target.maxUnitPrice = unit;
        target.maxTotalPrice = total;
        target.maxQuantity = (int) qty;
        target.minPauseMs = (int) pause;
        target.requiredTraits = traits;
        target.enabled = draft.enabled;
        target.autoBuy = draft.autoBuy;
        if (original == null) eng.config.filters.add(target);
        eng.syncRows();
        eng.saveConfig();
        eng.log.info((original == null ? "Filter erstellt: " : "Filter gespeichert: ") + target.displayName + " (" + id + ")");
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Draw.verticalGradient(g, 0, 0, width, height, Palette.BG_DEEP, Palette.BG);
        int px = panelX(), py = panelY(), pw = panelW();
        Draw.card(g, px, py, pw, 272, 8, Palette.CARD, Palette.BORDER);
        Draw.roundedRect(g, px + 1, py + 1, pw - 2, 3, 1, Palette.ACCENT);
        g.drawString(font, getTitle(), px + 12, py + 12, Palette.TEXT);

        ItemStack icon = Icons.stackFor(FilterRule.normalizeId(idBox == null ? "" : idBox.getValue()));
        if (!icon.isEmpty()) g.renderItem(icon, px + pw - 30, py + 8);

        int y = py + 32;
        for (String l : LABELS) { g.drawString(font, l, px + 12, y + 5, Palette.MUTED); y += 22; }

        if (!error.isEmpty()) g.drawString(font, Draw.fit(font, error, pw - 24), px + 12, py + 272 - 12, Palette.RED);

        drawUiButtons(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
