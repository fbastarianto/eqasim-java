package org.eqasim.jakarta.mode_choice.constraints;

import java.util.Collection;
import java.util.List;

import org.eqasim.jakarta.routing.HomeSideTripAttributes;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

// TRIP-BASED API:
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraint;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.RoutedTripCandidate;

/**
 * Defensive DMC invariant for the PT private-motorcycle home-side rule.
 * Motorcycle is allowed anywhere on PT access only from home, and anywhere
 * on PT egress only toward home.
 *
 * Allowed:
 * home --(motorcycle access)--> stop --(pt)--> stop --(walk/mcodt/carodt egress)--> non-home
 * non-home --(walk/mcodt/carodt access)--> stop --(pt)--> stop --(motorcycle egress)--> home
 *
 * Disallowed examples:
 * non-home
 *   -> motorcycle
 *   -> walk
 *   -> pt
 *   -> ...
 *
 * ...
 *   -> pt
 *   -> walk
 *   -> motorcycle
 *   -> non-home
 *
 * The routing-level restriction is authoritative; this constraint preserves
 * the same invariant defensively for DMC candidates. Intervening feeder legs
 * cannot hide motorcycle use.
 *
 * @author faza
 */

public class NoMotorcycleEgressExceptHome implements TripConstraint {
    private static final Logger log = LogManager.getLogger(NoMotorcycleEgressExceptHome.class);

    @Override
    public boolean validateBeforeEstimation(DiscreteModeChoiceTrip trip, String mode, List<String> previousModes) {
        // We only know access/egress after routing; decide in validateAfterEstimation.
        return true;
    }

    @Override
    public boolean validateAfterEstimation(
            DiscreteModeChoiceTrip trip,
            TripCandidate candidate,
            List<TripCandidate> previousCandidates
    ) {
        // If it’s not a PT candidate, nothing to do.
        if (!"pt".equalsIgnoreCase(candidate.getMode())) return true;

        // Candidate plan elements are: [access elements]*, PT elements, [egress elements]*.
        if (!(candidate instanceof RoutedTripCandidate routedCandidate)) {
            throw new IllegalStateException(
                    "NoMotorcycleEgressExceptHome: PT candidate is not a RoutedTripCandidate; class="
                            + candidate.getClass().getName()
            );
        }

        List<? extends PlanElement> elements = routedCandidate.getRoutedPlanElements();
        if (elements == null || elements.isEmpty()) {
            throw new IllegalStateException(
                    "NoMotorcycleEgressExceptHome: cannot inspect routed elements for PT candidate class="
                            + candidate.getClass().getName()
            );
        }

        // Find first and last indices of PT legs in the elements list
        int firstPtIdx = -1, lastPtIdx = -1;
        for (int i = 0; i < elements.size(); i++) {
            PlanElement pe = elements.get(i);
            if (pe instanceof Leg) {
                String lm = ((Leg) pe).getMode();
                if ("pt".equalsIgnoreCase(lm)) {
                    if (firstPtIdx == -1) firstPtIdx = i;
                    lastPtIdx = i;
                }
            }
        }
        // If there is no PT leg, nothing to constrain here
        if (firstPtIdx == -1) {
            return true;
        }

        // Check whether private motorcycle appears ANYWHERE on the PT access side
// (i.e. anywhere before the first actual PT leg).
        boolean motorcycleOnAccessSide = false;

        for (int i = 0; i < firstPtIdx; i++) {
            PlanElement pe = elements.get(i);

            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;

                if ("motorcycle".equalsIgnoreCase(leg.getMode())) {
                    motorcycleOnAccessSide = true;
                    break;
                }
            }
        }

// Check whether private motorcycle appears ANYWHERE on the PT egress side
// (i.e. anywhere after the last actual PT leg).
        boolean motorcycleOnEgressSide = false;

        for (int i = lastPtIdx + 1; i < elements.size(); i++) {
            PlanElement pe = elements.get(i);

            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;

                if ("motorcycle".equalsIgnoreCase(leg.getMode())) {
                    motorcycleOnEgressSide = true;
                    break;
                }
            }
        }

        String originType =
                trip.getOriginActivity() != null
                        ? trip.getOriginActivity().getType()
                        : null;

        String destType =
                trip.getDestinationActivity() != null
                        ? trip.getDestinationActivity().getType()
                        : null;

        boolean originIsHome = HomeSideTripAttributes.isHome(originType);
        boolean destinationIsHome = HomeSideTripAttributes.isHome(destType);

        log.debug(
                "NoMotorcycleEgressExceptHome: candidateClass={} elems={} firstPt={} lastPt={} "
                        + "motorcycleOnAccessSide={} motorcycleOnEgressSide={} origin={} dest={}",
                candidate.getClass().getName(),
                elements.size(),
                firstPtIdx,
                lastPtIdx,
                motorcycleOnAccessSide,
                motorcycleOnEgressSide,
                originType,
                destType
        );

// Private motorcycle anywhere on the PT access side
// is only allowed if the substantive trip origin is home.
        if (motorcycleOnAccessSide && !originIsHome) {
            return false;
        }

// Private motorcycle anywhere on the PT egress side
// is only allowed if the substantive trip destination is home.
        if (motorcycleOnEgressSide && !destinationIsHome) {
            return false;
        }

        return true;
    }

    public static class Factory implements TripConstraintFactory {
        @Override
        public TripConstraint createConstraint(Person person, List<DiscreteModeChoiceTrip> trips,
                                               Collection<String> availableModes) {
            log.debug("NoMotorcycleEgressExceptHome.Factory: creating constraint for person={}", person.getId());
            return new NoMotorcycleEgressExceptHome();
        }
    }
}
