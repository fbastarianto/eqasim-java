package ch.sbb.matsim.routing.pt.raptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder.Direction;
import org.eqasim.jakarta.routing.HomeSideRaptorDiagnostics;
import org.eqasim.jakarta.routing.HomeSideTripAttributes;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.PopulationUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

class JakartaHomeSideRaptorStopFinderTest {
    @Test
    void appliesDirectionalTruthTable() {
        assertDecision("home", "home", Direction.ACCESS, true);
        assertDecision("home", "home", Direction.EGRESS, true);

        assertDecision("home", "work", Direction.ACCESS, true);
        assertDecision("home", "work", Direction.EGRESS, false);

        assertDecision("work", "home", Direction.ACCESS, false);
        assertDecision("work", "home", Direction.EGRESS, true);

        assertDecision("work", "education", Direction.ACCESS, false);
        assertDecision("work", "education", Direction.EGRESS, false);
    }

    @Test
    void scansAllFeederLegsForMotorcycle() {
        InitialStop motorcycleOnly = candidate(1.0, TransportMode.motorcycle);
        InitialStop motorcycleThenWalk = candidate(2.0, TransportMode.motorcycle, TransportMode.walk);
        InitialStop walkOnly = candidate(3.0, TransportMode.walk);
        InitialStop mcodtOnly = candidate(4.0, "mcodt");
        InitialStop mcodtThenWalk = candidate(5.0, "mcodt", TransportMode.walk);

        List<InitialStop> candidates = List.of(
                motorcycleOnly, motorcycleThenWalk, walkOnly, mcodtOnly, mcodtThenWalk);
        HomeSideRaptorDiagnostics diagnostics = new HomeSideRaptorDiagnostics();
        JakartaHomeSideRaptorStopFinder finder = finder(candidates, diagnostics);

        List<InitialStop> result = find(finder, "work", "home", Direction.ACCESS);

        assertFalse(result.contains(motorcycleOnly));
        assertFalse(result.contains(motorcycleThenWalk));
        assertTrue(result.contains(walkOnly));
        assertTrue(result.contains(mcodtOnly));
        assertTrue(result.contains(mcodtThenWalk));
        assertEquals(2, diagnostics.snapshot().accessMotorcycleCandidates());
        assertEquals(2, diagnostics.snapshot().accessMotorcycleCandidatesRemoved());
    }

    @Test
    void checksPinnedInitialStopModeFieldDefensively() {
        InitialStop modeOnlyMotorcycle = new InitialStop(
                null, 1.0, 2.0, 3.0, TransportMode.motorcycle);

        List<InitialStop> result = find(
                finder(List.of(modeOnlyMotorcycle), new HomeSideRaptorDiagnostics()),
                "work",
                "home",
                Direction.ACCESS);

        assertTrue(result.isEmpty());
    }

    @Test
    void preservesNonMotorcycleCandidatesByIdentityElementsAndCost() {
        List<PlanElement> walkElements = new ArrayList<>();
        walkElements.add(PopulationUtils.createLeg(TransportMode.walk));
        InitialStop walk = new InitialStop(null, 7.5, 12.0, walkElements);
        InitialStop mcodt = candidate(8.5, "mcodt", TransportMode.walk);
        List<InitialStop> candidates = new ArrayList<>(List.of(walk, mcodt));

        JakartaHomeSideRaptorStopFinder finder = finder(candidates, new HomeSideRaptorDiagnostics());
        List<InitialStop> result = find(finder, "work", "education", Direction.ACCESS);

        assertSame(candidates, result);
        assertSame(walk, result.get(0));
        assertSame(walkElements, walk.planElements);
        assertEquals(7.5, walk.accessCost);
        assertSame(mcodt, result.get(1));
        assertEquals(8.5, mcodt.accessCost);
    }

    @Test
    void removesLowestCostForbiddenMotorcycleBeforeRouteSelectionButRetainsAlternatives() {
        InitialStop motorcycle = candidate(1.0, TransportMode.motorcycle);
        InitialStop walk = candidate(10.0, TransportMode.walk);
        InitialStop mcodt = candidate(20.0, "mcodt");
        List<InitialStop> candidates = List.of(motorcycle, walk, mcodt);

        List<InitialStop> forbidden = find(
                finder(candidates, new HomeSideRaptorDiagnostics()),
                "work",
                "home",
                Direction.ACCESS);
        List<InitialStop> allowed = find(
                finder(candidates, new HomeSideRaptorDiagnostics()),
                "home",
                "work",
                Direction.ACCESS);

        assertEquals(List.of(walk, mcodt), forbidden);
        assertSame(candidates, allowed);
        assertTrue(allowed.contains(motorcycle));
    }

