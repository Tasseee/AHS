package de.hugosniper.auction;

/** Veraenderlicher Anzeigezustand eines Filters (Dashboard). Nur vom Client-Thread beschrieben. */
public final class FilterRow {
    public RowStatus status = RowStatus.SEARCHING;
    public long detectedTotal = -1;
    public int detectedCount = -1;
    public long detectedUnit = -1;
    public String note = "";
    public int slot = -1;

    public void clearListing() {
        detectedTotal = -1;
        detectedCount = -1;
        detectedUnit = -1;
        slot = -1;
    }
}
