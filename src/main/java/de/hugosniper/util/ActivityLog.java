package de.hugosniper.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ringpuffer fuer Protokolleintraege. Keine Minecraft-Abhaengigkeit. */
public final class ActivityLog {
    public enum Level { INFO, OK, WARN, ERROR }

    public record Entry(long epochMs, Level level, String text) {}

    private final Deque<Entry> entries = new ArrayDeque<>();
    private int max;

    public ActivityLog(int max) { this.max = Math.max(10, max); }

    public synchronized void setMax(int max) {
        this.max = Math.max(10, max);
        while (entries.size() > this.max) entries.removeFirst();
    }

    public synchronized void add(Level level, String text) {
        entries.addLast(new Entry(System.currentTimeMillis(), level, text));
        while (entries.size() > max) entries.removeFirst();
    }

    public void info(String t) { add(Level.INFO, t); }
    public void ok(String t) { add(Level.OK, t); }
    public void warn(String t) { add(Level.WARN, t); }
    public void error(String t) { add(Level.ERROR, t); }

    /** Neueste zuerst. */
    public synchronized List<Entry> snapshotNewestFirst() {
        List<Entry> l = new ArrayList<>(entries);
        java.util.Collections.reverse(l);
        return l;
    }

    public synchronized int size() { return entries.size(); }
    public synchronized void clear() { entries.clear(); }
}
