package ch.sbb.matsim.routing.pt.raptor;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.eqasim.jakarta.routing.HomeSideRaptorDiagnostics;
import org.eqasim.jakarta.routing.HomeSideTripAttributes;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.facilities.Facility;
import org.matsim.utils.objectattributes.attributable.Attributes;

/**
 * Jakarta's routing-level PT private-motorcycle home-side restriction.
 *
 * <p>This class deliberately lives in the SwissRailRaptor package solely as a
 * minimal compile-time compatibility bridge for MATSim 2026.0-2025w19.
 * {@link InitialStop} is public in that pinned API, but its candidate mode and
 * plan elements have package visibility and no accessors. No route or cost is
 * reconstructed: this wrapper retains the default candidates by identity and
 * removes only directionally infeasible motorcycle candidates before RAPTOR
 * chooses a path.</p>
 */
public final class JakartaHomeSideRaptorStopFinder implements RaptorStopFinder {
    private final RaptorStopFinder delegate;
    private final HomeSideRaptorDiagnostics diagnostics;

    @Inject
    public JakartaHomeSideRaptorStopFinder(
            DefaultRaptorStopFinder delegate,
            HomeSideRaptorDiagnostics diagnostics) {
        this((RaptorStopFinder) delegate, diagnostics);
    }

    JakartaHomeSideRaptorStopFinder(
            RaptorStopFinder delegate,
            HomeSideRaptorDiagnostics diagnostics) {
        this.delegate = delegate;
        this.diagnostics = diagnostics;
    }

    @Override
    public List<InitialStop> findStops(
            Facility fromFacility,
            Facility toFacility,
            Person person,
            double departureTime,
            Attributes routingAttributes,
            RaptorParameters parameters,
            SwissRailRaptorData data,
            Direction direction) {
        List<InitialStop> candidates = delegate.findStops(
                fromFacility,
                toFacility,
                person,
                departureTime,
                routingAttributes,
                parameters,
                data,
                direction);

        boolean access = direction == Direction.ACCESS;
        boolean[] motorcycleBearing = new boolean[candidates.size()];
        boolean hasMotorcycleCandidate = false;

        for (int index = 0; index < candidates.size(); index++) {
            motorcycleBearing[index] = isMotorcycleBearing(candidates.get(index), person, direction);
            if (motorcycleBearing[index]) {
                hasMotorcycleCandidate = true;
                diagnostics.recordMotorcycleCandidate(access);
            }
        }

        if (!hasMotorcycleCandidate) {
            return candidates;
        }

        String endpointKey = access
                ? HomeSideTripAttributes.SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE
                : HomeSideTripAttributes.SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE;
        String endpointName = access ? "origin" : "destination";
        String endpointType = requireEndpointType(routingAttributes, endpointKey, endpointName, person, direction);

        if (HomeSideTripAttributes.isHome(endpointType)) {
            return candidates;
        }

        List<InitialStop> filtered = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            if (motorcycleBearing[index]) {
                diagnostics.recordRemovedMotorcycleCandidate(access);
            } else {
                filtered.add(candidates.get(index));
            }
        }
        return filtered;
    }

    private boolean isMotorcycleBearing(InitialStop candidate, Person person, Direction direction) {
        if (TransportMode.motorcycle.equalsIgnoreCase(candidate.mode)) {
            return true;
        }

        if (candidate.planElements != null) {
            for (PlanElement element : candidate.planElements) {
                if (element instanceof Leg leg) {
                    if (TransportMode.motorcycle.equalsIgnoreCase(leg.getMode())) {
                        return true;
                    }
                }
            }
            return false;
        }

        if (candidate.mode != null) {
            return false;
        }

        diagnostics.recordUninspectableFeederCandidate();
        throw new IllegalStateException(
                "Jakarta PT home-side routing cannot inspect feeder candidate"
                        + " person=" + personId(person)
                        + " direction=" + direction
                        + ": InitialStop has neither a mode nor any feeder legs");
    }

    private String requireEndpointType(
            Attributes routingAttributes,
            String key,
            String endpointName,
            Person person,
            Direction direction) {
        Object value = routingAttributes == null ? null : routingAttributes.getAttribute(key);
        if (value instanceof String type) {
            return type;
        }

        diagnostics.recordMissingEndpointMetadata();
        throw new IllegalStateException(
                "Jakarta PT home-side routing is missing substantive " + endpointName
                        + " activity type metadata"
                        + " person=" + personId(person)
                        + " direction=" + direction
                        + " key=" + key);
    }

    private static String personId(Person person) {
        return person == null ? "<unavailable>" : person.getId().toString();
    }
}
