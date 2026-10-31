package de.hugosniper;

import de.hugosniper.auction.*;
import de.hugosniper.filter.FilterRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseStateMachineTest {
    static final long CAP = Limits.HARD_MAX_AUTO_TOTAL;

    static ListingSnapshot snap(long price) {
        return new ListingSnapshot(12, "minecraft:dragon_head", 1, "Dragon Head", List.of(price), List.of());
    }

    static Evaluation eval(ListingSnapshot s) {
        FilterRule r = new FilterRule("Dragon Head", "minecraft:dragon_head", Long.MAX_VALUE / 4);
        r.autoBuy = true;
        return ListingEvaluator.evaluate(s, r, true, CAP);
    }

    /** Bringt die Maschine bis BUY_MENU_OPEN. */
    static PurchaseStateMachine atBuyMenu(ListingSnapshot s, long[] tick) {
        var m = new PurchaseStateMachine(5000);
        assertTrue(m.startScanning(tick[0]));
        assertTrue(m.begin(s, eval(s), tick[0]));
        tick[0] += 10;
        assertTrue(m.markListingClick(tick[0]));
        tick[0] += 2;
        assertTrue(m.onMenuOpened(tick[0]));
        tick[0] += 2;
        assertTrue(m.onBuyMenuVerified(s, tick[0]));
        return m;
    }

    @Test void happyPathNeedsConfirmationBeforeSuccess() {
        long[] t = {100};
        var s = snap(1_000);
        var m = atBuyMenu(s, t);
        t[0] += 10;
        assertTrue(m.authorizeBuyClick(t[0], 400, CAP));
        assertEquals(PurchaseState.BUY_ACTION_SENT, m.state());
        assertNotEquals(PurchaseState.SUCCESS, m.state()); // gesendeter Klick != Kauf
        assertTrue(m.awaitResult(t[0] + 1));
        assertTrue(m.onPurchaseConfirmed(t[0] + 5));
        assertEquals(PurchaseState.SUCCESS, m.state());
    }

    @Test void buyClickCanOnlyBeAuthorizedOnce() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        t[0] += 10;
        assertTrue(m.authorizeBuyClick(t[0], 400, CAP));
        assertFalse(m.authorizeBuyClick(t[0] + 100, 400, CAP));
        assertFalse(m.authorizeBuyClick(t[0] + 1000, 400, CAP));
    }

    @Test void noSecondCandidateWhileBusy() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        var other = snap(2_000);
        assertFalse(m.begin(other, eval(other), t[0]));
    }

    @Test void overHardCapIsNeverAuthorized() {
        long[] t = {100};
        var s = snap(10_000_001);
        var m = new PurchaseStateMachine(5000);
        m.startScanning(t[0]);
        // Bewertung liefert NEEDS_MANUAL_CONFIRM => begin klappt (Nutzer soll es sehen), aber nie ein Buy-Klick
        assertEquals(Verdict.NEEDS_MANUAL_CONFIRM, eval(s).verdict());
        assertTrue(m.begin(s, eval(s), t[0]));
        m.markListingClick(t[0] + 10);
        m.onMenuOpened(t[0] + 12);
        m.onBuyMenuVerified(s, t[0] + 14);
        assertFalse(m.authorizeBuyClick(t[0] + 500, 400, CAP));
        assertFalse(m.authorizeBuyClick(t[0] + 500, 400, Long.MAX_VALUE));
        m.handOverToUser(t[0] + 20, "ueber Grenze - Nutzer entscheidet");
        assertEquals(PurchaseState.CANCELLED, m.state());
    }

    @Test void interactionDelayIsRespected() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        // letzte Aktion war markListingClick bei t=110; 400ms = 8 Ticks
        assertFalse(m.authorizeBuyClick(t[0], 1500, CAP));
        assertTrue(m.authorizeBuyClick(t[0] + 40, 400, CAP));
    }

    @Test void changedListingDataAbortsBeforeBuy() {
        long[] t = {100};
        var s = snap(1_000);
        var m = new PurchaseStateMachine(5000);
        m.startScanning(t[0]);
        m.begin(s, eval(s), t[0]);
        m.markListingClick(t[0] + 10);
        m.onMenuOpened(t[0] + 12);
        assertFalse(m.onBuyMenuVerified(snap(9_999_999), t[0] + 14));
        assertEquals(PurchaseState.CANCELLED, m.state());
        assertFalse(m.authorizeBuyClick(t[0] + 100, 400, CAP));
    }

    @Test void unexpectedMenuChangeCancelsPendingActions() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        m.onUnexpectedMenu(t[0] + 1, "anderes Menue");
        assertEquals(PurchaseState.CANCELLED, m.state());
        assertFalse(m.authorizeBuyClick(t[0] + 100, 400, CAP));
        assertFalse(m.isBuyClickAuthorized());
    }

    @Test void unexpectedMenuAfterBuyClickIsErrorWithoutRetry() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        assertTrue(m.authorizeBuyClick(t[0] + 40, 400, CAP));
        m.onUnexpectedMenu(t[0] + 45, "Menue weg");
        assertEquals(PurchaseState.ERROR, m.state());
        assertFalse(m.authorizeBuyClick(t[0] + 500, 400, CAP));
    }

    @Test void timeoutBeforeClickCancelsAndRecovers() {
        long[] t = {100};
        var s = snap(1_000);
        var m = new PurchaseStateMachine(5000);
        m.startScanning(t[0]);
        m.begin(s, eval(s), t[0]);
        m.markListingClick(t[0] + 5);
        assertFalse(m.tickTimeout(t[0] + 50));
        assertTrue(m.tickTimeout(t[0] + 5 + 100)); // 5000ms = 100 Ticks
        assertEquals(PurchaseState.CANCELLED, m.state());
        assertTrue(m.recover(t[0] + 120));
        assertEquals(PurchaseState.SCANNING, m.state());
    }

    @Test void timeoutAfterBuyClickIsErrorAndNeverRetries() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        long clickTick = t[0] + 40;
        assertTrue(m.authorizeBuyClick(clickTick, 400, CAP));
        m.awaitResult(clickTick + 1);
        assertTrue(m.tickTimeout(clickTick + 1 + 100));
        assertEquals(PurchaseState.ERROR, m.state());
        assertTrue(m.reason().contains("Ergebnis unklar"));
        assertTrue(m.recover(clickTick + 200));
        assertFalse(m.authorizeBuyClick(clickTick + 300, 400, CAP)); // nur im Zustand BUY_MENU_OPEN moeglich
    }

    @Test void sameListingIsBlockedAfterwardsNoDoublePurchase() {
        long[] t = {100};
        var s = snap(1_000);
        var m = atBuyMenu(s, t);
        m.authorizeBuyClick(t[0] + 40, 400, CAP);
        m.awaitResult(t[0] + 41);
        m.onPurchaseConfirmed(t[0] + 50);
        m.recover(t[0] + 51);
        assertTrue(m.isBlocked(s, t[0] + 60));
        assertFalse(m.begin(s, eval(s), t[0] + 60));
    }

    @Test void pauseCancelsOutstandingActionWithoutSending() {
        long[] t = {100};
        var m = atBuyMenu(snap(1_000), t);
        m.stop(t[0] + 1, "Pausiert");
        assertEquals(PurchaseState.CANCELLED, m.state());
        assertFalse(m.authorizeBuyClick(t[0] + 100, 400, CAP));
    }
}
