package org.eqasim.jakarta.mode_choice.behaviour;

import java.util.List;
import java.util.Map;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CarPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.WalkPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PredictorUtils;
import org.eqasim.jakarta.mode_choice.parameters.JakartaModeParameters;
import org.eqasim.jakarta.mode_choice.parameters.JakartaNonCommuterParameters;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPtPredictor;
import org.matsim.api.core.v01.population.*;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

/** One shared feature/cost layer, with explicit independent formula delegates. No candidate cache. */
@Singleton
public final class JakartaBehaviour {
    private final JakartaCommuterFormula commuter;
    private final JakartaNonCommuterFormula nonCommuter;
    private final CarPredictor car;
    private final WalkPredictor walk = new WalkPredictor();
    private final JakartaPtPredictor pt;
    private final Map<String, CostModel> costs;

    @Inject
    public JakartaBehaviour(JakartaModeParameters commuterParameters, JakartaNonCommuterParameters nonCommuterParameters,
            JakartaPtPredictor pt, @Named("car") CostModel carCost,
            @Named("motorcycle") CostModel motorcycleCost, @Named("mcodt") CostModel mcodtCost,
            @Named("carodt") CostModel carodtCost) {
        commuter = new JakartaCommuterFormula(commuterParameters);
        nonCommuter = new JakartaNonCommuterFormula(nonCommuterParameters);
        this.pt = pt;
        // Strip behavioural access/search constants from prediction. Each formula applies its own.
        ModeParameters neutral = new ModeParameters();
        neutral.car.additionalAccessEgressWalkTime_min = 0;
        neutral.car.constantParkingSearchPenalty_min = 0;
        car = new CarPredictor(neutral, carCost);
        costs = Map.of("motorcycle", motorcycleCost, "mcodt", mcodtCost, "carodt", carodtCost);
    }
    public double estimate(String mode, Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        JakartaSubpopulation group = JakartaSubpopulation.select(person);
        JakartaPersonData data = JakartaPersonData.read(person, "mcodt".equals(mode) || "carodt".equals(mode));
        JakartaTripFeatures features = features(mode, person, trip, elements);
        return group == JakartaSubpopulation.COMMUTER
            ? commuter.evaluate(mode, data, features) : nonCommuter.evaluate(mode, data, features);
    }
    public JakartaTripFeatures features(String mode, Person person, DiscreteModeChoiceTrip trip,
            List<? extends PlanElement> elements) {
        if ("pt".equals(mode)) {
            var v = pt.predictVariables(person, trip, elements);
            return new JakartaTripFeatures(v.inVehicleTime_min, v.accessEgressTime_min, v.waitingTime_min,
                v.euclideanDistance_km, v.cost_MU);
        }
        if ("car".equals(mode)) {
            var v = car.predict(person, trip, elements); // deliberately bypass core trip-only cache
            return new JakartaTripFeatures(v.travelTime_min, v.accessEgressTime_min, 0, v.euclideanDistance_km, v.cost_MU);
        }
        double distance = PredictorUtils.calculateEuclideanDistance_km(trip);
        if ("walk".equals(mode)) return new JakartaTripFeatures(walk.predict(person, trip, elements).travelTime_min, 0, 0, distance, 0);
        // Same single-leg and undefined-time conventions as 2.8.0 Jakarta direct-mode predictors.
        if (elements.size() != 1 || !(elements.get(0) instanceof Leg))
            throw new IllegalStateException("Person " + person.getId() + ": unsupported multi-stage " + mode + " trip");
        double time = ((Leg) elements.get(0)).getTravelTime().orElse(0) / 60;
        return new JakartaTripFeatures(time, 0, 0, distance, costs.get(mode).calculateCost_MU(person, trip, elements));
    }
}
