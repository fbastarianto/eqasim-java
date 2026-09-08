package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;
import com.google.inject.Inject;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.jakarta.mode_choice.behaviour.*;
import org.eqasim.jakarta.routing.HomeSideTripAttributes;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

/** Existing mode alias delegates through the strict subpopulation selector. */
public class JakartaPTUtilityEstimator implements UtilityEstimator {
    private final JakartaBehaviour behaviour;
    @Inject
    public JakartaPTUtilityEstimator(JakartaBehaviour behaviour) { this.behaviour = behaviour; }
	// Defensive DMC check only. The authoritative restriction is at the
	// RaptorStopFinder boundary so startup/general routing is covered as well.
	private static boolean hasIllegalMotorcycleAccess(DiscreteModeChoiceTrip trip,
			List<? extends PlanElement> elements) {
		boolean originIsHome = HomeSideTripAttributes.isHome(trip.getOriginActivity().getType());

		for (int i = 0; i < elements.size(); i++) {
			PlanElement pe = elements.get(i);
			if (!(pe instanceof Leg)) continue;

			Leg leg = (Leg) pe;

			// Is this leg immediately BEFORE a PT leg? -> access feeder
			boolean beforePt = (i + 1 < elements.size()
					&& elements.get(i + 1) instanceof Leg
					&& TransportMode.pt.equals(((Leg) elements.get(i + 1)).getMode()));

			if (beforePt && TransportMode.motorcycle.equals(leg.getMode())) {
				return !originIsHome; // illegal if origin is not "home"
			}
		}
		return false;
	}

	// ------- Guard: forbid motorcycle EGRESS unless DESTINATION is "home"
	private static boolean hasIllegalMotorcycleEgress(DiscreteModeChoiceTrip trip,
			List<? extends PlanElement> elements) {
		boolean destIsHome = HomeSideTripAttributes.isHome(trip.getDestinationActivity().getType());

		for (int i = 0; i < elements.size(); i++) {
			PlanElement pe = elements.get(i);
			if (!(pe instanceof Leg)) continue;

			Leg leg = (Leg) pe;

			// Is this leg immediately AFTER a PT leg? -> egress feeder
			boolean afterPt = (i - 1 >= 0
					&& elements.get(i - 1) instanceof Leg
					&& TransportMode.pt.equals(((Leg) elements.get(i - 1)).getMode()));

			if (afterPt && TransportMode.motorcycle.equals(leg.getMode())) {
				return !destIsHome; // illegal if destination is not "home"
			}
		}
		return false;
	}


    @Override
    public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        JakartaSubpopulation.select(person);
        JakartaPersonData.read(person, false);
        if (hasIllegalMotorcycleAccess(trip, elements) || hasIllegalMotorcycleEgress(trip, elements))
            return Double.NEGATIVE_INFINITY;
        return behaviour.estimate("pt", person, trip, elements);
    }
}
