package de.hugosniper;

import de.hugosniper.config.ConfigStore;
import de.hugosniper.config.HugoConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Benoetigt Gson (kommt mit Minecraft) - laeuft daher nur im Gradle-Build. */
class ConfigStoreTest {

    @Test void roundTripKeepsValues(@TempDir Path dir) {
        ConfigStore store = new ConfigStore(dir.resolve("hugo_sniper.json"));
        HugoConfig c = HugoConfig.defaults();
        c.refreshIntervalMs = 1500;
        assertTrue(store.save(c));
        HugoConfig loaded = new ConfigStore(dir.resolve("hugo_sniper.json")).load();
        assertEquals(1500, loaded.refreshIntervalMs);
        assertEquals(1, loaded.filters.size());
        assertEquals("minecraft:dragon_head", loaded.filters.get(0).registryId);
        assertFalse(loaded.autoBuyEnabled);
    }

    @Test void corruptedFileFallsBackToDefaultsAndKeepsBackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hugo_sniper.json");
        Files.writeString(file, "{ das ist kein json ");
        ConfigStore store = new ConfigStore(file);
        HugoConfig c = store.load();
        assertFalse(store.warnings.isEmpty());
        assertFalse(c.autoBuyEnabled);
        try (var s = Files.list(dir)) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().contains(".broken-")));
        }
    }

    @Test void outOfRangeValuesAreRepairedOnLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hugo_sniper.json");
        Files.writeString(file, "{\"configVersion\":1,\"refreshIntervalMs\":3,\"maxAutoTotalPrice\":99999999999,\"filters\":[]}");
        HugoConfig c = new ConfigStore(file).load();
        assertEquals(1000, c.refreshIntervalMs);
        assertEquals(10_000_000L, c.maxAutoTotalPrice);
    }

    @Test void missingFileCreatesDefaults(@TempDir Path dir) {
        Path file = dir.resolve("neu").resolve("hugo_sniper.json");
        HugoConfig c = new ConfigStore(file).load();
        assertNotNull(c);
        assertTrue(Files.exists(file));
    }
}
