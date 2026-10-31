package de.hugosniper.auction;

import de.hugosniper.util.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Liest Server-GUIs (Container-Menues) aus. Das ist die EINZIGE Stelle, die Minecraft-Item-Klassen anfasst.
 * Wichtig: Entscheidungen basieren auf der echten Registry-ID des ItemStacks, nie auf dem Anzeigenamen.
 */
public final class ContainerReader {
    private ContainerReader() {}

    /** Echte Registry-ID (z.B. "minecraft:dragon_head") oder null bei leerem Stack. */
    public static String registryId(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        return String.valueOf(BuiltInRegistries.ITEM.getKey(st.getItem()));
    }

    public static String displayName(ItemStack st) {
        return st.getHoverName().getString();
    }

    public static List<String> loreLines(ItemStack st) {
        List<String> out = new ArrayList<>();
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return out;
        for (Component c : lore.lines()) out.add(c.getString());
        return out;
    }

    /** Name + Lore, kleingeschrieben, fuer Hinweiswort-Suche (Buy-/Confirm-/Refresh-Buttons). */
    public static String searchText(ItemStack st) {
        StringBuilder sb = new StringBuilder(displayName(st));
        for (String l : loreLines(st)) sb.append(' ').append(l);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean containsAny(String haystackLower, List<String> hints) {
        for (String h : hints) {
            if (h != null && !h.isBlank() && haystackLower.contains(h.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Erkannte Merkmale als exakte Strings, z.B. "enchant:minecraft:sharpness=5" oder "renamed". */
    public static List<String> traits(ItemStack st) {
        List<String> t = new ArrayList<>();
        addEnchants(t, st.get(DataComponents.ENCHANTMENTS));
        addEnchants(t, st.get(DataComponents.STORED_ENCHANTMENTS));
        if (st.has(DataComponents.CUSTOM_NAME)) t.add("renamed");
        return t;
    }

    private static void addEnchants(List<String> out, ItemEnchantments e) {
        if (e == null) return;
        for (var en : e.entrySet()) {
            out.add("enchant:" + en.getKey().getRegisteredName() + "=" + en.getIntValue());
        }
    }

    /** Alle Lore-Zeilen, die laut Hinweiswoertern Preiszeilen sind, als eindeutige Zahlen-Kandidaten. */
    public static List<Long> priceCandidates(ItemStack st, List<String> priceLineHints) {
        List<Long> out = new ArrayList<>();
        for (String line : loreLines(st)) {
            if (!containsAny(line.toLowerCase(Locale.ROOT), priceLineHints)) continue;
            OptionalLong p = Money.parsePrice(line);
            if (p.isPresent() && !out.contains(p.getAsLong())) out.add(p.getAsLong());
            // Preiszeile, die nicht parsebar ist (z.B. "1.5M"): als Mehrdeutigkeit markieren
            if (p.isEmpty()) out.add(-1L);
        }
        // -1 steht fuer "Preiszeile vorhanden aber unlesbar" -> macht den Preis uneindeutig bzw. unbrauchbar
        return out;
    }

    public static ListingSnapshot snapshot(int slotIndex, ItemStack st, List<String> priceLineHints) {
        List<Long> cands = priceCandidates(st, priceLineHints);
        return new ListingSnapshot(slotIndex, registryId(st), st.getCount(), displayName(st), cands, traits(st));
    }

    /** Nur die Slots des Server-Containers, nicht das Spielerinventar. */
    public static List<Slot> containerSlots(AbstractContainerMenu menu) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) out.add(s);
        }
        return out;
    }

    public static Slot slotById(AbstractContainerMenu menu, int index) {
        for (Slot s : menu.slots) if (s.index == index) return s;
        return null;
    }

    /** Anzahl eines Items im Spielerinventar (zur Kauf-Verifikation). */
    public static int countInInventory(Minecraft mc, String registryId) {
        if (mc.player == null || registryId == null) return 0;
        Inventory inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && registryId.equals(registryId(st))) n += st.getCount();
        }
        return n;
    }
}
