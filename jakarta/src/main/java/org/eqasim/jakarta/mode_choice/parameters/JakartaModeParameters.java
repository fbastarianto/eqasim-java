package org.eqasim.jakarta.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;

public class JakartaModeParameters extends ModeParameters {
	public class JakartaWalkParameters {
		public double alpha_age = 0.0;
	}
	
	public class JakartaCarParameters {
		//public double alpha_car_city = 0.0;
	}
	
	public class JakartaPTParameters {
		// Existing parameter (keep for backward compatibility)
		//	public double alpha_pt_city = 0.0;
		public double alpha_age = 0.0;

		// New nested class for latent class parameters
		public static class LatentClassParameters {
			public double constant = 0.0;
			public double accessTime = 0.0;
			public double inVehicleTime = 0.0;
			public double egressTime = 0.0;
			public double cost = 0.0;
		}

		// Add four latent class parameter sets
		public LatentClassParameters class1 = new LatentClassParameters();
		public LatentClassParameters class2 = new LatentClassParameters();
		public LatentClassParameters class3 = new LatentClassParameters();
		public LatentClassParameters class4 = new LatentClassParameters();

		// Generic parameters for non-classified individuals
		public LatentClassParameters generic = new LatentClassParameters();

	}
	
	public class JakartaIncomeElasticity {
		public double lambda_income = 0.0;
	}
	
	public class JakartaAvgHHLIncome {
		public double avg_hhl_income = 0.0;
	}
	
	//public class JakartaTaxiParameters {
	//	public double alpha_taxi_city = 0.0;
	//	public double beta_TravelTime_u_min = 0.0;
	//	
	//	public double betaAccessEgressWalkTime_min = 0.0;
	//	public double betaWaitingTime_u_min = 0.0;
	//	public double alpha_u = 0.0;
	//	
	//	public JakartaTaxiParameters() {
	//		this.alpha_taxi_city = 0.0;
	//	}
	//	
	//}
	
	
	public class JakartaCarodtParameters {
	//	public double alpha_carodt_city = 0.0;
		public double beta_TravelTime_u_min = 0.0;
		
		public double betaAccessEgressWalkTime_min = 0.0;
		public double betaWaitingTime_u_min = 0.0;
		public double alpha_u = 0.0;
		public double alpha_sex = 0.0;
		public double alpha_age = 0.0;
		
	//	public JakartaCarodtParameters() {
	//		this.alpha_carodt_city = 0.0;
	//	}
		
	}
	
	
	public class JakartaMcodtParameters {
		//public double alpha_mcodt_city = 0.0;
		public double alpha_age = 0.0;
		public double beta_TravelTime_u_min = 0.0;
		
		public double betaAccessEgressWalkTime_min = 0.0;
		public double betaWaitingTime_u_min = 0.0;
		public double alpha_u = 0.0;
		public double alpha_sex = 0.0;
		
	//	public JakartaMcodtParameters() //{
			//this.alpha_mcodt_city = 0.0;
	//	}
		
	}
	
	
	public class JakartaMotorcycleParameters {
	//	public double alpha_motorcycle_city = 0.0;
		public double alpha_age = 0.0;
		public double beta_TravelTime_u_min = 0.0;
		
		public double betaAccessEgressWalkTime_min = 0.0;
		public double betaWaitingTime_u_min = 0.0;
		public double alpha_u = 0.0;
		
	//	public JakartaMotorcycleParameters() {
	//		this.alpha_motorcycle_city = 0.0;
	//	}
		
	}
	
	public final JakartaWalkParameters jWalk = new JakartaWalkParameters();
	public final JakartaPTParameters jPT = new JakartaPTParameters();
	public final JakartaCarParameters jCar = new JakartaCarParameters();
	public final JakartaIncomeElasticity jIncomeElasticity = new JakartaIncomeElasticity();
	public final JakartaAvgHHLIncome jAvgHHLIncome = new JakartaAvgHHLIncome();
	//public final JakartaTaxiParameters jTaxi = new JakartaTaxiParameters();
	public final JakartaCarodtParameters jCarodt = new JakartaCarodtParameters();
	public final JakartaMcodtParameters jMcodt = new JakartaMcodtParameters();
	public final JakartaMotorcycleParameters jMotorcycle = new JakartaMotorcycleParameters();

