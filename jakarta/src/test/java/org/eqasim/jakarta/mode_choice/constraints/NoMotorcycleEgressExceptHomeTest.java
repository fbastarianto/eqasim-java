package org.eqasim.jakarta.mode_choice.constraints;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.DefaultRoutedTripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.DefaultTripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.core.population.PopulationUtils;

class NoMotorcycleEgressExceptHomeTest {
    private final NoMotorcycleEgressExceptHome constraint = new NoMotorcycleEgressExceptHome();

    @Test
    void enforcesMotorcycleOnEveryPtSideLeg() {
        assertAllowed("home", "work", "motorcycle", "walk", "pt", "mcodt");
        assertForbidden("work", "home", "motorcycle", "walk", "pt", "mcodt");
        assertForbidden("home", "work", "mcodt", "pt", "walk", "motorcycle");
        assertAllowed("work", "home", "mcodt", "pt", "walk", "motorcycle");
        assertForbidden("home", "work", "motorcycle", "pt", "motorcycle");
        assertForbidden("work", "home", "motorcycle", "pt", "motorcycle");
        assertAllowed("work", "education", "walk", "pt", "mcodt");
    }

    @Test
    void handlesPtLabelledWalkOnlyCandidateExplicitlyAsNotRealisedPt() {
        assertAllowed("work", "education", "walk", "walk");
    }

    @Test
    void failsLoudForUninspectablePtCandidate() {
        DiscreteModeChoiceTrip trip = trip("home", "work");
        TripCandidate nonRouted = new DefaultTripCandidate(0.0, TransportMode.pt, 0.0);
        DefaultRoutedTripCandidate empty = new DefaultRoutedTripCandidate(
                0.0, TransportMode.pt, List.of(), 0.0);

        assertThrows(
                IllegalStateException.class,
                () -> constraint.validateAfterEstimation(trip, nonRouted, List.of()));
        assertThrows(
                IllegalStateException.class,
                () -> constraint.validateAfterEstimation(trip, empty, List.of()));
    }

    private void assertAllowed(String originType, String destinationType, String... modes) {
        assertTrue(validate(originType, destinationType, modes));
    }

    private void assertForbidden(String originType, String destinationType, String... modes) {
        assertFalse(validate(originType, destinationType, modes));
    }

    private boolean validate(String originType, String destinationType, String... modes) {
        DefaultRoutedTripCandidate candidate = new DefaultRoutedTripCandidate(
                0.0,
                TransportMode.pt,
                elements(modes),
                0.0);
        return constraint.validateAfterEstimation(
                trip(originType, destinationType), candidate, List.of());
    }

    private static DiscreteModeChoiceTrip trip(String originType, String destinationType) {
        Activity origin = PopulationUtils.createActivityFromCoord(originType, null);
        Activity destination = PopulationUtils.createActivityFromCoord(destinationType, null);
        return new DiscreteModeChoiceTrip(
                origin,
                destination,
                TransportMode.pt,
                List.of(),
                1,
                2,
                0,
                origin.getAttributes());
    }

    private static List<PlanElement> elements(String... modes) {
        List<PlanElement> elements = new ArrayList<>();
        for (String mode : modes) {
            elements.add(PopulationUtils.createLeg(mode));
        }
        return elements;
    }
}
