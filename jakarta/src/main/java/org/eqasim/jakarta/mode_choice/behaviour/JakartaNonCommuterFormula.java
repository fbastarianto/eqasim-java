package org.eqasim.jakarta.mode_choice.behaviour;

import org.eqasim.jakarta.mode_choice.parameters.JakartaNonCommuterParameters;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;

/** Approved generic legacy equations evaluated against current shared physical features/costs. */
public final class JakartaNonCommuterFormula {
    private final JakartaNonCommuterParameters p;
    public JakartaNonCommuterFormula(JakartaNonCommuterParameters parameters) { p = parameters; }
    public double evaluate(String mode, JakartaPersonData a, JakartaTripFeatures v) {
        double d = EstimatorUtils.interaction(v.distance(), p.referenceEuclideanDistance_km, p.lambdaCostEuclideanDistance);
        double h = EstimatorUtils.interaction(a.income(), p.jAvgHHLIncome.avg_hhl_income, p.jIncomeElasticity.lambda_income);
        double c = p.betaCost_u_MU * v.cost();
        return switch (mode) {
            case "car" -> p.car.alpha_u + p.car.betaTravelTime_u_min*(v.time() + p.car.constantParkingSearchPenalty_min)
                + p.walk.betaTravelTime_u_min*(v.access() + p.car.additionalAccessEgressWalkTime_min) + c*d*h;
            case "walk" -> p.walk.alpha_u + p.walk.betaTravelTime_u_min*v.time() + p.jWalk.alpha_age*a.age();
            case "motorcycle" -> p.jMotorcycle.alpha_u + p.jMotorcycle.beta_TravelTime_u_min*v.time()
                + p.walk.betaTravelTime_u_min*(v.access() + p.car.additionalAccessEgressWalkTime_min)
                + p.jMotorcycle.alpha_age*a.age() + c*d*h;
            case "mcodt" -> p.jMcodt.alpha_u + p.jMcodt.beta_TravelTime_u_min*v.time()
                + p.jMcodt.alpha_age*a.age() + ("m".equals(a.sex()) ? p.jMcodt.alpha_male : 0) + c*d*h;
            case "carodt" -> p.jCarodt.alpha_u + p.jCarodt.beta_TravelTime_u_min*v.time()
                + p.jCarodt.alpha_age*a.age() + ("m".equals(a.sex()) ? p.jCarodt.alpha_male : 0) + c*d*h;
            case "pt" -> p.jPT.generic.constant + p.pt.betaAccessEgressTime_u_min*v.access()
                + p.pt.betaInVehicleTime_u_min*v.time() + p.pt.betaWaitingTime_u_min*v.waiting() + c*d;
            default -> throw new IllegalArgumentException("Unsupported Jakarta behavioural mode: " + mode);
        };
    }
}
