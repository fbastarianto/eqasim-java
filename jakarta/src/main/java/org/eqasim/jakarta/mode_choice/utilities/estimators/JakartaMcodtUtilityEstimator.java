package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PersonPredictor;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaMcodtPredictor;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPersonPredictor;
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.eqasim.jakarta.mode_choice.utilities.variables.McodtVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

//import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceTrip;


public class JakartaMcodtUtilityEstimator implements UtilityEstimator {
	private final JakartaModeParameters parameters;
	private final JakartaPersonPredictor predictor;
	private final JakartaMcodtPredictor mcodtPredictor;

	@Inject
	public JakartaMcodtUtilityEstimator(JakartaModeParameters parameters, PersonPredictor personPredictor,
			JakartaMcodtPredictor mcodtPredictor, JakartaPersonPredictor predictor) {
		this.mcodtPredictor = mcodtPredictor;
		this.parameters = parameters;
		this.predictor = predictor;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		JakartaPersonVariables variables = predictor.predictVariables(person, trip, elements);
		McodtVariables variables_mcodt = mcodtPredictor.predict(person, trip, elements);

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateTravelTimeUtility(variables_mcodt);

		// utility += estimateAccessEgressTimeUtility(variables_mcodt); // not used in R utility

		// b_age_taxi_rh * AGE
		utility += parameters.jMcodt.alpha_age * variables.age;

		// b_female_rh * (SEX == 2)
		if ("f".equals(variables.sex))
			utility += parameters.jMcodt.alpha_sex; // females receive the coefficient and males receive zero

		//if (variables.sex == "f")
		//	utility += 0.0;
		//else
		//	utility += parameters.jMcodt.alpha_sex	;
		//if (variables.hhlIncome == 0.0)
		//	utility += estimateMonetaryCostUtility(variables_mcodt)

		// b_tc_value * tc_mc_odt_single
		utility += estimateMonetaryCostUtility(variables_mcodt) * EstimatorUtils.interaction(
				variables.hhlIncome,
				parameters.jAvgHHLIncome.avg_hhl_income,
				parameters.jIncomeElasticity.lambda_income);

		// b_short_dist_mode * (td_mc_single / 1000)
		utility += parameters.jMcodt.betaShortDistance_km
				* variables_mcodt.euclideanDistance_km;

		//	* (parameters.jAvgHHLIncome.avg_hhl_income / 1.0);
		//else
		//	utility += estimateMonetaryCostUtility(variables_mcodt)
		//		* (parameters.jAvgHHLIncome.avg_hhl_income / variables.hhlIncome);

		return utility;
	}


	private double estimateTravelTimeUtility(McodtVariables variables_mcodt) {
		return parameters.jMcodt.beta_TravelTime_u_min * variables_mcodt.travelTime_min;
	}


	protected double estimateMonetaryCostUtility(McodtVariables variables_mcodt) {
		return parameters.betaCost_u_MU * EstimatorUtils.interaction(variables_mcodt.euclideanDistance_km, 
				parameters.referenceEuclideanDistance_km, parameters.lambdaCostEuclideanDistance) * variables_mcodt.cost_MU;
	}


	protected double estimateAccessEgressTimeUtility(McodtVariables variables_mcodt) {
		return parameters.jMcodt.betaAccessEgressWalkTime_min * variables_mcodt.accessEgressTime_min;
	}


	protected double estimateConstantUtility() {
		return parameters.jMcodt.alpha_u;
	}

	public JakartaMcodtPredictor getMcodtPredictor() {
		return mcodtPredictor;
	}

}