    @Test
    void failsClosedWithDiagnosticWhenRequiredEndpointMetadataIsMissing() {
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("missing-person"));
        HomeSideRaptorDiagnostics diagnostics = new HomeSideRaptorDiagnostics();
        JakartaHomeSideRaptorStopFinder finder = finder(
                List.of(candidate(1.0, TransportMode.motorcycle)), diagnostics);

        IllegalStateException accessError = assertThrows(
                IllegalStateException.class,
                () -> finder.findStops(
                        null, null, person, 0.0, new AttributesImpl(), null, null, Direction.ACCESS));
        IllegalStateException egressError = assertThrows(
                IllegalStateException.class,
                () -> finder.findStops(
                        null, null, person, 0.0, new AttributesImpl(), null, null, Direction.EGRESS));

        assertTrue(accessError.getMessage().contains("person=missing-person"));
        assertTrue(accessError.getMessage().contains("direction=ACCESS"));
        assertTrue(accessError.getMessage().contains(
                HomeSideTripAttributes.SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE));
        assertTrue(egressError.getMessage().contains("direction=EGRESS"));
        assertTrue(egressError.getMessage().contains(
                HomeSideTripAttributes.SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE));
        assertEquals(2, diagnostics.snapshot().missingEndpointMetadata());
    }

    @Test
    void failsLoudForTrulyUninspectableFeederCandidate() {
        InitialStop uninspectable = new InitialStop(null, 1.0, 2.0, 3.0, null);
        HomeSideRaptorDiagnostics diagnostics = new HomeSideRaptorDiagnostics();
        JakartaHomeSideRaptorStopFinder finder = finder(List.of(uninspectable), diagnostics);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> find(finder, "home", "home", Direction.ACCESS));

        assertTrue(error.getMessage().contains("cannot inspect feeder candidate"));
        assertTrue(error.getMessage().contains("direction=ACCESS"));
        assertEquals(1, diagnostics.snapshot().uninspectableFeederCandidates());
    }

    @Test
    void retainsEmptyPlanElementsAsInspectableNonMotorcycleCandidate() {
        InitialStop emptyElements = new InitialStop(null, 1.0, 2.0, List.of());
        List<InitialStop> candidates = List.of(emptyElements);
        HomeSideRaptorDiagnostics diagnostics = new HomeSideRaptorDiagnostics();
        JakartaHomeSideRaptorStopFinder finder = finder(candidates, diagnostics);

        List<InitialStop> result = find(finder, "work", "education", Direction.ACCESS);

        assertSame(candidates, result);
        assertSame(emptyElements, result.get(0));
        assertEquals(0, diagnostics.snapshot().uninspectableFeederCandidates());
    }

    private static void assertDecision(
            String originType,
            String destinationType,
            Direction direction,
            boolean expectedKept) {
        InitialStop motorcycle = candidate(1.0, TransportMode.motorcycle);
        List<InitialStop> candidates = List.of(motorcycle);
        List<InitialStop> result = find(
                finder(candidates, new HomeSideRaptorDiagnostics()),
                originType,
                destinationType,
                direction);

        assertEquals(expectedKept, result.contains(motorcycle));
        if (expectedKept) {
            assertSame(candidates, result);
        }
    }

    private static JakartaHomeSideRaptorStopFinder finder(
            List<InitialStop> candidates,
            HomeSideRaptorDiagnostics diagnostics) {
        RaptorStopFinder delegate = (from, to, person, departure, attributes, parameters, data, direction) -> candidates;
        return new JakartaHomeSideRaptorStopFinder(delegate, diagnostics);
    }

    private static List<InitialStop> find(
            JakartaHomeSideRaptorStopFinder finder,
            String originType,
            String destinationType,
            Direction direction) {
        Attributes attributes = new AttributesImpl();
        attributes.putAttribute(HomeSideTripAttributes.SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE, originType);
        attributes.putAttribute(HomeSideTripAttributes.SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE, destinationType);
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("person"));
        return finder.findStops(null, null, person, 0.0, attributes, null, null, direction);
    }

    private static InitialStop candidate(double cost, String... modes) {
        List<PlanElement> elements = new ArrayList<>();
        for (String mode : modes) {
            Leg leg = PopulationUtils.createLeg(mode);
            elements.add(leg);
        }
        return new InitialStop(null, cost, cost, elements);
    }
}
