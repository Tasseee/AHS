package de.hugosniper.auction;

import de.hugosniper.config.ConfigStore;
import de.hugosniper.config.HugoConfig;
import de.hugosniper.filter.FilterRule;
import de.hugosniper.util.ActivityLog;
import de.hugosniper.util.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.*;

/**
 * Tick-basierte Steuerung. Laeuft ausschliesslich im Client-Tick (kein Thread, keine Schleife, kein Timer).
 * Alle Kaufklicks gehen durch {@link PurchaseStateMachine#authorizeBuyClick}.
 */
public final class SniperEngine {
    private static final SniperEngine INSTANCE = new SniperEngine();
    public static SniperEngine get() { return INSTANCE; }

    public enum Mode { PAUSED, ACTIVE, ERROR }

    public HugoConfig config = HugoConfig.defaults();
    public ConfigStore store;
    public final ActivityLog log = new ActivityLog(200);
    public final Map<FilterRule, FilterRow> rows = new IdentityHashMap<>();
    public final PurchaseStateMachine machine = new PurchaseStateMachine(Limits.RESPONSE_TIMEOUT_MS);

    private long tick;
    private Mode mode = Mode.PAUSED;
    private String modeDetail = "Pausiert";
    private long nextScanTick;
    private boolean manualScanRequested;
    private long manualScanDeadline;
    private int ahContainerId = -1;
    private long ahOpenSince;
    private static final int AH_SETTLE_TICKS = 6;
    private boolean refreshPending;
    private long lastScanEpoch;
    private int lastFoundCount;
    private String lastSeenContainerTitle = "";
    private boolean pauseAfterTerminal;
    private final Set<String> loggedKeys = new HashSet<>();

    // Kontext des laufenden Kaufvorgangs
    private FilterRule activeRule;
    private int listingMenuId = -1;
    private int listingSlot = -1;
    private int buyMenuId = -1;
    private int buySlot = -1;
    private int invBefore;
    private String targetId;

    private SniperEngine() {}

    // ---------------------------------------------------------------- Setup

    public void init(Path configFile) {
        store = new ConfigStore(configFile);
        config = store.load();
        for (String w : store.warnings) log.warn("Config: " + w);
        log.setMax(config.logMaxEntries);
        syncRows();
        log.info("HugoSniper geladen (" + config.filters.size() + " Filter, Auto-Kauf " + (config.autoBuyEnabled ? "AN" : "AUS") + ")");
    }

    public void saveConfig() {
        if (store != null && !store.save(config)) {
            for (String w : store.warnings) log.error(w);
        }
    }

    public void syncRows() {
        rows.keySet().removeIf(f -> !config.filters.contains(f));
        for (FilterRule f : config.filters) rows.computeIfAbsent(f, k -> new FilterRow());
    }

    // ---------------------------------------------------------------- Anzeige-Getter

    public Mode mode() { return mode; }
    public String modeDetail() { return modeDetail; }
    public int lastFoundCount() { return lastFoundCount; }
    public long lastScanEpoch() { return lastScanEpoch; }
    public String lastSeenContainerTitle() { return lastSeenContainerTitle; }
    public long currentTick() { return tick; }
    public boolean isActive() { return mode == Mode.ACTIVE; }

    // ---------------------------------------------------------------- Steuerung

    public void start() {
        if (mode == Mode.ACTIVE) return;
        if (machine.state().isTerminal()) machine.resetToIdle(tick);
        if (!machine.startScanning(tick)) { log.warn("Start nicht moeglich: Vorgang laeuft noch"); return; }
        mode = Mode.ACTIVE;
        modeDetail = "Suche aktiv";
        nextScanTick = tick;
        refreshPending = false;
        pauseAfterTerminal = false;
        log.info("Suche gestartet (Intervall " + config.refreshIntervalMs + " ms, Auto-Kauf " + (config.autoBuyEnabled ? "AN" : "AUS") + ")");
    }

    /** Pausiert und verwirft alle noch nicht ausgeloesten Aktionen. */
    public void pause(String why) {
        machine.stop(tick, why);
        machine.resetToIdle(tick);
        mode = Mode.PAUSED;
        modeDetail = "Pausiert";
        refreshPending = false;
        log.info("Pausiert" + (why == null || why.isBlank() ? "" : ": " + why));
    }