	public static JakartaModeParameters buildDefault() {
		JakartaModeParameters parameters = new JakartaModeParameters();

		// Cost
		parameters.betaCost_u_MU = -2.08/100;
		parameters.lambdaCostEuclideanDistance = -0.75;
		parameters.referenceEuclideanDistance_km = 7.67;
        parameters.jIncomeElasticity.lambda_income = -0.06;
        parameters.jAvgHHLIncome.avg_hhl_income = 5327;
        
		// Car
		parameters.car.alpha_u = -0.50;
		parameters.car.betaTravelTime_u_min = -1.24/100;

		parameters.car.additionalAccessEgressWalkTime_min = 0.0;
		parameters.car.constantParkingSearchPenalty_min = 0.0;
		//parameters.jCar.alpha_car_city = -0.1597;

		// PT // jPT or pt???
		parameters.pt.alpha_u = -3.50;
		parameters.pt.betaLineSwitch_u = 0.0;
		parameters.pt.betaInVehicleTime_u_min = -1.49/100;
		parameters.pt.betaWaitingTime_u_min = -1.49/100;
		parameters.pt.betaAccessEgressTime_u_min = -1.49/100;
		//parameters.jPT.alpha_pt_city = 0.0;
		//parameters.jPT.alpha_age = 0.0;

		// PT Latent Class Parameters
		// Class 1: Non-private motorised
		parameters.jPT.class1.accessTime = -0.013;
		parameters.jPT.class1.inVehicleTime = -0.023;
		parameters.jPT.class1.egressTime = -0.069;
		parameters.jPT.class1.cost = -0.006;

		// Class 2: Young cost-sensitive
		parameters.jPT.class2.accessTime = -0.461;
		parameters.jPT.class2.inVehicleTime = 0.156;  // Note positive value
		parameters.jPT.class2.egressTime = -0.450;
		parameters.jPT.class2.cost = -0.183;

		// Class 3: Affluent car-dependent
		parameters.jPT.class3.cost = -0.019;  // Only cost considered (other n.e.)

		// Class 4: Young time-sensitive
		parameters.jPT.class4.accessTime = -0.052;
		parameters.jPT.class4.inVehicleTime = -0.010;
		parameters.jPT.class4.egressTime = -0.056;
		parameters.jPT.class4.cost = 0.030;   // Note positive value
		
		// Bike
		parameters.bike.alpha_u = -4.44;
		parameters.bike.betaTravelTime_u_min = -9.05/100;
		//parameters.bike.betaAgeOver18_u_a = 0.0;

		// Walk
		parameters.walk.alpha_u = -2.50;
		parameters.walk.betaTravelTime_u_min = -0.52/100;
		parameters.jWalk.alpha_age = 1.03/100;		//parameters.jWalk.alpha_walk_city = 0.0;
		
		//Carodt
		//parameters.jCarodt.alpha_carodt_city = 0.0;
		
		parameters.jCarodt.beta_TravelTime_u_min = -6.26/100 ;
		//parameters.jCarodt.betaWaitingTime_u_min = 0.0 ;
		//parameters.jCarodt.betaAccessEgressWalkTime_min = 0.0;
		parameters.jCarodt.alpha_u = -1.23;
		parameters.jCarodt.alpha_sex = -0.42;
		parameters.jCarodt.alpha_age = -1.32/100;

		//Taxi
		//parameters.jTaxi.alpha_taxi_city = 0.0;		
		//parameters.jTaxi.beta_TravelTime_u_min = 0.0 ;
		//parameters.jTaxi.betaWaitingTime_u_min = 0.0 ;
		//parameters.jTaxi.betaAccessEgressWalkTime_min = 0.0;
		//parameters.jTaxi.alpha_u = 0.0;	
		
		//Mcodt
		//parameters.jMcodt.alpha_mcodt_city = 0.0;				
		parameters.jMcodt.beta_TravelTime_u_min = -6.26/100;
		//parameters.jMcodt.betaWaitingTime_u_min = 0.0 ;
		//parameters.jMcodt.betaAccessEgressWalkTime_min = 0.0;
		parameters.jMcodt.alpha_u = -1.15;
		parameters.jMcodt.alpha_sex = -0.42;
		parameters.jMcodt.alpha_age = -1.32/100;
		
		
		//Motorcycle
		//parameters.jMcodt.alpha_mcodt_city = 0.0;				
		parameters.jMotorcycle.beta_TravelTime_u_min = -3.32/100 ;
		//parameters.jMotorcycle.betaWaitingTime_u_min = 0.0 ;
		//parameters.jMotorcycle.betaAccessEgressWalkTime_min = 0.0;
		parameters.jMotorcycle.alpha_u = 0.0;
		parameters.jMotorcycle.alpha_age = -0.83/100;
		
		
		
		return parameters;
	}
}
