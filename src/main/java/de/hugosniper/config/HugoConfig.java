package de.hugosniper.config;

import de.hugosniper.auction.Limits;
import de.hugosniper.filter.FilterRule;

import java.util.ArrayList;
import java.util.List;

/** Persistierte Einstellungen (config/hugo_sniper.json). Gson-kompatibles POJO. */
public final class HugoConfig {
    public static final int CURRENT_VERSION = 1;

    public int configVersion = CURRENT_VERSION;
    public int refreshIntervalMs = Limits.DEFAULT_REFRESH_MS;
    public int interactionDelayMs = Limits.DEFAULT_INTERACTION_MS;
    /** Standardmaessig AUS. Muss ausdruecklich aktiviert werden. */
    public boolean autoBuyEnabled = false;
    public long maxAutoTotalPrice = Limits.HARD_MAX_AUTO_TOTAL;
    public boolean hudEnabled = true;
    public int hudX = 6;
    public int hudY = 6;
    public int logMaxEntries = 200;
    public boolean logToGameLog = true;
    /** Woerter im Fenstertitel, die das Auction House kennzeichnen (kleingeschrieben, "enthaelt"). */
    public List<String> auctionTitleHints = new ArrayList<>(List.of("auction", "auktion", "auktionshaus"));
    /** Woerter im Item-Namen/Lore des Buy-Buttons. Es muss GENAU ein Slot passen, sonst kein Kauf. */
    public List<String> buyButtonHints = new ArrayList<>(List.of("buy", "kaufen", "purchase"));
    /** Woerter, die ein zusaetzliches Server-Bestaetigungsmenue anzeigen (Hand-over an den Nutzer). */
    public List<String> confirmHints = new ArrayList<>(List.of("confirm", "bestaetig", "bestätig"));
    /** Woerter eines serverseitigen Refresh-Buttons (nur genutzt, wenn activeRefreshEnabled). */
    public List<String> refreshHints = new ArrayList<>(List.of("refresh", "aktualisier", "neu laden"));
    /** Nur Lore-Zeilen mit einem dieser Woerter gelten als Preiszeile (verhindert "laeuft ab in 23 Stunden" als Preis). */
    public List<String> priceLineHints = new ArrayList<>(List.of("price", "preis", "cost", "kosten", "$", "coins"));
    /** Aktiver Refresh-Klick. STANDARD AUS: Ohne ihn wird nur der vom Server gelieferte Inhalt gelesen (passiv). */
    public boolean activeRefreshEnabled = false;
    /** Strikt: Das Kaufmenue muss den Preis nochmals zeigen, sonst wird nicht automatisch gekauft. */
    public boolean requirePriceInBuyMenu = true;
    public List<FilterRule> filters = new ArrayList<>();

    public static HugoConfig defaults() {
        HugoConfig c = new HugoConfig();
        FilterRule dragon = new FilterRule("Dragon Head", "minecraft:dragon_head", 500_000);
        dragon.maxQuantity = 4;
        dragon.maxTotalPrice = 2_000_000;
        c.filters.add(dragon);
        return c;
    }
}
