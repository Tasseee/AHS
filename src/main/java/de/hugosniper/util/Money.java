package de.hugosniper.util;

import java.util.Locale;
import java.util.OptionalLong;

/** Reine Geld-Hilfen. Alle Betraege sind ganzzahlig (long). Keine Rundung, keine Fliesskommazahlen. */
public final class Money {
    private Money() {}

    /** Ergebnis einer Stueckpreis-Berechnung. */
    public record UnitPrice(long total, int count, long unit, boolean exact) {}

    /**
     * Stueckpreis = Gesamtpreis / Anzahl. Liefert empty bei count <= 0 oder negativem Gesamtpreis.
     * Es wird ganzzahlig AUFGERUNDET (ceil), damit ein Angebot nie guenstiger wirkt als es ist
     * (konservativ fuer eine Maximalpreis-Pruefung). {@code exact} sagt, ob die Division ohne Rest aufging.
     */
    public static java.util.Optional<UnitPrice> unitPrice(long total, int count) {
        if (count <= 0 || total < 0) return java.util.Optional.empty();
        long q = total / count;
        boolean exact = total % count == 0;
        long unit = exact ? q : q + 1;
        return java.util.Optional.of(new UnitPrice(total, count, unit, exact));
    }

    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("\\d+(?:[.,']\\d+)*");
    private static final java.util.regex.Pattern GROUPED = java.util.regex.Pattern.compile("\\d{1,3}(?:([.,'])\\d{3}(?:\\1\\d{3})*)");
    private static final java.util.regex.Pattern PLAIN = java.util.regex.Pattern.compile("\\d+");

    /** Kurzform: Zahl (max. 2 Nachkommastellen) + k/m/b/t direkt dahinter, z.B. "100k", "1.5M", "2,25m". */
    private static final java.util.regex.Pattern ABBREV =
            java.util.regex.Pattern.compile("(?<![\\d.,])(\\d+(?:[.,]\\d{1,2})?)([kKmMbBtT])(?![A-Za-z])");

    private static java.math.BigDecimal multiplier(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'k' -> java.math.BigDecimal.valueOf(1_000L);
            case 'm' -> java.math.BigDecimal.valueOf(1_000_000L);
            case 'b' -> java.math.BigDecimal.valueOf(1_000_000_000L);
            default -> java.math.BigDecimal.valueOf(1_000_000_000_000L); // t
        };
    }

    /**
     * Parst einen Preis aus einer Tooltip-/Lore-Zeile. Fail-closed: liefert nur dann einen Wert, wenn die Zeile
     * EINDEUTIG genau eine Zahl enthaelt.
     * <ul>
     *  <li>Ganze Zahlen: "1234567", "1.234.567", "1,234,567", "1'234'567" - exakt.</li>
     *  <li>Kurzformen: "100k", "100m", "1.5M", "2,25b" - der Server RUNDET sie. Zurueckgegeben wird deshalb die
     *      OBERE GRENZE des moeglichen echten Preises ("100k" -> 100.500), damit Limits nie unterschaetzt werden.</li>
     *  <li>Abgelehnt: Dezimalzahlen ohne Kuerzel ("12,50"), Kuerzel mit 3 Nachkommastellen ("1.500k", mehrdeutig),
     *      mehrere Zahlen, 0, absurd lange Zahlen.</li>
     * </ul>
     */
    public static OptionalLong parsePrice(String raw) {
        if (raw == null) return OptionalLong.empty();
        String s = raw.replace('\u00A0', ' ').replace('\u202F', ' ').trim();
        if (s.isEmpty()) return OptionalLong.empty();

        // Es darf in der ganzen Zeile nur EINE Zahl stehen.
        java.util.regex.Matcher m = TOKEN.matcher(s);
        String token = null;
        int end = -1;
        while (m.find()) {
            if (token != null) return OptionalLong.empty(); // zwei Zahlen => uneindeutig
            token = m.group();
            end = m.end();
        }
        if (token == null) return OptionalLong.empty();

        java.util.regex.Matcher a = ABBREV.matcher(s);
        if (a.find()) {
            String num = a.group(1).replace(',', '.');
            char suffix = a.group(2).charAt(0);
            if (a.find()) return OptionalLong.empty(); // zwei Kurzformen
            try {
                java.math.BigDecimal nominal = new java.math.BigDecimal(num);
                if (nominal.signum() <= 0) return OptionalLong.empty();
                java.math.BigDecimal mult = multiplier(suffix);
                // Rundungsspielraum: halbe Einheit der letzten angezeigten Stelle
                java.math.BigDecimal halfStep = java.math.BigDecimal.valueOf(5, nominal.scale() + 1);
                java.math.BigDecimal upper = nominal.add(halfStep).multiply(mult)
                        .setScale(0, java.math.RoundingMode.CEILING);
                if (upper.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE / 4)) > 0) return OptionalLong.empty();
                return OptionalLong.of(upper.longValueExact());
            } catch (NumberFormatException | ArithmeticException e) {
                return OptionalLong.empty();
            }
        }

        // Format strikt pruefen: entweder reine Ziffern oder sauber gruppiert (immer 3 Ziffern, ein Trennzeichen).
        if (!PLAIN.matcher(token).matches() && !GROUPED.matcher(token).matches()) return OptionalLong.empty();

        // Direkt angehaengte, nicht erkannte Kurzform ablehnen; ganze Woerter wie "Coins" sind ok.
        if (end < s.length()) {
            char c = Character.toLowerCase(s.charAt(end));
            if (c == 'k' || c == 'm' || c == 'b' || c == 't') {
                boolean wordFollows = end + 1 < s.length() && Character.isLetter(s.charAt(end + 1));
                if (!wordFollows) return OptionalLong.empty();
            }
        }
        String digits = token.replaceAll("[^0-9]", "");
        if (digits.length() > 18) return OptionalLong.empty();
        try {
            long v = Long.parseLong(digits);
            return v > 0 ? OptionalLong.of(v) : OptionalLong.empty(); // 0 gilt NIE als gueltiger Preis
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Formatiert mit Punkt als Tausendertrenner, unabhaengig von der Systemsprache. */
    public static String format(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }
}
