package org.eqasim.jakarta.mode_choice.behaviour;

import org.matsim.api.core.v01.population.Person;

/** Validated data only: no imputation, behavioural coefficients, or caching. */
public record JakartaPersonData(double income, double age, String sex, boolean fullTime) {
    public static JakartaPersonData read(Person person, boolean needsSex) {
        double income = number(person, "hhlIncome");
        if (income <= 0) throw invalid(person, "hhlIncome");
        double age = number(person, "age");
        if (age < 0 || age != Math.rint(age)) throw invalid(person, "age");
        Object sex = person.getAttributes().getAttribute("sex");
        if (needsSex && !"m".equals(sex) && !"f".equals(sex)) throw invalid(person, "sex");
        return new JakartaPersonData(income, age, sex instanceof String ? (String) sex : null,
            "yes".equals(person.getAttributes().getAttribute("employment")));
    }
    private static double number(Person person, String key) {
        Object value = person.getAttributes().getAttribute(key);
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue()))
            throw invalid(person, key);
        return ((Number) value).doubleValue();
    }
    private static IllegalArgumentException invalid(Person person, String key) {
        return new IllegalArgumentException("Person " + person.getId() + ": invalid " + key + "="
            + person.getAttributes().getAttribute(key));
    }
}
