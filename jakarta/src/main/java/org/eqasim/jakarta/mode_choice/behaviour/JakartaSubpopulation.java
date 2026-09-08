package org.eqasim.jakarta.mode_choice.behaviour;

import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

public enum JakartaSubpopulation {
    COMMUTER, NON_COMMUTER;

    public static JakartaSubpopulation select(Person person) {
        Object raw = person.getAttributes().getAttribute("subpopulation");
        if (!(raw instanceof String)) throw invalid(person, raw);
        String value = PopulationUtils.getSubpopulation(person);
        if ("commuter".equals(value)) return COMMUTER;
        if ("non_commuter".equals(value)) return NON_COMMUTER;
        throw invalid(person, value);
    }
    private static IllegalArgumentException invalid(Person person, Object value) {
        return new IllegalArgumentException("Person " + person.getId()
            + ": invalid subpopulation=" + value + "; expected commuter or non_commuter");
    }
}
