package org.eqasim.jakarta.mode_choice.constraints;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
 * This constraint forbids PT trips from using motorcycle as egress mode,
 * except when the destination activity type is "home".
 *
 * Allowed:
 * home --(motorcycle access)--> stop --(pt)--> stop --(walk/mcodt/carodt egress)--> non-home
 * non-home --(walk/mcodt/carodt access)--> stop --(pt)--> stop --(motorcycle egress)--> home
 *
 * Disallowed:
 * ... --(pt)--> stop --(motorcycle egress)--> non-home
 *
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
 * In other words:
 * - motorcycle can be used as access to PT (e.g., home → stop),
 * - motorcycle can only be used as egress if the destination is home (stop → home),
 * - otherwise, the alternative is rejected.
 * - the current implementation prevents an intervening walk leg from hiding
 *   motorcycle use from this constraint.
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

        // Candidate plan elements are: [access legs]*, PT legs, [egress legs]*
        // We only care about the first and last legs (if they exist).
        // Extract first and last *Leg* from plan elements
        // Extract routed plan elements from the concrete candidate type
        List<? extends PlanElement> elements = extractElements(candidate);
        if (elements.isEmpty()) {
            throw new IllegalStateException(
                    "NoMotorcycleEgressExceptHome: cannot inspect routed elements for PT candidate class="
                            + candidate.getClass().getName()
            );
        } // add logging and make extractElements more robust

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
            StringBuilder modes = new StringBuilder();

            for (PlanElement pe : elements) {
                if (pe instanceof Leg) {
                    if (modes.length() > 0) {
                        modes.append("-");
                    }
                    modes.append(((Leg) pe).getMode());
                }
            }

            log.warn(
                    "NoMotorcycleEgressExceptHome: PT-labelled candidate contains no actual PT leg; "
                            + "constraint not applicable. candidateClass="
                            + candidate.getClass().getName()
                            + " legModes=" + modes
            );

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

        boolean originIsHome =
                originType != null
                        && "home".equalsIgnoreCase(originType);

        boolean destinationIsHome =
                destType != null
                        && "home".equalsIgnoreCase(destType);

// Diagnostic logger
        log.debug(String.format(
                "NoMotorcycleEgressExceptHome: candidateClass=%s elems=%d firstPt=%d lastPt=%d "
                        + "motorcycleOnAccessSide=%s motorcycleOnEgressSide=%s origin=%s dest=%s",
                candidate.getClass().getName(),
                elements.size(),
                firstPtIdx,
                lastPtIdx,
                motorcycleOnAccessSide,
                motorcycleOnEgressSide,
                originType,
                destType
        ));

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

    /** Works across DMC snapshots: tries getPlanElements(), getElements(), getPlan(). */
    /** More robust element extraction: try named getters, then any List-returning method mentioning plan/element. */
    @SuppressWarnings("unchecked")
    private static List<? extends PlanElement> extractElements(TripCandidate candidate) {
        if (candidate instanceof RoutedTripCandidate) {
            String[] preferred = new String[]{"getPlanElements", "getElements", "getPlan"};
            for (String m : preferred) {
                try {
                    Method method = candidate.getClass().getMethod(m);
                    Object res = method.invoke(candidate);
                    if (res instanceof List<?>) return (List<? extends PlanElement>) res;
                } catch (NoSuchMethodException ignore) {
                } catch (Exception e) {
                    // If you want, log once:
                    // Logger.getLogger(NoMotorcycleEgressExceptHome.class.getName()).fine("Failed " + m + ": " + e.getMessage());
                }
            }
            // fallback: scan methods for a List-returning method whose name mentions "plan" or "element"
            for (Method method : candidate.getClass().getMethods()) {
                String n = method.getName().toLowerCase();
                if ((n.contains("plan") || n.contains("element") || n.contains("elements")) &&
                        List.class.isAssignableFrom(method.getReturnType()) &&
                        method.getParameterCount() == 0) {
                    try {
                        Object res = method.invoke(candidate);
                        if (res instanceof List<?>) return (List<? extends PlanElement>) res;
                    } catch (Exception ignore) {}
                }
            }
        }
        return Collections.emptyList();
    }

    public static class Factory implements TripConstraintFactory {
        @Override
        public TripConstraint createConstraint(Person person, List<DiscreteModeChoiceTrip> trips,
                                               Collection<String> availableModes) {
            log.info("NoMotorcycleEgressExceptHome.Factory: creating constraint for person=" + person.getId());
            return new NoMotorcycleEgressExceptHome();
        }
    }
}