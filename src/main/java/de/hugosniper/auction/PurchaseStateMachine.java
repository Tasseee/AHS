package de.hugosniper.auction;

import java.util.HashMap;
import java.util.Map;

/**
 * Zustandsmaschine fuer genau EINEN Kaufvorgang. Zeit wird als Client-Tick-Zaehler uebergeben (kein System-Timer),
 * dadurch deterministisch testbar. Keine Minecraft-Klassen.
 *
 * Sicherheitsgarantien:
 *  - nur ein Vorgang gleichzeitig ({@link #begin})
 *  - der Buy-Klick kann pro Vorgang hoechstens EINMAL freigegeben werden ({@link #authorizeBuyClick})
 *  - Gesamtpreis > Sicherheitsgrenze => nie freigegeben
 *  - Timeout/Fehler fuehren nie zu einer automatischen Wiederholung
 */
public final class PurchaseStateMachine {

    private PurchaseState state = PurchaseState.IDLE;
    private long stateSinceTick;
    private long lastActionTick = Long.MIN_VALUE / 2;
    private boolean buyClickAuthorized;
    private ListingSnapshot candidate;
    private Evaluation evaluation;
    private String reason = "";
    private final Map<String, Long> blockedUntil = new HashMap<>();
    private final long timeoutTicks;
    private final long refusedCooldownTicks;

    public PurchaseStateMachine(int responseTimeoutMs) {
        this.timeoutTicks = Limits.msToTicks(responseTimeoutMs);
        this.refusedCooldownTicks = Limits.msToTicks(30_000);
    }

    public PurchaseState state() { return state; }
    public String reason() { return reason; }
    public ListingSnapshot candidate() { return candidate; }
    public Evaluation evaluation() { return evaluation; }
    public boolean isBuyClickAuthorized() { return buyClickAuthorized; }

    private void to(PurchaseState s, long tick, String why) {
        state = s;
        stateSinceTick = tick;
        reason = why == null ? "" : why;
    }

    /** Suche aktivieren. Nur aus Ruhe-/Endzustaenden erlaubt. */
    public boolean startScanning(long tick) {
        if (state.isBusy()) return false;
        to(PurchaseState.SCANNING, tick, "Scan aktiv");
        return true;
    }

    /** Pause/Stopp: bricht jeden laufenden Vorgang ab, ohne irgendetwas zu senden. */
    public void stop(long tick, String why) {
        buyClickAuthorized = false;
        if (state.isBusy()) to(PurchaseState.CANCELLED, tick, why); else to(PurchaseState.IDLE, tick, why);
    }

    /** Zurueck in einen sicheren Zustand. */
    public void resetToIdle(long tick) {
        buyClickAuthorized = false;
        candidate = null;
        evaluation = null;
        to(PurchaseState.IDLE, tick, "");
    }

    public boolean isBlocked(ListingSnapshot l, long tick) {
        Long until = blockedUntil.get(l.fingerprint());
        return until != null && tick < until;
    }

    /** Neuen Kandidaten annehmen. Nur wenn kein Vorgang laeuft und das Angebot nicht gesperrt ist. */
    public boolean begin(ListingSnapshot l, Evaluation ev, long tick) {
        if (state.isBusy() || l == null || ev == null || !ev.verdict().isHit()) return false;
        if (isBlocked(l, tick)) return false;
        candidate = l;
        evaluation = ev;
        buyClickAuthorized = false;
        to(PurchaseState.CANDIDATE_FOUND, tick, "Kandidat Slot " + l.slot());
        return true;
    }

    /** Bereit fuer die naechste GUI-Interaktion (Mindestpause eingehalten)? */
    public boolean interactionDue(long tick, int minPauseMs) {
        return tick - lastActionTick >= Limits.msToTicks(minPauseMs);
    }

    /** Ein (optionaler) Refresh-Klick wurde gesendet; startet die Mindestpause neu. */
    public void markRefreshAction(long tick) { lastActionTick = tick; }

    /** Listing-Klick wurde gesendet. */
    public boolean markListingClick(long tick) {
        if (state != PurchaseState.CANDIDATE_FOUND) return false;
        lastActionTick = tick;
        to(PurchaseState.OPENING_LISTING, tick, "Angebot angeklickt");
        return true;
    }

    /** Es hat sich ein Menue geoeffnet; Inhalt wird geprueft. */
    public boolean onMenuOpened(long tick) {
        if (state != PurchaseState.OPENING_LISTING) return false;
        to(PurchaseState.VERIFYING_LISTING, tick, "Menue geoeffnet, pruefe");
        return true;
    }

