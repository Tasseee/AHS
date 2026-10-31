package de.hugosniper.auction;

/** Statusanzeige pro Filterzeile im Dashboard. */
public enum RowStatus {
    SEARCHING("Gesucht"), FOUND("Gefunden"), CHECK("Pruefen"), BUY_MENU("Kaufmenue geoeffnet"),
    SKIPPED("Uebersprungen"), ERROR("Fehler"), DISABLED("Deaktiviert");

    public final String label;
    RowStatus(String label) { this.label = label; }
}
