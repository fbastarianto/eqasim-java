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
 * In other words:
 * - motorcycle can be used as access to PT (e.g., home → stop),
 * - motorcycle can only be used as egress if the destination is home (stop → home),
 * - otherwise, the alternative is rejected.
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
            log.info("NoMotorcycleEgressExceptHome: no elements for candidate class=" + candidate.getClass().getName());
            return true;
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
            log.info("NoMotorcycleEgressExceptHome: no PT leg found in elements for candidate class=" + candidate.getClass().getName());
            return true;
        }

        // Access leg: the leg immediately before the first PT leg (if any)
        Leg accessLeg = null;
        for (int i = firstPtIdx - 1; i >= 0; i--) {
            if (elements.get(i) instanceof Leg) { accessLeg = (Leg) elements.get(i); break; }
        }

        // Egress leg: the leg immediately after the last PT leg (if any)
        Leg egressLeg = null;
        for (int i = lastPtIdx + 1; i < elements.size(); i++) {
            if (elements.get(i) instanceof Leg) { egressLeg = (Leg) elements.get(i); break; }
        }

        String originType = trip.getOriginActivity() != null ? trip.getOriginActivity().getType() : null;
        String destType   = trip.getDestinationActivity() != null ? trip.getDestinationActivity().getType() : null;

        // Diagnostic print -> logger
        log.info(String.format(
                "NoMotorcycleEgressExceptHome: candidateClass=%s elems=%d firstPt=%d lastPt=%d access=%s egress=%s origin=%s dest=%s",
                candidate.getClass().getName(),
                elements.size(),
                firstPtIdx,
                lastPtIdx,
                accessLeg != null ? accessLeg.getMode() : "null",
                egressLeg != null ? egressLeg.getMode() : "null",
                originType, destType
        ));

        // Diagnostic print
        System.out.println(String.format(
                "NoMotorcycleEgressExceptHome: candidateClass=%s elems=%d firstPt=%d lastPt=%d access=%s egress=%s origin=%s dest=%s",
                candidate.getClass().getName(),
                elements.size(),
                firstPtIdx,
                lastPtIdx,
                accessLeg != null ? accessLeg.getMode() : "null",
                egressLeg != null ? egressLeg.getMode() : "null",
                originType, destType
        ));

        // Motorcycle as access is only allowed if origin is home
        if (accessLeg != null && "motorcycle".equalsIgnoreCase(accessLeg.getMode())
                && (originType == null || !"home".equalsIgnoreCase(originType))) {
            return false;
        }

        // Motorcycle as egress is only allowed if destination is home
        if (egressLeg != null && "motorcycle".equalsIgnoreCase(egressLeg.getMode())
                && (destType == null || !"home".equalsIgnoreCase(destType))) {
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