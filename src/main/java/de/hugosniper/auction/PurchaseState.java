package de.hugosniper.auction;

public enum PurchaseState {
    IDLE, SCANNING, CANDIDATE_FOUND, OPENING_LISTING, VERIFYING_LISTING,
    BUY_MENU_OPEN, BUY_ACTION_SENT, WAITING_FOR_RESULT, SUCCESS, CANCELLED, ERROR;

    /** Zustaende, in denen ein Kaufvorgang aktiv ist (keine neuen Kandidaten annehmen). */
    public boolean isBusy() {
        return switch (this) {
            case CANDIDATE_FOUND, OPENING_LISTING, VERIFYING_LISTING, BUY_MENU_OPEN, BUY_ACTION_SENT, WAITING_FOR_RESULT -> true;
            default -> false;
        };
    }

    public boolean isTerminal() { return this == SUCCESS || this == CANCELLED || this == ERROR; }
}
