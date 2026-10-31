package de.hugosniper.config;

import de.hugosniper.auction.Limits;
import de.hugosniper.filter.FilterRule;

import java.util.ArrayList;
import java.util.List;

/** Validiert und repariert eine geladene Config. Gibt Warnungen zurueck, wirft nie. */
public final class ConfigValidator {
    private ConfigValidator() {}

    public static List<String> validate(HugoConfig c) {
        List<String> warn = new ArrayList<>();

        if (c.configVersion <= 0 || c.configVersion > HugoConfig.CURRENT_VERSION) {
            warn.add("Unbekannte configVersion " + c.configVersion + " -> setze " + HugoConfig.CURRENT_VERSION);
            c.configVersion = HugoConfig.CURRENT_VERSION;
        }
        if (c.refreshIntervalMs < Limits.MIN_REFRESH_MS || c.refreshIntervalMs > Limits.MAX_REFRESH_MS) {
            warn.add("refreshIntervalMs ausserhalb " + Limits.MIN_REFRESH_MS + ".." + Limits.MAX_REFRESH_MS + " -> Standard");
            c.refreshIntervalMs = Limits.DEFAULT_REFRESH_MS;
        }
        if (c.interactionDelayMs < Limits.MIN_INTERACTION_MS || c.interactionDelayMs > Limits.MAX_INTERACTION_MS) {
            warn.add("interactionDelayMs ausserhalb " + Limits.MIN_INTERACTION_MS + ".." + Limits.MAX_INTERACTION_MS + " -> Standard");
            c.interactionDelayMs = Limits.DEFAULT_INTERACTION_MS;
        }
        if (c.maxAutoTotalPrice <= 0 || c.maxAutoTotalPrice > Limits.HARD_MAX_AUTO_TOTAL) {
            warn.add("maxAutoTotalPrice ungueltig/ueber harter Grenze -> " + Limits.HARD_MAX_AUTO_TOTAL);
            c.maxAutoTotalPrice = Limits.HARD_MAX_AUTO_TOTAL;
        }
        if (c.logMaxEntries < 10 || c.logMaxEntries > 5000) {
            warn.add("logMaxEntries ungueltig -> 200");
            c.logMaxEntries = 200;
        }
        if (c.hudX < 0 || c.hudX > 10_000) c.hudX = 6;
        if (c.hudY < 0 || c.hudY > 10_000) c.hudY = 6;

        HugoConfig d = new HugoConfig();
        if (c.auctionTitleHints == null || c.auctionTitleHints.isEmpty()) c.auctionTitleHints = d.auctionTitleHints;
        if (c.buyButtonHints == null || c.buyButtonHints.isEmpty()) c.buyButtonHints = d.buyButtonHints;
        if (c.confirmHints == null || c.confirmHints.isEmpty()) c.confirmHints = d.confirmHints;
        if (c.priceLineHints == null || c.priceLineHints.isEmpty()) c.priceLineHints = d.priceLineHints;
        if (c.refreshHints == null || c.refreshHints.isEmpty()) c.refreshHints = d.refreshHints;

        if (c.filters == null) c.filters = new ArrayList<>();
        List<FilterRule> ok = new ArrayList<>();
        for (int i = 0; i < c.filters.size(); i++) {
            FilterRule f = c.filters.get(i);
            if (f == null) { warn.add("Filter #" + (i + 1) + " ist null -> entfernt"); continue; }
            String id = FilterRule.normalizeId(f.registryId);
            if (!FilterRule.isValidId(id)) {
                warn.add("Filter #" + (i + 1) + ": ungueltige Registry-ID '" + f.registryId + "' -> entfernt");
                continue;
            }
            f.registryId = id;
            if (f.displayName == null || f.displayName.isBlank()) f.displayName = id;
            if (f.maxUnitPrice <= 0) { warn.add("Filter '" + f.displayName + "': maxUnitPrice <= 0 -> deaktiviert"); f.maxUnitPrice = 1; f.enabled = false; }
            if (f.maxTotalPrice < 0) f.maxTotalPrice = 0;
            if (f.maxQuantity < 0) f.maxQuantity = 0;
            if (f.minPauseMs < Limits.MIN_INTERACTION_MS || f.minPauseMs > Limits.MAX_INTERACTION_MS) f.minPauseMs = Limits.DEFAULT_INTERACTION_MS;
            if (f.requiredTraits == null) f.requiredTraits = new ArrayList<>();
            ok.add(f);
        }
        c.filters = ok;
        return warn;
    }
}
