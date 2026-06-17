package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.estimators.WalkUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PersonPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.WalkPredictor;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPersonPredictor;
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

//import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceTrip;

public class JakartaWalkUtilityEstimator extends WalkUtilityEstimator {
	
	private final JakartaModeParameters parameters;
	private final JakartaPersonPredictor predictor;

	@Inject
	public JakartaWalkUtilityEstimator(JakartaModeParameters parameters, PersonPredictor personPredictor,
			WalkPredictor walkPredictor, JakartaPersonPredictor predictor) {
		super(parameters, walkPredictor);

		this.parameters = parameters;
		this.predictor = predictor;
	}

//	protected double estimateRegionalUtility(JakartaPersonVariables variables) {
//		return (variables.cityTrip) ? parameters.spWalk.alpha_walk_city : 0.0;
//	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		//JakartaPersonVariables variables = predictor.predictVariables(person, trip, elements); // It is now unused unless I want to keep age/regional terms

		double utility = 0.0;
		double distance = CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(),
				trip.getDestinationActivity().getCoord());

		// Optional availability-style penalty for unrealistic walk distances
		if (distance > 3747) // maximum observed walking distance for a single trip is 3.747km
			utility += -1500;

		// asc_walk + b_tt_non_pt * tt_walk_single_min >> check org.eqasim.core.simulation.mode_choice.utilities.estimators.WalkUtilityEstimator
		utility += super.estimateUtility(person, trip, elements);

		// b_td_walk * (td_walk / 1000)
		utility += parameters.jWalk.betaTravelDistance_km * (distance / 1000.0);
		//utility += parameters.jWalk.alpha_age * variables.age;
		//utility += estimateRegionalUtility(variables);

		return utility;
	}

}
