package de.hugosniper.auction;

import de.hugosniper.filter.FilterRule;
import de.hugosniper.util.Money;

import java.util.Optional;
import java.util.OptionalLong;

/** Reine Bewertungslogik. Keine Minecraft-Klassen, vollstaendig unit-testbar. */
public final class ListingEvaluator {
    private ListingEvaluator() {}

    /**
     * Bewertet ein Angebot gegen EINEN Filter. Fail-closed: bei jeder Unsicherheit kein Kauf.
     *
     * @param autoBuyGlobal globaler Auto-Kauf-Schalter
     * @param autoCap       konfigurierte Auto-Kauf-Grenze (wird intern zusaetzlich auf {@link Limits#HARD_MAX_AUTO_TOTAL} gekappt)
     */
    public static Evaluation evaluate(ListingSnapshot l, FilterRule rule, boolean autoBuyGlobal, long autoCap) {
        if (l == null || rule == null) return Evaluation.of(Verdict.NO_FILTER_MATCH, "kein Angebot/Filter");
        if (!rule.enabled) return Evaluation.of(Verdict.NO_FILTER_MATCH, "Filter deaktiviert");

        if (l.registryId() == null || l.registryId().isBlank()) {
            return Evaluation.of(Verdict.ID_UNKNOWN, "Registry-ID nicht lesbar");
        }
        if (!rule.matchesId(l.registryId())) {
            return Evaluation.of(Verdict.NO_FILTER_MATCH, "ID " + l.registryId() + " != " + rule.registryId);
        }
        if (l.count() <= 0) return Evaluation.of(Verdict.BAD_COUNT, "Anzahl " + l.count());

        if (l.priceCandidates().isEmpty()) return Evaluation.of(Verdict.PRICE_UNKNOWN, "kein Preis in der Lore");
        OptionalLong p = l.uniquePrice();
        if (p.isEmpty()) return Evaluation.of(Verdict.PRICE_AMBIGUOUS, l.priceCandidates().size() + " Preis-Kandidaten");
        long total = p.getAsLong();
        if (total <= 0) return Evaluation.of(Verdict.PRICE_UNKNOWN, "Preis <= 0");

        Optional<Money.UnitPrice> up = Money.unitPrice(total, l.count());
        if (up.isEmpty()) return Evaluation.of(Verdict.BAD_COUNT, "Stueckpreis nicht berechenbar");
        long unit = up.get().unit();

        if (rule.maxQuantity > 0 && l.count() > rule.maxQuantity) {
            return new Evaluation(Verdict.OVER_QUANTITY, total, unit, "Menge " + l.count() + " > " + rule.maxQuantity);
        }
        if (rule.maxTotalPrice > 0 && total > rule.maxTotalPrice) {
            return new Evaluation(Verdict.OVER_TOTAL_LIMIT, total, unit, "Gesamt " + total + " > " + rule.maxTotalPrice);
        }
        if (unit > rule.maxUnitPrice) {
            return new Evaluation(Verdict.OVER_UNIT_LIMIT, total, unit, "Stueck " + unit + " > " + rule.maxUnitPrice);
        }
        if (rule.requiredTraits != null) {
            for (String t : rule.requiredTraits) {
                if (!l.traits().contains(t)) {
                    return new Evaluation(Verdict.TRAITS_MISSING, total, unit, "fehlt: " + t);
                }
            }
        }

        long cap = Math.min(Math.max(autoCap, 0), Limits.HARD_MAX_AUTO_TOTAL);
        boolean autoOk = autoBuyGlobal && rule.autoBuy && total <= cap;
        return new Evaluation(autoOk ? Verdict.BUY_ALLOWED : Verdict.NEEDS_MANUAL_CONFIRM, total, unit,
                autoOk ? "Auto-Kauf erlaubt" : (total > cap ? "ueber Sicherheitsgrenze " + cap : "Auto-Kauf aus"));
    }
}
