package de.hugosniper.auction;

/** Harte Sicherheitsgrenzen. Bewusst als Konstanten, nicht ueber Filter ueberschreibbar. */
public final class Limits {
    private Limits() {}

    /** Absolute Obergrenze fuer automatische Kaeufe (Gesamtpreis). Auch die Config darf sie nicht ueberschreiten. */
    public static final long HARD_MAX_AUTO_TOTAL = 10_000_000L;

    public static final int MIN_REFRESH_MS = 500;
    public static final int MAX_REFRESH_MS = 10_000;
    public static final int DEFAULT_REFRESH_MS = 1_000;

    public static final int MIN_INTERACTION_MS = 200;
    public static final int MAX_INTERACTION_MS = 1_500;
    public static final int DEFAULT_INTERACTION_MS = 400;

    /** Maximale Wartezeit auf Server-Reaktion (Menue / Ergebnis) in Millisekunden. */
    public static final int RESPONSE_TIMEOUT_MS = 5_000;

    public static final int MS_PER_TICK = 50;

    public static int msToTicks(int ms) { return Math.max(1, (ms + MS_PER_TICK - 1) / MS_PER_TICK); }
}
