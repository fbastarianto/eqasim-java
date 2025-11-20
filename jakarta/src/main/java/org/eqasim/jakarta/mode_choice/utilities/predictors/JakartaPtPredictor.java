package org.eqasim.jakarta.mode_choice.utilities.predictors;

import java.util.ArrayList;
import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.predictors.CachedVariablePredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.Inject;

/**
 * JakartaPtPredictor
 *
 * Purpose:
 *  - Keep eqasim's standard PT variables but make sure access/egress time
 *    also accounts for motorised feeders (motorcycle, mcodt),
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
 *  - If we want to include monetary costs for ODT feeders (mcodt),
 *    we can extend this to add a fare component onto v.cost_MU.
 */

public class JakartaPtPredictor extends CachedVariablePredictor<PtVariables> {
    private final PtPredictor delegate;
    private final JakartaModeParameters params;

    @Inject
    public JakartaPtPredictor(PtPredictor delegate, TransitSchedule schedule, JakartaModeParameters params) {
        this.delegate = delegate;
        this.params = params;
    }

    @Override
    protected PtVariables predict(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        // 1) Delegate on PT-only legs
        List<PlanElement> ptOnly = new ArrayList<>();
        for (PlanElement pe : elements) {
            if (pe instanceof Leg && TransportMode.pt.equals(((Leg) pe).getMode())) {
                ptOnly.add(pe);
            }
        }
        PtVariables v = delegate.predictVariables(person, trip, ptOnly);

        // 2) From original elements: feeder mins and ODT mins/counts
        double extraAccessEgress_min = 0.0;
        double mcodt_km = 0.0; // double mcodtMin = 0.0,
        int mcodtLegs = 0; //int mcodtLegs = 0,

        for (int i = 0; i < elements.size(); i++) {
            PlanElement pe = elements.get(i);
            if (!(pe instanceof Leg)) continue;
            Leg leg = (Leg) pe;
            String mode = leg.getMode();

            boolean isFeeder =
                    TransportMode.walk.equals(mode)
                            || "non_network_walk".equals(mode)
                            || "motorcycle".equals(mode)
                            || "mcodt".equals(mode);
            if (!isFeeder) continue;

            boolean touchesPt = false;
            if (i + 1 < elements.size() && elements.get(i + 1) instanceof Leg) {
                if (TransportMode.pt.equals(((Leg) elements.get(i + 1)).getMode())) touchesPt = true;
            }
            if (!touchesPt && i - 1 >= 0 && elements.get(i - 1) instanceof Leg) {
                if (TransportMode.pt.equals(((Leg) elements.get(i - 1)).getMode())) touchesPt = true;
            }
            if (!touchesPt) continue;

            double tt_s = leg.getTravelTime().seconds();
            if (Double.isNaN(tt_s) || tt_s <= 0) continue;
            double tt_min = tt_s / 60.0;

            //extraAccessEgress_min += tt_min;

            // distance-based ODT fare
            if ("mcodt".equals(mode)) {
                double dist_m = 0.0;
                if (leg.getRoute() != null && !Double.isNaN(leg.getRoute().getDistance()))
                    dist_m = leg.getRoute().getDistance();

                mcodt_km += dist_m / 1000.0;
                mcodtLegs++;
            }
        }

        // 3) ODT fares (MU) from parameters
        double odtCostMU =
                params.jPT.odt.base_mcodt * mcodtLegs
                        + params.jPT.odt.per_km_mcodt * mcodt_km; //params.jPT.odt.per_min_mcodt * mcodtMin

        // 4) Return updated PtVariables
        return new PtVariables(
                v.inVehicleTime_min,
                v.waitingTime_min,
                v.accessEgressTime_min + extraAccessEgress_min,
                v.numberOfLineSwitches,
                v.cost_MU + odtCostMU,
                v.euclideanDistance_km
        );
    }
}
