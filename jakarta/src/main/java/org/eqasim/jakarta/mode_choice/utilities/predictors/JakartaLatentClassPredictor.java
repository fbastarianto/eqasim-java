package org.eqasim.jakarta.mode_choice.utilities.predictors;

import org.eqasim.jakarta.mode_choice.utilities.variables.JakartaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import javax.inject.Inject;

public class JakartaLatentClassPredictor {
    @Inject private JakartaPersonPredictor personPredictor;

    public int predictClass(Person person, DiscreteModeChoiceTrip trip) {
        // Skip classification for non-employed individuals
        String employment = (String) person.getAttributes().getAttribute("employment");
        if (!"yes".equals(employment)) {
            return -1; // Special value for non-employed
        }

        JakartaPersonVariables vars = personPredictor.predictVariables(person, trip);

        // Class 1: Non-private motorised (older, no vehicle)
        if (vars.age > 40 && vars.vehicleOwnership == 0) {
            return 1;
        }
        // Class 2: Young cost-sensitive motorised (young male with vehicle)
        else if (vars.age <= 40 && vars.vehicleOwnership > 0 && "m".equals(vars.sex)) {
            return 2;
        }
        // Class 3: Affluent car-dependent (older male with vehicle)
        else if (vars.age > 40 && vars.vehicleOwnership > 0 && "m".equals(vars.sex)) {
            return 3;
        }
        // Class 4: Young time-sensitive (young female)
        else if (vars.age <= 40 && "f".equals(vars.sex)) {
            return 4;
        }
        // Default fallback (Class 3)
        return 3;
    }
}
