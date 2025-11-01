package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.PtUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;         // core (for super)
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPersonPredictor;      // custom
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPtPredictor;          // custom
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

/**
 * Jakarta PT utility estimator with feeder-mode restrictions:
 * - Motorcycle access is allowed only if the trip's ORIGIN is "home".
 * - Motorcycle egress is allowed only if the trip's DESTINATION is "home".
 *
 * (We enforce this here because feeder legs are produced by SRR and are not
 *  governed by the tour constraints on main modes.)
 */
public class JakartaPTUtilityEstimator extends PtUtilityEstimator {
	private final JakartaModeParameters parameters;
	private final JakartaPersonPredictor predictor;      // custom person predictor
	private final JakartaPtPredictor jakartaPtPredictor; // custom PT predictor (adds ODT fares, etc.)

	@Inject
	public JakartaPTUtilityEstimator(
			JakartaModeParameters parameters,
			PtPredictor ptPredictor,              // core pt predictor passed to super
			JakartaPtPredictor jakartaPtPredictor,
			JakartaPersonPredictor predictor) {

		super(parameters, ptPredictor);
		this.jakartaPtPredictor = jakartaPtPredictor;
		this.parameters = parameters;
		this.predictor = predictor;

		// Optional: check what was bound
		System.out.println("PtPredictor bound to: " + ptPredictor.getClass().getName());
	}

	// ------- Helper: activity type "home" matcher (robust to variants like "home", "home_1", etc.)
	private static boolean isHomeType(String type) {
		if (type == null) return false;
		String t = type.toLowerCase();
		return t.equals("home") || t.startsWith("home");
	}

	// ------- Guard: forbid motorcycle ACCESS unless ORIGIN is "home"
	private static boolean hasIllegalMotorcycleAccess(DiscreteModeChoiceTrip trip,
													  List<? extends PlanElement> elements) {
		boolean originIsHome = isHomeType(trip.getOriginActivity().getType());

		for (int i = 0; i < elements.size(); i++) {
			PlanElement pe = elements.get(i);
			if (!(pe instanceof Leg)) continue;

			Leg leg = (Leg) pe;

			// Is this leg immediately BEFORE a PT leg? -> access feeder
			boolean beforePt = (i + 1 < elements.size()
					&& elements.get(i + 1) instanceof Leg
					&& TransportMode.pt.equals(((Leg) elements.get(i + 1)).getMode()));

			if (beforePt && TransportMode.motorcycle.equals(leg.getMode())) {
				return !originIsHome; // illegal if origin is not "home"
			}
		}
		return false;
	}

	// ------- Guard: forbid motorcycle EGRESS unless DESTINATION is "home"
	private static boolean hasIllegalMotorcycleEgress(DiscreteModeChoiceTrip trip,
													  List<? extends PlanElement> elements) {
		boolean destIsHome = isHomeType(trip.getDestinationActivity().getType());

		for (int i = 0; i < elements.size(); i++) {
			PlanElement pe = elements.get(i);
			if (!(pe instanceof Leg)) continue;

			Leg leg = (Leg) pe;

			// Is this leg immediately AFTER a PT leg? -> egress feeder
			boolean afterPt = (i - 1 >= 0
					&& elements.get(i - 1) instanceof Leg
					&& TransportMode.pt.equals(((Leg) elements.get(i - 1)).getMode()));

			if (afterPt && TransportMode.motorcycle.equals(leg.getMode())) {
				return !destIsHome; // illegal if destination is not "home"
			}
		}
		return false;
	}

	// (Age utility; keep it if we need it)
	protected double estimateAgeUtility(Person person) {
		Object ageAttr = person.getAttributes().getAttribute("age");
		if (ageAttr instanceof Integer) {
			return ((Integer) ageAttr <= 16) ? parameters.jPT.alpha_age : 0.0;
		}
		return 0.0;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		// ----- HARD FILTERS on motorcycle feeder usage -----
		if (hasIllegalMotorcycleAccess(trip, elements) || hasIllegalMotorcycleEgress(trip, elements)) {
			return Double.NEGATIVE_INFINITY; // reject this PT alternative outright
		}

		// Person & PT variables
		JakartaPersonVariables variables = predictor.predictVariables(person, trip, elements);
		PtVariables variables_pt = jakartaPtPredictor.predictVariables(person, trip, elements);

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateAccessEgressTimeUtility(variables_pt);
		utility += estimateInVehicleTimeUtility(variables_pt);
		utility += estimateWaitingTimeUtility(variables_pt);
		utility += estimateLineSwitchUtility(variables_pt);
//		utility += estimateRegionalUtility(variables);
		utility += estimateAgeUtility(person);
		utility += estimateMonetaryCostUtility(variables_pt) * EstimatorUtils.interaction(variables.hhlIncome,
				parameters.jAvgHHLIncome.avg_hhl_income, parameters.jIncomeElasticity.lambda_income);

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