    /** Das Kaufmenue wurde erkannt UND Angebotsdaten stimmen noch mit dem Kandidaten ueberein. */
    public boolean onBuyMenuVerified(ListingSnapshot shownInMenu, long tick) {
        if (state != PurchaseState.VERIFYING_LISTING || candidate == null || shownInMenu == null) return false;
        if (!candidate.fingerprint().equals(shownInMenu.fingerprint())) {
            cancel(tick, "Angebotsdaten haben sich geaendert");
            return false;
        }
        to(PurchaseState.BUY_MENU_OPEN, tick, "Kaufmenue verifiziert");
        return true;
    }

    /**
     * Darf JETZT der Buy-Klick gesendet werden? Gibt true hoechstens einmal pro Vorgang zurueck.
     * Alle harten Grenzen werden hier ein letztes Mal geprueft.
     */
    public boolean authorizeBuyClick(long tick, int minPauseMs, long autoCap) {
        if (state != PurchaseState.BUY_MENU_OPEN || buyClickAuthorized) return false;
        if (evaluation == null || evaluation.verdict() != Verdict.BUY_ALLOWED) return false;
        long cap = Math.min(Math.max(autoCap, 0), Limits.HARD_MAX_AUTO_TOTAL);
        if (evaluation.total() <= 0 || evaluation.total() > cap) return false;
        if (!interactionDue(tick, minPauseMs)) return false;
        buyClickAuthorized = true;
        lastActionTick = tick;
        to(PurchaseState.BUY_ACTION_SENT, tick, "Buy-Klick gesendet (noch NICHT bestaetigt)");
        return true;
    }

    /** Nach dem Senden: auf Ergebnis warten. */
    public boolean awaitResult(long tick) {
        if (state != PurchaseState.BUY_ACTION_SENT) return false;
        to(PurchaseState.WAITING_FOR_RESULT, tick, "Warte auf Serverergebnis");
        return true;
    }

    /** Nachweis (Inventar/GUI) dass der Kauf durchging. */
    public boolean onPurchaseConfirmed(long tick) {
        if (state != PurchaseState.WAITING_FOR_RESULT && state != PurchaseState.BUY_ACTION_SENT) return false;
        blockCandidate(tick);
        to(PurchaseState.SUCCESS, tick, "Kauf nachweislich abgeschlossen");
        return true;
    }

    /** Unerwarteter Menuewechsel => Abbruch, nie Wiederholung. */
    public void onUnexpectedMenu(long tick, String what) {
        if (!state.isBusy()) return;
        boolean afterClick = state == PurchaseState.BUY_ACTION_SENT || state == PurchaseState.WAITING_FOR_RESULT;
        blockCandidate(tick);
        buyClickAuthorized = buyClickAuthorized && afterClick;
        to(afterClick ? PurchaseState.ERROR : PurchaseState.CANCELLED, tick, "Unerwartetes Menue: " + what);
    }

    public void cancel(long tick, String why) {
        if (!state.isBusy()) return;
        blockCandidate(tick);
        to(PurchaseState.CANCELLED, tick, why);
    }

    /** Bewusst zu pruefendes Angebot (z.B. > Grenze): Vorgang sauber beenden, Menue offen lassen. */
    public void handOverToUser(long tick, String why) {
        if (!state.isBusy()) return;
        blockCandidate(tick);
        to(PurchaseState.CANCELLED, tick, why);
    }

    /** Pro Tick aufrufen. Beendet haengende Vorgaenge. Gibt true zurueck, wenn ein Timeout auftrat. */
    public boolean tickTimeout(long tick) {
        if (!state.isBusy()) return false;
        if (tick - stateSinceTick < timeoutTicks) return false;
        boolean afterClick = state == PurchaseState.BUY_ACTION_SENT || state == PurchaseState.WAITING_FOR_RESULT;
        blockCandidate(tick);
        // Nach gesendetem Klick: Ergebnis UNKLAR -> ERROR (kein Retry). Davor: einfach abbrechen.
        to(afterClick ? PurchaseState.ERROR : PurchaseState.CANCELLED, tick,
                afterClick ? "Timeout nach Buy-Klick - Ergebnis unklar, bitte Inventar pruefen" : "Timeout - Vorgang abgebrochen");
        return true;
    }

    /** Nach Ende eines Vorgangs kann der Scan weiterlaufen. */
    public boolean recover(long tick) {
        if (!state.isTerminal()) return false;
        buyClickAuthorized = false;
        candidate = null;
        evaluation = null;
        to(PurchaseState.SCANNING, tick, "Scan aktiv");
        return true;
    }

    private void blockCandidate(long tick) {
        if (candidate != null) blockedUntil.put(candidate.fingerprint(), tick + refusedCooldownTicks);
    }
}
