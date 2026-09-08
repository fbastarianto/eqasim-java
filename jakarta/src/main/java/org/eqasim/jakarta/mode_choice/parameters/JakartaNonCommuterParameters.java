package org.eqasim.jakarta.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.ParameterDefinition;

/** Startup-loaded generic non-commuter specification; no latent-class or policy fields. */
public final class JakartaNonCommuterParameters implements ParameterDefinition {
    public double betaCost_u_MU = -0.0208;
    public double lambdaCostEuclideanDistance = -0.75;
    public double referenceEuclideanDistance_km = 7.67;
    public static class Income { public double lambda_income = -0.06; }
    public static class AverageIncome { public double avg_hhl_income = 5327; }
    public final Income jIncomeElasticity = new Income();
    public final AverageIncome jAvgHHLIncome = new AverageIncome();
    public static class Car {
        public double alpha_u = -0.514;
        public double betaTravelTime_u_min = -0.04;
        public double additionalAccessEgressWalkTime_min = 0;
        public double constantParkingSearchPenalty_min = 0;
    }
    public static class Walk { public double alpha_u = -4.5; public double betaTravelTime_u_min = -0.007; }
    public static class WalkAge { public double alpha_age = 0.0103; }
    public static class Motorcycle {
        public double alpha_u = 0;
        public double beta_TravelTime_u_min = -0.0948;
        public double alpha_age = -0.0083;
    }
    public static class Mcodt {
        public double alpha_u = -2.5;
        public double beta_TravelTime_u_min = -0.1652;
        public double alpha_age = -0.0132;
        public double alpha_male = -1.15;
    }
    public static class Carodt {
        public double alpha_u = -5;
        public double beta_TravelTime_u_min = -0.0826;
        public double alpha_age = -0.0132;
        public double alpha_male = -0.42;
    }
    public static class Pt {
        public double betaInVehicleTime_u_min = -0.005;
        public double betaWaitingTime_u_min = -0.05;
        public double betaAccessEgressTime_u_min = -0.05;
    }
    public static class Generic { public double constant = -3.5; }
    public static class JakartaPt { public final Generic generic = new Generic(); }
    public final Car car = new Car();
    public final Walk walk = new Walk();
    public final WalkAge jWalk = new WalkAge();
    public final Motorcycle jMotorcycle = new Motorcycle();
    public final Mcodt jMcodt = new Mcodt();
    public final Carodt jCarodt = new Carodt();
    public final Pt pt = new Pt();
    public final JakartaPt jPT = new JakartaPt();
}
