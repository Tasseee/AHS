package de.hugosniper.auction;

import java.util.List;
import java.util.OptionalLong;

/**
 * Unveraenderlicher Schnappschuss eines Angebots, wie es der Client aus dem Server-GUI ausgelesen hat.
 * Enthaelt nur Rohdaten. Die Bewertung passiert in {@link ListingEvaluator}.
 *
 * @param slot          Slotindex im geoeffneten Container
 * @param registryId    ECHTE Registry-ID des ItemStacks (nicht der Anzeigename!) oder null, wenn unbekannt
 * @param count         Stack-Anzahl (kann 0/negativ sein, wird dann abgelehnt)
 * @param displayName   Sichtbarer Name (nur zur Anzeige, NIE fuer Entscheidungen)
 * @param priceCandidates alle Zahlen, die aus der Lore wie ein Preis aussehen (zur Mehrdeutigkeits-Pruefung)
 * @param traits        erkannte Merkmale (z.B. "enchant:minecraft:sharpness=5")
 */
public record ListingSnapshot(int slot, String registryId, int count, String displayName,
                              List<Long> priceCandidates, List<String> traits) {

    public ListingSnapshot {
        priceCandidates = priceCandidates == null ? List.of() : List.copyOf(priceCandidates);
        traits = traits == null ? List.of() : List.copyOf(traits);
    }

    /** Eindeutiger Preis: nur wenn GENAU ein Kandidat vorhanden ist. */
    public OptionalLong uniquePrice() {
        return priceCandidates.size() == 1 ? OptionalLong.of(priceCandidates.get(0)) : OptionalLong.empty();
    }

    /** Fingerabdruck zur Wiedererkennung vor dem Klick (Slot egal, Inhalt zaehlt). */
    public String fingerprint() {
        return registryId + "|" + count + "|" + priceCandidates + "|" + traits;
    }
}
