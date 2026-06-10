package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.PtUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PersonPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;         // core (for super)
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPersonPredictor;      // custom
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

/**
 * Baseline Jakarta PT utility estimator.
 */

public class JakartaPTUtilityEstimator extends PtUtilityEstimator {
	private final JakartaModeParameters parameters;
	private final JakartaPersonPredictor predictor;      // custom person predictor
	private final PtPredictor ptPredictor;

	@Inject
	public JakartaPTUtilityEstimator(
			JakartaModeParameters parameters,
			PersonPredictor personPredictor,
			PtPredictor ptPredictor,              // core pt predictor passed to super
			JakartaPersonPredictor predictor) {

		super(parameters, ptPredictor);
		this.ptPredictor = ptPredictor;
		this.parameters = parameters;
		this.predictor = predictor;

	}

	// (Age utility; keep it if we need it)
	protected double estimateAgeUtility(Person person) {
		return (int) person.getAttributes().getAttribute("age") <= 16 ? parameters.jPT.alpha_age : 0.0;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		JakartaPersonVariables variables = predictor.predictVariables(person, trip, elements);
		PtVariables variables_pt = ptPredictor.predict(person, trip, elements);

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateAccessEgressTimeUtility(variables_pt);
		utility += estimateInVehicleTimeUtility(variables_pt);
		utility += estimateWaitingTimeUtility(variables_pt);
		utility += estimateLineSwitchUtility(variables_pt);
//		utility += estimateRegionalUtility(variables);

		// b_age_pt * AGE
		// utility += estimateAgeUtility(person); this one doesn't capture continuous age effect
		utility += parameters.jPT.alpha_age * variables.age;

		// Cost with income elasticity
		utility += estimateMonetaryCostUtility(variables_pt) * EstimatorUtils.interaction(
				variables.hhlIncome,
				parameters.jAvgHHLIncome.avg_hhl_income,
				parameters.jIncomeElasticity.lambda_income);

		// b_fulltime_pt * (S_OC == 1)
		if (variables.employment == 1)
			utility += parameters.jPT.alpha_fulltime;

		// Income elasticity on cost (generic multiplier)
		//JakartaPersonVariables personVariables = new JakartaPersonVariables(person);
		//utility += parameters.jPT.generic.cost * variables_pt.cost_MU *
		//		EstimatorUtils.interaction(
		//				personVariables.hhlIncome,
		//				parameters.jAvgHHLIncome.avg_hhl_income,
		//				parameters.jIncomeElasticity.lambda_income
		//		);

		// Optionally: include age utility if you want it to apply to PT
		// utility += estimateAgeUtility(person);

		return utility;
	}
}