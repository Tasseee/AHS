package de.hugosniper;

import de.hugosniper.util.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void unitPriceExact() {
        var u = Money.unitPrice(2_000_000, 4).orElseThrow();
        assertEquals(500_000, u.unit());
        assertTrue(u.exact());
    }

    @Test void unitPriceRoundsUpNeverCheaper() {
        var u = Money.unitPrice(10, 3).orElseThrow();
        assertEquals(4, u.unit());
        assertFalse(u.exact());
    }

    @Test void stackCountZeroOrNegativeIsRejected() {
        assertTrue(Money.unitPrice(1000, 0).isEmpty());
        assertTrue(Money.unitPrice(1000, -5).isEmpty());
    }

    @Test void negativeTotalIsRejected() {
        assertTrue(Money.unitPrice(-1, 1).isEmpty());
    }

    @Test void largeAmountsDoNotOverflow() {
        var u = Money.unitPrice(9_000_000_000_000L, 64).orElseThrow();
        assertEquals(140_625_000_000L, u.unit());
    }

    @Test void parsesCommonFormats() {
        assertEquals(1_234_567, Money.parsePrice("1.234.567").getAsLong());
        assertEquals(1_234_567, Money.parsePrice("1,234,567").getAsLong());
        assertEquals(1_234_567, Money.parsePrice("1234567").getAsLong());
        assertEquals(1_234_567, Money.parsePrice("Preis: 1.234.567 $").getAsLong());
        assertEquals(500_000, Money.parsePrice("500000").getAsLong());
    }

    @Test void missingPriceIsEmptyNotZero() {
        assertTrue(Money.parsePrice(null).isEmpty());
        assertTrue(Money.parsePrice("").isEmpty());
        assertTrue(Money.parsePrice("   ").isEmpty());
        assertTrue(Money.parsePrice("Preis: unbekannt").isEmpty());
        assertTrue(Money.parsePrice("0").isEmpty());
    }

    @Test void abbreviationsReturnConservativeUpperBound() {
        assertEquals(100_500, Money.parsePrice("100k").getAsLong());
        assertEquals(100_500_000, Money.parsePrice("Preis: 100m").getAsLong());
        assertEquals(100_500_000, Money.parsePrice("100M Coins").getAsLong());
        assertEquals(1_550_000, Money.parsePrice("1.5M").getAsLong());
        assertEquals(1_550_000, Money.parsePrice("1,5m").getAsLong());
        assertEquals(2_250_000_000L + 5_000_000L, Money.parsePrice("2.25b").getAsLong());
        assertEquals(2_500, Money.parsePrice("2k").getAsLong());
    }

    @Test void badAbbreviationsAreRejected() {
        assertTrue(Money.parsePrice("1.500k").isEmpty());      // 3 Nachkommastellen: mehrdeutig
        assertTrue(Money.parsePrice("0k").isEmpty());
        assertTrue(Money.parsePrice("100k und 200k").isEmpty());
        assertTrue(Money.parsePrice("999999999999999t").isEmpty());
    }

    @Test void ambiguousFormatsAreRejected() {
        assertTrue(Money.parsePrice("12,50").isEmpty());
        assertTrue(Money.parsePrice("100 und 200").isEmpty());
    }

    @Test void absurdlyLongNumbersAreRejected() {
        assertTrue(Money.parsePrice("99999999999999999999999").isEmpty());
    }
}
