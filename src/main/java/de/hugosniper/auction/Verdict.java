package de.hugosniper.auction;

/** Ergebnis der Angebotsbewertung. Nur BUY_ALLOWED und NEEDS_MANUAL_CONFIRM sind "Treffer". */
public enum Verdict {
    /** Alle Pruefungen bestanden, Auto-Kauf ist erlaubt. */
    BUY_ALLOWED("Gefunden"),
    /** Treffer, aber Gesamtpreis ueber Sicherheitsgrenze ODER Auto-Kauf aus: Nutzer entscheidet selbst. */
    NEEDS_MANUAL_CONFIRM("Pruefen"),
    NO_FILTER_MATCH("Filter passt nicht"),
    ID_UNKNOWN("Registry-ID unklar"),
    PRICE_UNKNOWN("Preis unklar"),
    PRICE_AMBIGUOUS("Preis mehrdeutig"),
    BAD_COUNT("Ungueltige Anzahl"),
    OVER_UNIT_LIMIT("Stueckpreis zu hoch"),
    OVER_TOTAL_LIMIT("Gesamtpreis zu hoch"),
    OVER_QUANTITY("Menge zu gross"),
    TRAITS_MISSING("Merkmale fehlen");

    public final String label;
    Verdict(String label) { this.label = label; }

    public boolean isHit() { return this == BUY_ALLOWED || this == NEEDS_MANUAL_CONFIRM; }
}
