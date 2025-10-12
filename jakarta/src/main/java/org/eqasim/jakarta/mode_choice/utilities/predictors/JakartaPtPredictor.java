package org.eqasim.jakarta.mode_choice.utilities.predictors;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.predictors.CachedVariablePredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

/**
 * JakartaPtPredictor
 *
 * Purpose:
 *  - Keep eqasim's standard PT variables but make sure access/egress time
 *    also accounts for motorised feeders (motorcycle, mcodt, carodt),
 *    in addition to (non_network_)walk.
 *
 * How it works:
 *  - Use the delegate PtPredictor to compute baseline PtVariables.
 *  - Iterate over plan elements; when a leg is a feeder mode and sits
 *    immediately before or after a 'pt' leg, treat it as access/egress.
 *  - Sum those feeder leg travel times and add to the delegate's
 *    access/egress time.
 *
 * Notes:
 *  - This class does NOT split bus vs rail components. If we need that,
 *    we would need a custom variables class (e.g., LeedsPtVariables).
 *  - If we want to include monetary costs for ODT feeders (mcodt/carodt),
 *    we can extend this to add a fare component onto v.cost_MU.
 */

public class JakartaPtPredictor extends CachedVariablePredictor<PtVariables> {
    private final PtPredictor delegate;

    @Inject
    public JakartaPtPredictor(PtPredictor delegate, TransitSchedule schedule) {
        this.delegate = delegate;
    }

    @Override
    protected PtVariables predict(Person person, DiscreteModeChoiceTrip trip,
                                  List<? extends PlanElement> elements) {
        // 1) Filter to PT-only legs for the delegate
        List<PlanElement> filteredElements = new java.util.ArrayList<>();
        for (PlanElement pe : elements) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                if (TransportMode.pt.equals(leg.getMode())) {
                    filteredElements.add(leg);
                }
            }
        }

        // 2) Delegate computes PT core vars (no access/egress from feeders)
        PtVariables v = delegate.predictVariables(person, trip, filteredElements);

        // 3) Compute extra access/egress minutes from original elements
        double extraAccessEgress_min = 0.0;

        for (int i = 0; i < elements.size(); i++) {
            PlanElement pe = elements.get(i);
            if (!(pe instanceof Leg)) continue;

            Leg leg = (Leg) pe;
            String mode = leg.getMode();

            boolean isFeeder =
                    TransportMode.walk.equals(mode)
                            || "non_network_walk".equals(mode)
                            || "motorcycle".equals(mode)
                            || "mcodt".equals(mode)
                            || "carodt".equals(mode);

            if (!isFeeder) continue;

            boolean touchesPt = false;
            if (i + 1 < elements.size() && elements.get(i + 1) instanceof Leg) {
                if (TransportMode.pt.equals(((Leg) elements.get(i + 1)).getMode())) {
                    touchesPt = true; // access
                }
            }
            if (!touchesPt && i - 1 >= 0 && elements.get(i - 1) instanceof Leg) {
                if (TransportMode.pt.equals(((Leg) elements.get(i - 1)).getMode())) {
                    touchesPt = true; // egress
                }
            }

            if (touchesPt) {
                double tt_s = leg.getTravelTime().seconds();
                if (!Double.isNaN(tt_s) && tt_s > 0.0) {
                    extraAccessEgress_min += tt_s / 60.0;
                }
            }
        }

        // 4) Return combined variables (add our feeder minutes)
        return new PtVariables(
                v.inVehicleTime_min,
                v.waitingTime_min,
                v.accessEgressTime_min + extraAccessEgress_min, // delegate likely 0 here
                v.numberOfLineSwitches,
                v.cost_MU,
                v.euclideanDistance_km
        );
    }
}
