package de.hugosniper.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeFmt {
    private TimeFmt() {}
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static String hms(long epochMs) {
        return epochMs <= 0 ? "--:--:--" : HMS.format(Instant.ofEpochMilli(epochMs));
    }
}
