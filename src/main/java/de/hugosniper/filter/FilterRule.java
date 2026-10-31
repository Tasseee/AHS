package de.hugosniper.filter;

import java.util.ArrayList;
import java.util.List;

/** Ein Item-Filter. Einfaches POJO, damit Gson es direkt (de)serialisieren kann. */
public final class FilterRule {
    public String displayName = "Neuer Filter";
    /** Exakte Registry-ID, z.B. "minecraft:dragon_head". */
    public String registryId = "minecraft:stone";
    public long maxUnitPrice = 1_000;
    /** 0 = kein Gesamtpreis-Limit pro Filter. */
    public long maxTotalPrice = 0;
    /** 0 = keine Mengenbegrenzung. */
    public int maxQuantity = 0;
    public boolean enabled = true;
    public boolean autoBuy = false;
    public int minPauseMs = 400;
    /** Optionale zusaetzliche Merkmale als exakte Strings, z.B. "enchant:minecraft:sharpness=5". Leer = nicht gefordert. */
    public List<String> requiredTraits = new ArrayList<>();

    public FilterRule() {}

    public FilterRule(String displayName, String registryId, long maxUnitPrice) {
        this.displayName = displayName;
        this.registryId = registryId;
        this.maxUnitPrice = maxUnitPrice;
    }

    public FilterRule copy() {
        FilterRule r = new FilterRule(displayName, registryId, maxUnitPrice);
        r.maxTotalPrice = maxTotalPrice;
        r.maxQuantity = maxQuantity;
        r.enabled = enabled;
        r.autoBuy = autoBuy;
        r.minPauseMs = minPauseMs;
        r.requiredTraits = new ArrayList<>(requiredTraits == null ? List.of() : requiredTraits);
        return r;
    }

    /** Registry-ID-Vergleich: exakt, case-insensitiv, "stone" wird zu "minecraft:stone" normalisiert. */
    public static String normalizeId(String id) {
        if (id == null) return "";
        String s = id.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return "";
        return s.contains(":") ? s : "minecraft:" + s;
    }

    public static boolean isValidId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    public boolean matchesId(String actualId) {
        String a = normalizeId(actualId);
        return !a.isEmpty() && a.equals(normalizeId(registryId));
    }
}
