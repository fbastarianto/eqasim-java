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
 *  - Keep eqasim's standard PT variables but ensure that feeder legs
 *    (walk / non_network_walk / motorcycle / mcodt) that connect to PT
 *    are treated consistently.
 *  - Add a monetary ODT fare component for mcodt feeders into v.cost_MU,
 *    controlled via jPT.odt.* parameters in JakartaModeParameters.
 *
 * How it works:
 *  - Use the delegate PtPredictor to compute baseline PtVariables.
 *  - Iterate over plan elements; when a leg is a feeder mode and sits
 *    immediately before/after a "pt interaction" activity that connects
 *    to a PT leg, treat it as a PT feeder leg.
 *  - Sum mcodt feeder distances and compute an additional monetary cost.
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
        // 1) Delegate on PT-only legs (eqasim core behaviour)
        List<PlanElement> ptOnly = new ArrayList<>();
        for (PlanElement pe : elements) {
            if (pe instanceof Leg && TransportMode.pt.equals(((Leg) pe).getMode())) {
                ptOnly.add(pe);
            }
        }

        // DEBUG: prove that JakartaPtPredictor is being used
        System.out.println("PT PREDICTOR CALLED for person=" + person.getId());

        PtVariables v = delegate.predictVariables(person, trip, ptOnly);

        // 2) From original elements: feeder mins + mcodt feeder distances
        double extraAccessEgress_min = 0.0;
        double mcodt_km = 0.0; // double mcodtMin = 0.0,
        int mcodtLegs = 0; //int mcodtLegs = 0,

        for (int i = 0; i < elements.size(); i++) {
            PlanElement pe = elements.get(i);
            if (!(pe instanceof Leg)) {
                continue;
            }

            Leg leg = (Leg) pe;
            String mode = leg.getMode();

            // 1) Candidate feeder modes
            boolean isFeeder =
                    TransportMode.walk.equals(mode)
                            || "non_network_walk".equals(mode)
                            || TransportMode.motorcycle.equals(mode)
                            || "mcodt".equals(mode);
            if (!isFeeder) {
                continue;
            }

            // 2) Check if this leg touches a PT trip via "pt interaction" activities
            boolean touchesPt = false;

            // ACCESS: feeder leg -> pt interaction -> pt
            if (i + 1 < elements.size() && elements.get(i + 1) instanceof org.matsim.api.core.v01.population.Activity) {
                String typeNext = ((org.matsim.api.core.v01.population.Activity) elements.get(i + 1)).getType();
                if (typeNext != null && typeNext.startsWith("pt interaction")) {
                    touchesPt = true;
                }
            }

            // EGRESS: pt -> pt interaction -> feeder leg
            if (!touchesPt && i - 1 >= 0 && elements.get(i - 1) instanceof org.matsim.api.core.v01.population.Activity) {
                String typePrev = ((org.matsim.api.core.v01.population.Activity) elements.get(i - 1)).getType();
                if (typePrev != null && typePrev.startsWith("pt interaction")) {
                    touchesPt = true;
                }
            }

            if (!touchesPt) {
                continue; // not a PT feeder
            }

            // 3) From here we know: this leg is a feeder for PT

            double tt_s = leg.getTravelTime().seconds();
            if (Double.isNaN(tt_s) || tt_s <= 0.0) {
                continue;
            }

            double tt_min = tt_s / 60.0;

            // If you want feeder time to be added explicitly to PT access/egress time,
            // uncomment the following line:
            // extraAccessEgress_min += tt_min;

            // distance-based ODT fare for mcodt feeders
            if ("mcodt".equals(mode)) {

                double dist_m = 0.0;
                double tt_s_mcodt = 0.0;   // renamed to avoid clash

                if (leg.getRoute() != null) {
                    if (!Double.isNaN(leg.getRoute().getDistance())) {
                        dist_m = leg.getRoute().getDistance();
                    }
                    if (leg.getRoute().getTravelTime().isDefined()) {
                        tt_s_mcodt = leg.getRoute().getTravelTime().seconds();
                    }
                }

                System.out.println(
                        "DEBUG FEEDER MCODT | person=" + person.getId()
                                + " | mode=" + mode
                                + " | dist_m=" + dist_m
                                + " | tt_s=" + tt_s_mcodt
                                + " | touchesPt=" + touchesPt
                );

                if (touchesPt) {
                    mcodt_km += dist_m / 1000.0;
                    mcodtLegs++;
                }
            }

        }

        // DEBUG: prove that YAML has NO effect
        System.out.println("DEBUG per_km_mcodt=" + params.jPT.odt.per_km_mcodt);

        // 3) ODT fares (MU) from parameters
        double odtCostMU =
                params.jPT.odt.base_mcodt * mcodtLegs
                        + params.jPT.odt.per_km_mcodt * mcodt_km; //params.jPT.odt.per_min_mcodt * mcodtMin

        // 3b) PT voucher: reduce normal PT cost by a share of the feeder cost
        double subsidyShare = params.jPT.odt.subsidyShare_mcodt;   // e.g. 0.25 for 25%
        double discountMU = subsidyShare * odtCostMU;

        // Optional safety: avoid making cost wildly negative
        double maxDiscountMU = params.jPT.odt.maxDiscountMU_mcodt; // or something calibrated in the yml file

        // Apply cap
        if (discountMU > maxDiscountMU) {
            discountMU = maxDiscountMU;
        }

        // double newCostMU = v.cost_MU - discountMU;

        // DEBUG: prove if mcodt_km and odtCostMU are non-zero
        if (mcodt_km > 0) {
            System.out.println(
                    "DEBUG PT ODT COST | person=" + person.getId()
                            + " | mcodt_km=" + mcodt_km
                            + " | per_km_mcodt=" + params.jPT.odt.per_km_mcodt
                            + " | odtCostMU=" + odtCostMU
                            + " | baseCostPerLeg=" + params.jPT.odt.base_mcodt
            );
        }

        // 4) Return updated PtVariables
        return new PtVariables(
                v.inVehicleTime_min,
                v.waitingTime_min,
                v.accessEgressTime_min, //+ extraAccessEgress_min,
                v.numberOfLineSwitches,
                v.cost_MU - discountMU,
                v.euclideanDistance_km
        );
    }
}
