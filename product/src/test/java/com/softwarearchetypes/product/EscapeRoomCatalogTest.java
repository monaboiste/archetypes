package com.softwarearchetypes.product;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task points 1 & 4 - the "world of sales" (CatalogEntry, L06) and sales-context constraints
 * (L08).
 *
 * <p>Demonstrates the three-worlds split:
 * <ul>
 *   <li><b>Definition world</b> - {@code ProductType} captures what the product IS (VR required,
 *       difficulty, capacity). See the product-type constants in {@link EscapeRoomCatalog}.
 *   <li><b>Sales world</b> - {@code CatalogEntry} captures where/when/to-whom it is offered.
 *       This class.
 *   <li><b>Realisation world</b> - {@code ProductInstance} captures a concrete booking.
 * </ul>
 *
 * <p>Key constraint-placement rule (L08):
 * <ul>
 *   <li>Intrinsic rules (health, legal, physical) live on {@code ProductType.applicabilityConstraint}.
 *   <li>Sales-context rules (city, channel, operational readiness, day-of-week) live on
 *       {@code CatalogEntry.salesConstraint}.
 * </ul>
 */
class EscapeRoomCatalogTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    // -------------------------------------------------------------------------
    // City-based availability and VR operational check
    // -------------------------------------------------------------------------

    @Test
    void cyberpunk_is_available_in_warsaw_with_vr() {
        List<CatalogEntry> warsawCatalog = EscapeRoomCatalog.catalogEntriesFor("Warsaw");
        ApplicabilityContext ctx = ApplicabilityContext.of(Map.of(
                "city",           "Warsaw",
                "hasVrEquipment", "true"));

        boolean available = warsawCatalog.stream()
                .filter(e -> e.product().id().equals(EscapeRoomCatalog.ID_CYBERPUNK_2077))
                .anyMatch(e -> e.isAvailableFor(ctx, TODAY));

        assertThat(available).isTrue();
    }

    @Test
    void cyberpunk_not_available_in_warsaw_when_vr_equipment_is_down() {
        // VR equipment down for maintenance — operational state reported via context.
        // The product definition is unchanged; only the catalog entry becomes unavailable.
        List<CatalogEntry> warsawCatalog = EscapeRoomCatalog.catalogEntriesFor("Warsaw");
        ApplicabilityContext noVr = ApplicabilityContext.of(Map.of(
                "city",           "Warsaw",
                "hasVrEquipment", "false"));

        boolean available = warsawCatalog.stream()
                .filter(e -> e.product().id().equals(EscapeRoomCatalog.ID_CYBERPUNK_2077))
                .anyMatch(e -> e.isAvailableFor(noVr, TODAY));

        assertThat(available).isFalse();
    }

    @Test
    void cyberpunk_absent_from_krakow_and_wroclaw_catalogs() {
        // Cyberpunk 2077 simply has no entry in Krakow or Wroclaw - no entry = not offered.
        // The absence of an entry is richer than a negative constraint: the product definition
        // is unchanged; only the catalog (world of sales) knows which cities can offer it.
        List<CatalogEntry> krakowCatalog  = EscapeRoomCatalog.catalogEntriesFor("Krakow");
        List<CatalogEntry> wroclawCatalog = EscapeRoomCatalog.catalogEntriesFor("Wroclaw");

        assertThat(krakowCatalog)
                .isNotEmpty()
                .extracting(e -> e.product().id())
                .doesNotContain(EscapeRoomCatalog.ID_CYBERPUNK_2077);

        assertThat(wroclawCatalog)
                .isNotEmpty()
                .extracting(e -> e.product().id())
                .doesNotContain(EscapeRoomCatalog.ID_CYBERPUNK_2077);
    }

    @Test
    void regular_rooms_available_in_all_three_cities() {
        for (String city : List.of("Warsaw", "Krakow", "Wroclaw")) {
            List<CatalogEntry> catalog = EscapeRoomCatalog.catalogEntriesFor(city);
            assertThat(catalog)
                    .as("City: " + city)
                    .extracting(e -> e.product().id())
                    .contains(
                            EscapeRoomCatalog.ID_MAD_SCIENTIST_LAB,
                            EscapeRoomCatalog.ID_ALCATRAZ,
                            EscapeRoomCatalog.ID_EGYPTIAN_TOMB);
        }
    }

    // -------------------------------------------------------------------------
    // Actor - weekend-only scheduling constraint on the catalog entry
    // -------------------------------------------------------------------------

    @Test
    void actor_available_on_weekends_via_catalog_entry() {
        // Weekend availability is a sales/scheduling constraint — it depends on when you book,
        // not on what the actor add-on IS. It lives on the CatalogEntry so a future
        // corporate-events catalog could offer actors on weekdays without changing the ProductType.
        List<CatalogEntry> warsawCatalog = EscapeRoomCatalog.catalogEntriesFor("Warsaw");
        ApplicabilityContext saturday = ApplicabilityContext.of(Map.of("dayType", "Saturday"));
        ApplicabilityContext sunday   = ApplicabilityContext.of(Map.of("dayType", "Sunday"));

        assertThat(warsawCatalog.stream()
                .filter(e -> e.product().id().equals(EscapeRoomCatalog.ID_ACTOR))
                .anyMatch(e -> e.isAvailableFor(saturday, TODAY))).isTrue();

        assertThat(warsawCatalog.stream()
                .filter(e -> e.product().id().equals(EscapeRoomCatalog.ID_ACTOR))
                .anyMatch(e -> e.isAvailableFor(sunday, TODAY))).isTrue();
    }

    @Test
    void actor_not_available_on_weekdays_via_catalog_entry() {
        List<CatalogEntry> warsawCatalog = EscapeRoomCatalog.catalogEntriesFor("Warsaw");
        ApplicabilityContext monday = ApplicabilityContext.of(Map.of("dayType", "Monday"));

        boolean available = warsawCatalog.stream()
                .filter(e -> e.product().id().equals(EscapeRoomCatalog.ID_ACTOR))
                .anyMatch(e -> e.isAvailableFor(monday, TODAY));

        assertThat(available).isFalse();
    }

    // -------------------------------------------------------------------------
    // Validity - temporal window
    // -------------------------------------------------------------------------

    @Test
    void expired_entry_is_not_available() {
        Validity lastYear = Validity.between(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));
        CatalogEntry expired = CatalogEntry.builder()
                .id(CatalogEntryId.generate())
                .product(EscapeRoomCatalog.MAD_SCIENTIST_LAB)
                .displayName("Mad Scientist's Laboratory")
                .description("Dismantle the professor's doomsday device before time runs out.")
                .categories(Set.of("room"))
                .validity(lastYear)
                .build();

        assertThat(expired.isAvailableAt(TODAY)).isFalse();
    }

    @Test
    void validity_from_after_to_is_rejected() {
        assertThatThrownBy(() -> Validity.between(
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
