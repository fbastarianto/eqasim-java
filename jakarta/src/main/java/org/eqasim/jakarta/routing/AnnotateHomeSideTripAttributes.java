package org.eqasim.jakarta.routing;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.controler.PersonPrepareForSimAlgorithm;
import org.matsim.core.router.TripStructureUtils;

/**
 * Adds substantive endpoint types to the routing attributes consumed by
 * {@code PlanRouter} and DMC's trip-router estimator.
 *
 * <p>{@link TripStructureUtils.Trip#getTripAttributes()} is the origin
 * activity's attribute map in the pinned MATSim API. The default
 * {@link TripStructureUtils#getTrips(Plan)} stage-activity predicate ensures
 * that PT and other interaction activities are not treated as endpoints.</p>
 */
public final class AnnotateHomeSideTripAttributes implements PersonPrepareForSimAlgorithm {
    @Override
    public void run(Person person) {
        for (Plan plan : person.getPlans()) {
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                HomeSideTripAttributes.annotate(
                        trip.getTripAttributes(),
                        trip.getOriginActivity(),
                        trip.getDestinationActivity());
            }
        }
    }
}