    /** Einzelscan: wartet (max. 20 s), bis ein Auction-House-Fenster offen und geladen ist. */
    public void requestSingleScan() {
        manualScanRequested = true;
        manualScanDeadline = tick + 400;
        log.info("Einzelscan angefordert - wartet auf geoeffnetes Auction House (/ah)");
    }

    public void toggle() { if (mode == Mode.ACTIVE) pause("durch Nutzer"); else start(); }

    // ---------------------------------------------------------------- Tick

    public void onClientTick(Minecraft mc) {
        tick++;
        if (mc.player == null || mc.level == null) {
            if (mode == Mode.ACTIVE) pause("Verbindung getrennt");
            return;
        }
        if (machine.tickTimeout(tick)) { handleTerminal(); }

        boolean active = mode == Mode.ACTIVE;
        if (manualScanRequested && tick > manualScanDeadline) {
            manualScanRequested = false;
            log.warn("Einzelscan verworfen: Auction House wurde nicht rechtzeitig geoeffnet");
        }
        if (!active && !manualScanRequested) return;

        try {
            switch (machine.state()) {
                case IDLE, SCANNING -> doScanning(mc, active);
                case CANDIDATE_FOUND -> doOpenListing(mc);
                case OPENING_LISTING -> doWaitForMenu(mc);
                case VERIFYING_LISTING -> doVerifyBuyMenu(mc);
                case BUY_MENU_OPEN -> doBuy(mc);
                case WAITING_FOR_RESULT, BUY_ACTION_SENT -> doWaitResult(mc);
                default -> { }
            }
        } catch (RuntimeException e) {
            // Sicherheitsnetz: nie einen Kauf "blind" fortsetzen
            machine.cancel(tick, "Interner Fehler: " + e.getClass().getSimpleName());
            log.error("Interner Fehler: " + e);
            mode = Mode.ERROR;
            modeDetail = "Fehler: " + e.getClass().getSimpleName();
            machine.resetToIdle(tick);
            return;
        }
        if (machine.state().isTerminal()) handleTerminal();
    }

    // ---------------------------------------------------------------- Scan

    private AbstractContainerScreen<?> auctionScreen(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        String title = cs.getTitle().getString();
        lastSeenContainerTitle = title;
        if (ContainerReader.containsAny(title.toLowerCase(Locale.ROOT), config.auctionTitleHints)) return cs;
        return null;
    }

    private int effectivePauseMs(FilterRule r) {
        return Math.max(config.interactionDelayMs, r == null ? 0 : r.minPauseMs);
    }

    private void doScanning(Minecraft mc, boolean active) {
        AbstractContainerScreen<?> cs = auctionScreen(mc);
        if (cs == null) {
            modeDetail = lastSeenContainerTitle.isEmpty() || !(mc.screen instanceof AbstractContainerScreen)
                    ? "Auction House nicht geoeffnet (/ah)"
                    : "Fenster '" + lastSeenContainerTitle + "' nicht als Auction House erkannt";
            for (FilterRow r : rows.values()) if (r.status != RowStatus.DISABLED) { r.status = RowStatus.SEARCHING; r.clearListing(); }
            ahContainerId = -1;
            return;
        }
        // Server sendet die Slots verzoegert: erst nach kurzer Beruhigungszeit lesen
        if (cs.getMenu().containerId != ahContainerId) { ahContainerId = cs.getMenu().containerId; ahOpenSince = tick; }
        if (tick - ahOpenSince < AH_SETTLE_TICKS) return;
        boolean manual = manualScanRequested;
        if (!manual && tick < nextScanTick) return;
        nextScanTick = tick + Limits.msToTicks(config.refreshIntervalMs);

        AbstractContainerMenu menu = cs.getMenu();
        // Optional: serverseitigen Refresh-Button anklicken (nur wenn ausdruecklich aktiviert und EINDEUTIG)
        if (active && config.activeRefreshEnabled && !manual) {
            if (!refreshPending) {
                List<Slot> btn = findByHints(menu, config.refreshHints);
                if (btn.size() == 1 && machine.interactionDue(tick, config.interactionDelayMs)) {
                    click(mc, menu.containerId, btn.get(0).index);
                    machine.markRefreshAction(tick);
                    refreshPending = true;
                    nextScanTick = tick + Limits.msToTicks(config.interactionDelayMs);
                    modeDetail = "Aktualisiere (Server-Refresh-Button)";
                    return;
                }
                modeDetail = btn.size() == 1 ? "Suche aktiv" : "Refresh-Button nicht eindeutig (" + btn.size() + ") - passiver Modus";
            } else {
                refreshPending = false;
            }
        } else if (active) {
            modeDetail = "Suche aktiv (passiv: liest den vom Server gelieferten Inhalt)";
        }

        manualScanRequested = false;
        if (manual) log.info("Scan gestartet (manuell)");
        scan(mc, menu, active);
    }

