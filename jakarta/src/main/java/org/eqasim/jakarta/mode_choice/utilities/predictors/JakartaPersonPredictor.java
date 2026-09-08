package org.eqasim.jakarta.mode_choice.utilities.predictors;

import java.util.List;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CachedVariablePredictor;
import org.eqasim.jakarta.mode_choice.behaviour.JakartaPersonData;
import org.eqasim.jakarta.mode_choice.behaviour.JakartaSubpopulation;
import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

/** Validated and uncached, including calls made by the shared PT cost model. */
public class JakartaPersonPredictor extends CachedVariablePredictor<JakartaPersonVariables> {
    @Override
    public JakartaPersonVariables predictVariables(Person person, DiscreteModeChoiceTrip trip,
            List<? extends PlanElement> elements) { return predict(person, trip, elements); }
    @Override
    protected JakartaPersonVariables predict(Person person, DiscreteModeChoiceTrip trip,
            List<? extends PlanElement> elements) {
        JakartaSubpopulation.select(person);
        JakartaPersonData data = JakartaPersonData.read(person, false);
        return new JakartaPersonVariables(data.income(), (int) data.age(), data.sex(),
            JakartaPredictorUtils.vehicleOwnership(person), data.fullTime() ? 1 : 0);
    }
}
