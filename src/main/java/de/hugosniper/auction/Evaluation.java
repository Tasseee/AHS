package de.hugosniper.auction;

/** Ergebnis einer Bewertung inkl. berechneter Werte fuer die Anzeige. */
public record Evaluation(Verdict verdict, long total, long unit, String detail) {
    public static Evaluation of(Verdict v, String detail) { return new Evaluation(v, -1, -1, detail); }
}