    private void scan(Minecraft mc, AbstractContainerMenu menu, boolean allowPurchase) {
        Map<FilterRule, Object[]> bestHit = new IdentityHashMap<>();   // {snapshot, evaluation}
        Map<FilterRule, Object[]> bestMiss = new IdentityHashMap<>();
        int hits = 0;
        int total = 0;

        for (Slot sl : ContainerReader.containerSlots(menu)) {
            ItemStack st = sl.getItem();
            if (st.isEmpty()) continue;
            ListingSnapshot snap = ContainerReader.snapshot(sl.index, st, config.priceLineHints);
            for (FilterRule f : config.filters) {
                if (!f.enabled || !f.matchesId(snap.registryId())) continue;
                total++;
                Evaluation ev = ListingEvaluator.evaluate(snap, f, config.autoBuyEnabled, config.maxAutoTotalPrice);
                if (ev.verdict().isHit()) {
                    hits++;
                    Object[] cur = bestHit.get(f);
                    if (cur == null || ev.unit() < ((Evaluation) cur[1]).unit()) bestHit.put(f, new Object[]{snap, ev});
                } else {
                    Object[] cur = bestMiss.get(f);
                    if (cur == null || ev.unit() >= 0 && (((Evaluation) cur[1]).unit() < 0 || ev.unit() < ((Evaluation) cur[1]).unit())) {
                        bestMiss.put(f, new Object[]{snap, ev});
                    }
                    logOnce(snap, f, ev);
                }
            }
        }

        lastScanEpoch = System.currentTimeMillis();
        lastFoundCount = hits;

        FilterRule pickRule = null;
        ListingSnapshot pickSnap = null;
        Evaluation pickEval = null;

        for (FilterRule f : config.filters) {
            FilterRow row = rows.computeIfAbsent(f, k -> new FilterRow());
            if (!f.enabled) { row.status = RowStatus.DISABLED; row.clearListing(); continue; }
            Object[] hit = bestHit.get(f);
            Object[] miss = bestMiss.get(f);
            if (hit != null) {
                ListingSnapshot s = (ListingSnapshot) hit[0];
                Evaluation ev = (Evaluation) hit[1];
                fillRow(row, s, ev);
                row.status = ev.verdict() == Verdict.BUY_ALLOWED ? RowStatus.FOUND : RowStatus.CHECK;
                row.note = ev.detail();
                logOnce(s, f, ev);
                if (allowPurchase && config.autoBuyEnabled && f.autoBuy && pickRule == null && wantsOpen(ev)) {
                    pickRule = f; pickSnap = s; pickEval = ev;
                }
            } else if (miss != null) {
                ListingSnapshot s = (ListingSnapshot) miss[0];
                Evaluation ev = (Evaluation) miss[1];
                fillRow(row, s, ev);
                row.status = (ev.verdict() == Verdict.PRICE_UNKNOWN || ev.verdict() == Verdict.PRICE_AMBIGUOUS
                        || ev.verdict() == Verdict.ID_UNKNOWN) ? RowStatus.ERROR : RowStatus.SKIPPED;
                row.note = ev.verdict().label + (ev.detail().isEmpty() ? "" : " (" + ev.detail() + ")");
            } else {
                row.status = RowStatus.SEARCHING;
                row.clearListing();
                row.note = "";
            }
        }

        if (pickRule != null && machine.state() != PurchaseState.CANDIDATE_FOUND) {
            if (machine.state() == PurchaseState.IDLE) machine.startScanning(tick);
            if (machine.begin(pickSnap, pickEval, tick)) {
                activeRule = pickRule;
                targetId = pickSnap.registryId();
                listingMenuId = menu.containerId;
                listingSlot = pickSnap.slot();
                invBefore = ContainerReader.countInInventory(mc, targetId);
                log.info("Angebot gefunden: " + pickRule.displayName + " x" + pickSnap.count() + " fuer " + Money.format(pickEval.total())
                        + " (" + Money.format(pickEval.unit()) + "/Stk)");
            }
        }
        if (loggedKeys.size() > 1000) loggedKeys.clear();
    }

