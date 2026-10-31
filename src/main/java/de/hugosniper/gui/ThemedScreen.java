package de.hugosniper.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Basis fuer alle HugoSniper-Bildschirme. Eigene, dunkelblaue Buttons werden selbst gezeichnet; darueber liegen
 * unsichtbare Vanilla-Buttons (alpha 0) als Klickflaechen. So bleibt der Code unabhaengig von den
 * Input-Event-Signaturen der jeweiligen Minecraft-Version.
 */
public abstract class ThemedScreen extends Screen {

    protected record UiButton(Button widget, Supplier<String> label, IntSupplier color, boolean filled) {}

    protected final List<UiButton> uiButtons = new ArrayList<>();

    protected ThemedScreen(Component title) { super(title); }

    /** Am Anfang jeder init()-Methode aufrufen. */
    protected final void beginInit() { uiButtons.clear(); }

    protected UiButton ghost(int x, int y, int w, int h, Supplier<String> label, IntSupplier color, boolean filled, Runnable action) {
        Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);
        addRenderableWidget(b);
        UiButton u = new UiButton(b, label, color, filled);
        uiButtons.add(u);
        return u;
    }

    protected UiButton ghost(int x, int y, int w, int h, String label, int color, boolean filled, Runnable action) {
        return ghost(x, y, w, h, () -> label, () -> color, filled, action);
    }

    protected void drawUiButtons(GuiGraphics g) {
        for (UiButton u : uiButtons) {
            Button b = u.widget();
            if (!b.visible) continue;
            boolean hov = b.isHovered() && b.active;
            int col = u.color().getAsInt();
            int base = u.filled() ? col : Palette.CARD;
            int fill = hov ? Palette.mix(base, 0xFFFFFFFF, 0.12f) : base;
            int border = u.filled() ? Palette.mix(col, 0xFFFFFFFF, 0.25f) : (hov ? Palette.ACCENT : Palette.BORDER);
            Draw.card(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 4, fill, border);
            String t = Draw.fit(font, u.label().get(), b.getWidth() - 8);
            g.drawString(font, t, b.getX() + (b.getWidth() - font.width(t)) / 2, b.getY() + (b.getHeight() - 8) / 2,
                    u.filled() ? 0xFFFFFFFF : Palette.TEXT);
        }
    }

    /** Eigener Hintergrund wird in render() gemalt; der Vanilla-Hintergrund (Blur) wird bewusst unterdrueckt. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public boolean isPauseScreen() { return false; }
}
