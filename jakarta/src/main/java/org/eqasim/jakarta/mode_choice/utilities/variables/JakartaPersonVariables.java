package org.eqasim.jakarta.mode_choice.utilities.variables;

import org.eqasim.core.simulation.mode_choice.utilities.variables.BaseVariables;
import org.matsim.api.core.v01.population.Person;

public class JakartaPersonVariables implements BaseVariables {
//	public final boolean hasSubscription;
	public double hhlIncome;
	public int age;
	public String sex;
    public int vehicleOwnership;
    public int employment;
//	public boolean cityTrip;

	public JakartaPersonVariables(double hhlIncome, int age, String sex, int vehicleOwnership, int employment) {
//		this.hasSubscription = hasSubscription;
//		this.cityTrip = cityTrip;
		this.hhlIncome = hhlIncome;
		this.age = age;
		this.sex = sex;
		this.vehicleOwnership = vehicleOwnership;
        this.employment = employment;
	}

	// NEW constructor to build directly from a Person object for JakartaPTUtilityEstimator.java
	public JakartaPersonVariables(Person person) {
		this.hhlIncome = (Double) person.getAttributes().getAttribute("hhlIncome");
		this.age = (Integer) person.getAttributes().getAttribute("age");
		this.sex = (String) person.getAttributes().getAttribute("sex");
		String carAvailability = (String) person.getAttributes().getAttribute("carAvailability");
		this.vehicleOwnership = "always".equalsIgnoreCase(carAvailability) ? 1 : 0;
		String employmentStatus = (String) person.getAttributes().getAttribute("employment");
		this.employment = "yes".equalsIgnoreCase(employmentStatus) ? 1 : 0;
	}

}
