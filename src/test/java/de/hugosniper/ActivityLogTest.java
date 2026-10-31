package de.hugosniper;

import de.hugosniper.util.ActivityLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActivityLogTest {
    @Test void keepsOnlyNewestEntries() {
        ActivityLog log = new ActivityLog(10);
        for (int i = 0; i < 25; i++) log.info("e" + i);
        assertEquals(10, log.size());
        assertEquals("e24", log.snapshotNewestFirst().get(0).text());
        assertEquals("e15", log.snapshotNewestFirst().get(9).text());
    }
}