    /** Nur oeffnen, wenn ein Kauf erlaubt ist ODER der Preis ueber der Sicherheitsgrenze liegt (Nutzer entscheidet im Server-Menue). */
    private boolean wantsOpen(Evaluation ev) {
        long cap = Math.min(Math.max(config.maxAutoTotalPrice, 0), Limits.HARD_MAX_AUTO_TOTAL);
        return ev.verdict() == Verdict.BUY_ALLOWED || (ev.verdict() == Verdict.NEEDS_MANUAL_CONFIRM && ev.total() > cap);
    }

    private void fillRow(FilterRow row, ListingSnapshot s, Evaluation ev) {
        row.detectedCount = s.count();
        row.detectedTotal = ev.total();
        row.detectedUnit = ev.unit();
        row.slot = s.slot();
    }

    private void logOnce(ListingSnapshot s, FilterRule f, Evaluation ev) {
        String key = f.registryId + "|" + s.fingerprint() + "|" + ev.verdict();
        if (!loggedKeys.add(key)) return;
        switch (ev.verdict()) {
            case BUY_ALLOWED, NEEDS_MANUAL_CONFIRM -> log.info("Treffer: " + f.displayName + " x" + s.count() + " " + Money.format(ev.total()) + " [" + ev.verdict().label + "]");
            case ID_UNKNOWN -> log.warn("Registry-ID unklar (Slot " + s.slot() + ")");
            case PRICE_UNKNOWN, PRICE_AMBIGUOUS -> log.warn(f.displayName + ": " + ev.verdict().label + " - uebersprungen (Slot " + s.slot() + ")");
            case OVER_UNIT_LIMIT, OVER_TOTAL_LIMIT, OVER_QUANTITY -> log.info(f.displayName + ": Preislimit/Menge ueberschritten (" + ev.detail() + ")");
            default -> log.info(f.displayName + ": " + ev.verdict().label + (ev.detail().isEmpty() ? "" : " (" + ev.detail() + ")"));
        }
    }

    // ---------------------------------------------------------------- Kaufablauf

    private FilterRow activeRow() { return activeRule == null ? null : rows.get(activeRule); }

    private AbstractContainerScreen<?> containerScreen(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?> cs ? cs : null;
    }

    private void doOpenListing(Minecraft mc) {
        AbstractContainerScreen<?> cs = containerScreen(mc);
        if (cs == null || cs.getMenu().containerId != listingMenuId) {
            machine.cancel(tick, "Auction House nicht mehr offen");
            return;
        }
        if (!machine.interactionDue(tick, effectivePauseMs(activeRule))) return;

        AbstractContainerMenu menu = cs.getMenu();
        Slot sl = ContainerReader.slotById(menu, listingSlot);
        if (sl == null || sl.getItem().isEmpty()) { machine.cancel(tick, "Angebot nicht mehr vorhanden"); return; }
        ListingSnapshot now = ContainerReader.snapshot(listingSlot, sl.getItem(), config.priceLineHints);
        Evaluation ev = ListingEvaluator.evaluate(now, activeRule, config.autoBuyEnabled, config.maxAutoTotalPrice);
        if (!now.fingerprint().equals(machine.candidate().fingerprint()) || !ev.verdict().isHit()) {
            machine.cancel(tick, "Angebot hat sich geaendert oder passt nicht mehr");
            return;
        }
        click(mc, menu.containerId, listingSlot);
        machine.markListingClick(tick);
        FilterRow row = activeRow();
        if (row != null) row.status = RowStatus.CHECK;
        log.info("Angebot geoeffnet (Klick gesendet, Slot " + listingSlot + ")");
    }

