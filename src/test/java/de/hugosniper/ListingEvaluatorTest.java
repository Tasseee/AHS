package de.hugosniper;

import de.hugosniper.auction.*;
import de.hugosniper.filter.FilterRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListingEvaluatorTest {
    static final long CAP = Limits.HARD_MAX_AUTO_TOTAL;

    static FilterRule dragon() {
        FilterRule r = new FilterRule("Dragon Head", "minecraft:dragon_head", 500_000);
        r.maxQuantity = 4;
        r.maxTotalPrice = 2_000_000;
        r.autoBuy = true;
        return r;
    }

    static ListingSnapshot listing(String id, int count, Long... prices) {
        return new ListingSnapshot(10, id, count, "Dragon Head", List.of(prices), List.of());
    }

    @Test void matchingListingIsAllowed() {
        var e = ListingEvaluator.evaluate(listing("minecraft:dragon_head", 2, 800_000L), dragon(), true, CAP);
        assertEquals(Verdict.BUY_ALLOWED, e.verdict());
        assertEquals(400_000, e.unit());
        assertEquals(800_000, e.total());
    }

    @Test void itemIdComparisonIsExactNotNameBased() {
        // Anzeigename sagt "Dragon Head", echte ID ist Stein => darf nie passen
        var e = ListingEvaluator.evaluate(listing("minecraft:stone", 1, 100L), dragon(), true, CAP);
        assertEquals(Verdict.NO_FILTER_MATCH, e.verdict());
        assertFalse(e.verdict().isHit());
    }

    @Test void idNormalizationIsCaseInsensitiveAndAddsNamespace() {
        assertTrue(dragon().matchesId("MINECRAFT:Dragon_Head"));
        assertTrue(dragon().matchesId("dragon_head"));
        assertFalse(dragon().matchesId("minecraft:dragon_head2"));
        assertFalse(dragon().matchesId(""));
        assertFalse(dragon().matchesId(null));
    }

    @Test void unknownIdentityIsNeverBought() {
        assertEquals(Verdict.ID_UNKNOWN, ListingEvaluator.evaluate(listing(null, 1, 100L), dragon(), true, CAP).verdict());
        assertEquals(Verdict.ID_UNKNOWN, ListingEvaluator.evaluate(listing("  ", 1, 100L), dragon(), true, CAP).verdict());
    }

    @Test void stackCountZeroIsRejected() {
        assertEquals(Verdict.BAD_COUNT, ListingEvaluator.evaluate(listing("minecraft:dragon_head", 0, 100L), dragon(), true, CAP).verdict());
    }

    @Test void missingPriceIsRejected() {
        assertEquals(Verdict.PRICE_UNKNOWN, ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1), dragon(), true, CAP).verdict());
    }

    @Test void ambiguousPriceIsRejected() {
        assertEquals(Verdict.PRICE_AMBIGUOUS,
                ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 100L, 90_000_000L), dragon(), true, CAP).verdict());
    }

    @Test void unitAndTotalLimitsAreBothEnforced() {
        assertEquals(Verdict.OVER_UNIT_LIMIT,
                ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 500_001L), dragon(), true, CAP).verdict());
        assertEquals(Verdict.OVER_TOTAL_LIMIT,
                ListingEvaluator.evaluate(listing("minecraft:dragon_head", 4, 2_000_001L), dragon(), true, CAP).verdict());
        assertEquals(Verdict.OVER_QUANTITY,
                ListingEvaluator.evaluate(listing("minecraft:dragon_head", 5, 100L), dragon(), true, CAP).verdict());
    }

    @Test void unitPriceRoundingNeverLetsItSlipThrough() {
        FilterRule r = dragon();
        r.maxUnitPrice = 3;
        r.maxTotalPrice = 0;
        // 10 / 3 = 3,33 -> aufgerundet 4 > 3 => abgelehnt
        assertEquals(Verdict.OVER_UNIT_LIMIT, ListingEvaluator.evaluate(listing("minecraft:dragon_head", 3, 10L), r, true, CAP).verdict());
    }

    @Test void overTenMillionNeverAutoBuys() {
        FilterRule r = dragon();
        r.maxTotalPrice = 0;
        r.maxQuantity = 0;
        r.maxUnitPrice = 100_000_000;
        var e = ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 10_000_001L), r, true, CAP);
        assertEquals(Verdict.NEEDS_MANUAL_CONFIRM, e.verdict());
        var atLimit = ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 10_000_000L), r, true, CAP);
        assertEquals(Verdict.BUY_ALLOWED, atLimit.verdict());
    }

    @Test void configuredCapAboveHardLimitIsClamped() {
        FilterRule r = dragon();
        r.maxTotalPrice = 0;
        r.maxQuantity = 0;
        r.maxUnitPrice = Long.MAX_VALUE / 2;
        var e = ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 50_000_000L), r, true, Long.MAX_VALUE);
        assertEquals(Verdict.NEEDS_MANUAL_CONFIRM, e.verdict());
    }

    @Test void autoBuyOffMeansManualOnly() {
        assertEquals(Verdict.NEEDS_MANUAL_CONFIRM,
                ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 100L), dragon(), false, CAP).verdict());
        FilterRule r = dragon();
        r.autoBuy = false;
        assertEquals(Verdict.NEEDS_MANUAL_CONFIRM,
                ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 100L), r, true, CAP).verdict());
    }

    @Test void disabledFilterNeverMatches() {
        FilterRule r = dragon();
        r.enabled = false;
        assertFalse(ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 100L), r, true, CAP).verdict().isHit());
    }

    @Test void requiredTraitsMustBePresent() {
        FilterRule r = dragon();
        r.requiredTraits = List.of("enchant:minecraft:sharpness=5");
        var without = new ListingSnapshot(1, "minecraft:dragon_head", 1, "x", List.of(100L), List.of());
        var with = new ListingSnapshot(1, "minecraft:dragon_head", 1, "x", List.of(100L), List.of("enchant:minecraft:sharpness=5"));
        assertEquals(Verdict.TRAITS_MISSING, ListingEvaluator.evaluate(without, r, true, CAP).verdict());
        assertEquals(Verdict.BUY_ALLOWED, ListingEvaluator.evaluate(with, r, true, CAP).verdict());
    }

    @Test void abbreviatedPriceUsesUpperBoundAgainstHardCap() {
        FilterRule r = dragon();
        r.maxTotalPrice = 0; r.maxQuantity = 0; r.maxUnitPrice = 100_000_000;
        // "9.99M" -> obere Grenze 9.995.000 -> noch ok; "10M" -> 10.500.000 -> ueber der Grenze
        assertEquals(Verdict.BUY_ALLOWED, ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 9_995_000L), r, true, CAP).verdict());
        assertEquals(Verdict.NEEDS_MANUAL_CONFIRM, ListingEvaluator.evaluate(listing("minecraft:dragon_head", 1, 10_500_000L), r, true, CAP).verdict());
    }
}
