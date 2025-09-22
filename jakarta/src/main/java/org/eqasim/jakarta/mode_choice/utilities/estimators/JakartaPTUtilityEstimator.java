package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.PtUtilityEstimator;
//import org.eqasim.core.components.pt.estimators.PtUtilityEstimator; // not used anymore
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PersonPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPersonPredictor;
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

//import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceTrip; // a package migration in MATSim now it's been moved into MATSim’s contribs namespace

public class JakartaPTUtilityEstimator  extends PtUtilityEstimator{
	private final JakartaModeParameters parameters;
	private final JakartaPersonPredictor predictor;  // NA in Deepseek
	private final PtPredictor ptPredictor; // NA in Deepseek

	@Inject
	public JakartaPTUtilityEstimator(JakartaModeParameters parameters, PersonPredictor personPredictor,
			PtPredictor ptPredictor, JakartaPersonPredictor predictor) { // Some estimators are NA in Deepseek
		super(parameters, ptPredictor);
		this.ptPredictor = ptPredictor; // NA in Deepseek
		this.parameters = parameters;
		this.predictor = predictor; // NA in Deepseek
	}

//	protected double estimateRegionalUtility(JakartaPersonVariables variables) {
//		return (variables.cityTrip) ? parameters.jPT.alpha_pt_city : 0.0;
//	}
	
	protected double estimateAgeUtility(Person person) {
		return (int) person.getAttributes().getAttribute("age") <= 16 ? parameters.jPT.alpha_age : 0.0; // NA in Deepseek -->> For what?
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) { // Some estimators are NA in Deepseek
		JakartaPersonVariables variables = predictor.predictVariables(person, trip, elements);
		PtVariables variables_pt = ptPredictor.predict(person, trip, elements);
		String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");

		double utility = 0.0;

		// Handle non-commuters (generic parameters)
		if (subpopulation == null || subpopulation.equals("non_commuters")) { //subpopulation == "non-commuters" ???
			utility += parameters.jPT.generic.constant;
			utility += estimateAccessEgressTimeUtility(variables_pt);
			utility += estimateInVehicleTimeUtility(variables_pt);
			utility += estimateWaitingTimeUtility(variables_pt);
			utility += estimateMonetaryCostUtility(variables_pt);
			return utility;
		}

		// Class-specific utility components
		switch (subpopulation) {
			case "Class1_non_private_motorised_commuters":
				utility += parameters.jPT.class1.constant;
				utility += parameters.jPT.class1.accessTime * variables_pt.accessEgressTime_min; // -0.013 // Why is accessTime becomes red?
				utility += parameters.jPT.class1.inVehicleTime * variables_pt.inVehicleTime_min; // -0.023
				utility += parameters.jPT.class1.egressTime * variables_pt.accessEgressTime_min; // -0.069
				utility += parameters.jPT.class1.cost * variables_pt.cost_MU; // -0.006
				break;

			case "Class2_young_cost_sensitive_commuters":
				utility += parameters.jPT.class2.constant;
				utility += parameters.jPT.class2.accessTime * variables_pt.accessEgressTime_min; // -0.461
				utility += parameters.jPT.class2.inVehicleTime * variables_pt.inVehicleTime_min; // +0.156
				utility += parameters.jPT.class2.egressTime * variables_pt.accessEgressTime_min; // -0.450
				utility += parameters.jPT.class2.cost * variables_pt.cost_MU; // -0.183
				break;

			case "Class3_affluent_car_dependent_commuters":
				utility += parameters.jPT.class3.constant;
				// Only cost considered (other components n.e.)
				utility += parameters.jPT.class3.cost * variables_pt.cost_MU; // -0.019
				break;

			case "Class4_young_time_sensitive_commuters":
				utility += parameters.jPT.class4.constant;
				utility += parameters.jPT.class4.accessTime * variables_pt.accessEgressTime_min; // -0.052
				utility += parameters.jPT.class4.inVehicleTime * variables_pt.inVehicleTime_min; // -0.010
				utility += parameters.jPT.class4.egressTime * variables_pt.accessEgressTime_min; // -0.056
				utility += parameters.jPT.class4.cost * variables_pt.cost_MU; // +0.030
				break;

			default:
				throw new IllegalArgumentException("Unknown subpopulation: " + subpopulation);
		}

		// Apply income elasticity to cost for all classes
		JakartaPersonVariables personVariables = new JakartaPersonVariables(person);
		utility += parameters.jPT.generic.cost * variables_pt.cost_MU *
				EstimatorUtils.interaction(personVariables.hhlIncome,
						parameters.jAvgHHLIncome.avg_hhl_income,
						parameters.jIncomeElasticity.lambda_income);

		return utility;
	}

}


