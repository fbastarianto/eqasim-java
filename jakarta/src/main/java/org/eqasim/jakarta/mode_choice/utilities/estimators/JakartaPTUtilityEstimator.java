package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.PtUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;         // core (for super)
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPersonPredictor;      // your custom
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPtPredictor;          // your custom
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

public class JakartaPTUtilityEstimator extends PtUtilityEstimator {
	private final JakartaModeParameters parameters;
	private final JakartaPersonPredictor predictor;           // your custom person predictor used below
	private final JakartaPtPredictor jakartaPtPredictor;      // your custom PT predictor

	@Inject
	public JakartaPTUtilityEstimator(
			JakartaModeParameters parameters,
			PtPredictor ptPredictor,           // core pt predictor passed to super
			JakartaPtPredictor jakartaPtPredictor,
			JakartaPersonPredictor predictor) {

		super(parameters, ptPredictor);
		this.jakartaPtPredictor = jakartaPtPredictor;
		this.parameters = parameters;
		this.predictor = predictor;

		// Optional sanity check:
		System.out.println("PtPredictor bound to: " + ptPredictor.getClass().getName());
	}

	protected double estimateAgeUtility(Person person) {
		Object ageAttr = person.getAttributes().getAttribute("age");
		if (ageAttr instanceof Integer) {
			return ((Integer) ageAttr <= 16) ? parameters.jPT.alpha_age : 0.0;
		}
		return 0.0;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		JakartaPersonVariables variables = predictor.predictVariables(person, trip, elements);
		PtVariables variables_pt = jakartaPtPredictor.predictVariables(person, trip, elements); // <- use custom predictor
		String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");

		double utility = 0.0;

		// Non-commuters (generic)
		if (subpopulation == null || subpopulation.equals("non_commuters")) {
			utility += parameters.jPT.generic.constant;
			utility += estimateAccessEgressTimeUtility(variables_pt);
			utility += estimateInVehicleTimeUtility(variables_pt);
			utility += estimateWaitingTimeUtility(variables_pt);
			utility += estimateMonetaryCostUtility(variables_pt);
			return utility;
		}

		// Class-specific utility
		switch (subpopulation) {
			case "Class1_non_private_motorised_commuters":
				utility += parameters.jPT.class1.constant;
				utility += parameters.jPT.class1.accessTime * variables_pt.accessEgressTime_min;
				utility += parameters.jPT.class1.inVehicleTime * variables_pt.inVehicleTime_min;
				utility += parameters.jPT.class1.egressTime * variables_pt.accessEgressTime_min;
				utility += parameters.jPT.class1.cost * variables_pt.cost_MU;
				break;

			case "Class2_young_cost_sensitive_commuters":
				utility += parameters.jPT.class2.constant;
				utility += parameters.jPT.class2.accessTime * variables_pt.accessEgressTime_min;
				utility += parameters.jPT.class2.inVehicleTime * variables_pt.inVehicleTime_min;
				utility += parameters.jPT.class2.egressTime * variables_pt.accessEgressTime_min;
				utility += parameters.jPT.class2.cost * variables_pt.cost_MU;
				break;

			case "Class3_affluent_car_dependent_commuters":
				utility += parameters.jPT.class3.constant;
				utility += parameters.jPT.class3.cost * variables_pt.cost_MU; // others n.e.
				break;

			case "Class4_young_time_sensitive_commuters":
				utility += parameters.jPT.class4.constant;
				utility += parameters.jPT.class4.accessTime * variables_pt.accessEgressTime_min;
				utility += parameters.jPT.class4.inVehicleTime * variables_pt.inVehicleTime_min;
				utility += parameters.jPT.class4.egressTime * variables_pt.accessEgressTime_min;
				utility += parameters.jPT.class4.cost * variables_pt.cost_MU;
				break;

			default:
				throw new IllegalArgumentException("Unknown subpopulation: " + subpopulation);
		}

		// Income elasticity on cost
		JakartaPersonVariables personVariables = new JakartaPersonVariables(person);
		utility += parameters.jPT.generic.cost * variables_pt.cost_MU *
				EstimatorUtils.interaction(
						personVariables.hhlIncome,
						parameters.jAvgHHLIncome.avg_hhl_income,
						parameters.jIncomeElasticity.lambda_income
				);

		// (Optionally add age utility here if you intend it to apply)
		// utility += estimateAgeUtility(person);

		return utility;
	}
}
