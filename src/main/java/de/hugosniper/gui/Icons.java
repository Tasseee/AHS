package de.hugosniper.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Registry-ID -> ItemStack (fuer Icons). Gecacht; unbekannte IDs liefern ItemStack.EMPTY. */
public final class Icons {
    private Icons() {}

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    public static ItemStack stackFor(String registryId) {
        if (registryId == null) return ItemStack.EMPTY;
        String key = registryId.trim().toLowerCase(Locale.ROOT);
        ItemStack hit = CACHE.get(key);
        if (hit != null) return hit;
        ItemStack found = ItemStack.EMPTY;
        for (Item it : BuiltInRegistries.ITEM) {
            if (String.valueOf(BuiltInRegistries.ITEM.getKey(it)).equals(key)) { found = new ItemStack(it); break; }
        }
        CACHE.put(key, found);
        return found;
    }

    /** Existiert diese Registry-ID wirklich? ("minecraft:air" zaehlt nicht.) */
    public static boolean exists(String registryId) {
        ItemStack s = stackFor(registryId);
        return !s.isEmpty();
    }
}
