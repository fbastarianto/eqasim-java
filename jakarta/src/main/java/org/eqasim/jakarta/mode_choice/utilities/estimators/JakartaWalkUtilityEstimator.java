package org.eqasim.jakarta.mode_choice.utilities.estimators;

import java.util.List;
import com.google.inject.Inject;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.jakarta.mode_choice.behaviour.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

/** Existing mode alias delegates through the strict subpopulation selector. */
public class JakartaWalkUtilityEstimator implements UtilityEstimator {
    private final JakartaBehaviour behaviour;
    @Inject
    public JakartaWalkUtilityEstimator(JakartaBehaviour behaviour) { this.behaviour = behaviour; }

    @Override
    public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        return behaviour.estimate("walk", person, trip, elements);
    }
}