    private void doWaitForMenu(Minecraft mc) {
        AbstractContainerScreen<?> cs = containerScreen(mc);
        if (cs == null) {
            // Menue evtl. noch nicht geoeffnet; ein komplett anderer Bildschirm/ geschlossenes GUI wird abgebrochen
            if (mc.screen != null) machine.onUnexpectedMenu(tick, "Bildschirm " + mc.screen.getClass().getSimpleName());
            else machine.onUnexpectedMenu(tick, "Menue geschlossen");
            return;
        }
        if (cs.getMenu().containerId != listingMenuId) {
            buyMenuId = cs.getMenu().containerId;
            machine.onMenuOpened(tick);
        }
    }

    /** Sucht Slots des Containers, deren Name/Lore ein Hinweiswort enthaelt. */
    private List<Slot> findByHints(AbstractContainerMenu menu, List<String> hints) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : ContainerReader.containerSlots(menu)) {
            if (s.getItem().isEmpty()) continue;
            if (ContainerReader.containsAny(ContainerReader.searchText(s.getItem()), hints)) out.add(s);
        }
        return out;
    }

    /** Findet im Kaufmenue die Darstellung des Kandidaten (gleiche ID + Anzahl), genau eine. */
    private Optional<ListingSnapshot> findShownListing(AbstractContainerMenu menu) {
        ListingSnapshot cand = machine.candidate();
        ListingSnapshot found = null;
        for (Slot s : ContainerReader.containerSlots(menu)) {
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            if (!cand.registryId().equals(ContainerReader.registryId(st)) || st.getCount() != cand.count()) continue;
            if (found != null) return Optional.empty(); // mehrdeutig
            found = ContainerReader.snapshot(s.index, st, config.priceLineHints);
        }
        return Optional.ofNullable(found);
    }

    private void doVerifyBuyMenu(Minecraft mc) {
        AbstractContainerScreen<?> cs = containerScreen(mc);
        if (cs == null || cs.getMenu().containerId != buyMenuId) {
            machine.onUnexpectedMenu(tick, "Menuewechsel waehrend der Pruefung");
            return;
        }
        AbstractContainerMenu menu = cs.getMenu();
        ListingSnapshot cand = machine.candidate();

        boolean confirmSeen = !findByHints(menu, config.confirmHints).isEmpty();
        List<Slot> buys = findByHints(menu, config.buyButtonHints);
        Optional<ListingSnapshot> shown = findShownListing(menu);

        if (shown.isEmpty()) { machine.cancel(tick, "Angebot im Kaufmenue nicht eindeutig gefunden"); return; }
        ListingSnapshot s = shown.get();

        ListingSnapshot cmp = s;
        if (s.priceCandidates().isEmpty()) {
            if (config.requirePriceInBuyMenu) {
                log.warn("Kaufmenue zeigt keinen Preis - nicht automatisch kaufen (Einstellung 'Preis im Kaufmenue verlangen')");
                machine.onBuyMenuVerified(cand, tick);   // fuer saubere Zustandsfolge; es folgt KEIN Kaufklick
                machine.handOverToUser(tick, "Preis im Kaufmenue nicht verifizierbar");
                pauseAfterTerminal = true;
                return;
            }
            cmp = new ListingSnapshot(s.slot(), s.registryId(), s.count(), s.displayName(), cand.priceCandidates(), s.traits());
        }
        if (!machine.onBuyMenuVerified(cmp, tick)) return; // hat selbst abgebrochen (Daten geaendert)

        FilterRow row = activeRow();
        if (row != null) row.status = RowStatus.BUY_MENU;
        log.info("Kaufmenue erkannt");

        long cap = Math.min(Math.max(config.maxAutoTotalPrice, 0), Limits.HARD_MAX_AUTO_TOTAL);
        if (machine.evaluation().verdict() != Verdict.BUY_ALLOWED || machine.evaluation().total() > cap) {
            log.warn("Serverbestaetigung erforderlich / Preis ueber " + Money.format(cap) + ": HugoSniper klickt NICHT. Menue bleibt offen, du entscheidest.");
            machine.handOverToUser(tick, "Preis ueber Sicherheitsgrenze");
            pauseAfterTerminal = true;
            return;
        }
        if (confirmSeen) {
            log.warn("Server zeigt ein Bestaetigungsmenue - HugoSniper klickt NICHT. Du entscheidest.");
            machine.handOverToUser(tick, "Serverbestaetigung erforderlich");
            pauseAfterTerminal = true;
            return;
        }
        if (buys.size() != 1) {
            machine.cancel(tick, "Buy-Button nicht eindeutig (" + buys.size() + " Treffer)");
            log.warn("Buy-Button nicht eindeutig erkannt (" + buys.size() + " Treffer) - kein Kauf. Hinweiswoerter in den Einstellungen pruefen.");
            pauseAfterTerminal = true;
            return;
        }
        buySlot = buys.get(0).index;
        invBefore = ContainerReader.countInInventory(mc, targetId);
    }

    private void doBuy(Minecraft mc) {
        AbstractContainerScreen<?> cs = containerScreen(mc);
        if (cs == null || cs.getMenu().containerId != buyMenuId) {
            machine.onUnexpectedMenu(tick, "Menuewechsel vor dem Kauf");
            return;
        }
        AbstractContainerMenu menu = cs.getMenu();

        // Vor JEDEM Klick erneut pruefen: Angebot unveraendert und Buy-Button noch derselbe, eindeutige Slot.
        Optional<ListingSnapshot> shown = findShownListing(menu);
        if (shown.isEmpty()) { machine.cancel(tick, "Angebot im Kaufmenue nicht mehr eindeutig"); return; }
        List<Slot> buys = findByHints(menu, config.buyButtonHints);
        if (buys.size() != 1 || buys.get(0).index != buySlot) { machine.cancel(tick, "Buy-Button hat sich veraendert"); return; }
        if (!findByHints(menu, config.confirmHints).isEmpty()) {
            machine.handOverToUser(tick, "Serverbestaetigung erforderlich");
            pauseAfterTerminal = true;
            return;
        }

        if (!machine.authorizeBuyClick(tick, effectivePauseMs(activeRule), config.maxAutoTotalPrice)) return; // wartet auf Pause
        click(mc, buyMenuId, buySlot);
        log.warn("Kaufaktion ausgeloest (Klick gesendet - noch NICHT bestaetigt)");
        machine.awaitResult(tick);
    }

    private void doWaitResult(Minecraft mc) {
        ListingSnapshot cand = machine.candidate();
        if (cand == null || targetId == null) return;
        int now = ContainerReader.countInInventory(mc, targetId);
        if (now >= invBefore + cand.count()) {
            machine.onPurchaseConfirmed(tick);
            log.ok("Kauf nachweislich abgeschlossen: " + activeRule.displayName + " x" + cand.count() + " (Inventar +" + (now - invBefore) + ")");
        }
        // Sonst: weiter warten; Timeout -> ERROR ("Ergebnis unklar"), niemals erneut klicken.
    }

    private void handleTerminal() {
        PurchaseState st = machine.state();
        String why = machine.reason();
        FilterRow row = activeRow();
        switch (st) {
            case SUCCESS -> { if (row != null) { row.status = RowStatus.FOUND; row.note = "Gekauft"; } }
            case CANCELLED -> {
                log.warn("Vorgang abgebrochen: " + why);
                if (row != null) { row.status = RowStatus.SKIPPED; row.note = why; }
            }
            case ERROR -> {
                log.error("Fehler: " + why);
                if (row != null) { row.status = RowStatus.ERROR; row.note = why; }
                mode = Mode.ERROR;
                modeDetail = why;
            }
            default -> { return; }
        }
        activeRule = null;
        listingSlot = buySlot = -1;
        listingMenuId = buyMenuId = -1;
        if (st == PurchaseState.ERROR) {
            machine.resetToIdle(tick);        // sicherer Zustand, Engine bleibt in ERROR bis Nutzer neu startet
        } else if (pauseAfterTerminal) {
            pauseAfterTerminal = false;
            machine.resetToIdle(tick);
            mode = Mode.PAUSED;
            modeDetail = "Pausiert: " + why;
        } else if (mode == Mode.ACTIVE) {
            machine.recover(tick);
        } else {
            machine.resetToIdle(tick);
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private static void click(Minecraft mc, int containerId, int slotId) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(containerId, slotId, 0, ClickType.PICKUP, mc.player);
    }
}
