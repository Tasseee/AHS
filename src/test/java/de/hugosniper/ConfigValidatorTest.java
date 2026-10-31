package de.hugosniper;

import de.hugosniper.auction.Limits;
import de.hugosniper.config.ConfigValidator;
import de.hugosniper.config.HugoConfig;
import de.hugosniper.filter.FilterRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {
    @Test void defaultsAreValidAndAutoBuyIsOff() {
        HugoConfig c = HugoConfig.defaults();
        assertTrue(ConfigValidator.validate(c).isEmpty());
        assertFalse(c.autoBuyEnabled);
        assertEquals(1000, c.refreshIntervalMs);
        assertEquals(400, c.interactionDelayMs);
        assertEquals(10_000_000L, c.maxAutoTotalPrice);
    }

    @Test void invalidValuesAreRepaired() {
        HugoConfig c = new HugoConfig();
        c.configVersion = 99;
        c.refreshIntervalMs = 5;
        c.interactionDelayMs = 99_999;
        c.maxAutoTotalPrice = 999_000_000_000L;
        List<String> w = ConfigValidator.validate(c);
        assertFalse(w.isEmpty());
        assertEquals(HugoConfig.CURRENT_VERSION, c.configVersion);
        assertEquals(Limits.DEFAULT_REFRESH_MS, c.refreshIntervalMs);
        assertEquals(Limits.DEFAULT_INTERACTION_MS, c.interactionDelayMs);
        assertEquals(Limits.HARD_MAX_AUTO_TOTAL, c.maxAutoTotalPrice);
    }

    @Test void negativeCapFallsBackToHardLimitNotZeroOrInfinite() {
        HugoConfig c = new HugoConfig();
        c.maxAutoTotalPrice = -5;
        ConfigValidator.validate(c);
        assertEquals(Limits.HARD_MAX_AUTO_TOTAL, c.maxAutoTotalPrice);
    }

    @Test void brokenFiltersAreRemovedOrDisabled() {
        HugoConfig c = new HugoConfig();
        c.filters = new ArrayList<>();
        c.filters.add(null);
        c.filters.add(new FilterRule("Kaputt", "das ist keine id!", 10));
        c.filters.add(new FilterRule("Kein Preis", "minecraft:stone", 0));
        c.filters.add(new FilterRule("Gut", "STONE", 10));
        ConfigValidator.validate(c);
        assertEquals(2, c.filters.size());
        assertFalse(c.filters.get(0).enabled);       // maxUnitPrice <= 0 => deaktiviert
        assertEquals("minecraft:stone", c.filters.get(1).registryId);
    }

    @Test void nullFilterListIsHandled() {
        HugoConfig c = new HugoConfig();
        c.filters = null;
        ConfigValidator.validate(c);
        assertNotNull(c.filters);
    }
}
