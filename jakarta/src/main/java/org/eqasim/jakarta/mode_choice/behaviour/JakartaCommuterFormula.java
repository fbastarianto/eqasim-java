package org.eqasim.jakarta.mode_choice.behaviour;

import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;

/** Current 2.8.0 equations. Parameters are loaded once and never switched per person. */
public final class JakartaCommuterFormula {
    private final JakartaModeParameters p;
    public JakartaCommuterFormula(JakartaModeParameters parameters) { p = parameters; }
    public double evaluate(String mode, JakartaPersonData a, JakartaTripFeatures v) {
        double d = EstimatorUtils.interaction(v.distance(), p.referenceEuclideanDistance_km, p.lambdaCostEuclideanDistance);
        double h = EstimatorUtils.interaction(a.income(), p.jAvgHHLIncome.avg_hhl_income, p.jIncomeElasticity.lambda_income);
        double c = p.betaCost_u_MU * v.cost();
        return switch (mode) {
            case "car" -> p.car.alpha_u + p.car.betaTravelTime_u_min * (v.time() + p.car.constantParkingSearchPenalty_min)
                + c*d*h + p.jCar.betaTravelDistance_km*v.distance() + p.jCar.alpha_age*a.age();
            case "pt" -> p.pt.alpha_u + p.pt.betaAccessEgressTime_u_min*v.access()
                + p.pt.betaInVehicleTime_u_min*v.time() + c*d*h + p.jPT.alpha_age*a.age()
                + (a.fullTime() ? p.jPT.alpha_fulltime : 0);
            case "walk" -> p.walk.alpha_u + p.walk.betaTravelTime_u_min*v.time()
                + p.jWalk.betaTravelDistance_km*v.distance() + (v.distance()*1000 > 3747 ? -1500 : 0);
            case "motorcycle" -> p.jMotorcycle.alpha_u + p.jMotorcycle.beta_TravelTime_u_min*v.time()
                + c*h + p.jMotorcycle.betaShortDistance_km*v.distance();
            case "mcodt" -> p.jMcodt.alpha_u + p.jMcodt.beta_TravelTime_u_min*v.time()
                + p.jMcodt.alpha_age*a.age() + ("f".equals(a.sex()) ? p.jMcodt.alpha_female : 0)
                + c*d*h + p.jMcodt.betaShortDistance_km*v.distance();
            case "carodt" -> p.jCarodt.alpha_u + p.jCarodt.beta_TravelTime_u_min*v.time()
                + p.jCarodt.alpha_age*a.age() + ("f".equals(a.sex()) ? p.jCarodt.alpha_female : 0) + c*h;
            default -> throw new IllegalArgumentException("Unsupported Jakarta behavioural mode: " + mode);
        };
    }
}
