package org.eqasim.jakarta.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.PtConstants;

class AnnotateHomeSideTripAttributesTest {
    @Test
    void annotatesEverySubstantiveTripAcrossPlansAndIsIdempotent() {
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("person"));

        Plan firstPlan = PopulationUtils.createPlan();
        Activity home = activity("home");
        Activity ptInteraction = activity(PtConstants.TRANSIT_ACTIVITY_TYPE);
        Activity work = activity("work");
        Activity shop = activity("shop");
        home.getAttributes().putAttribute("unrelated", "preserved");
        ptInteraction.getAttributes().putAttribute("stageAttribute", "untouched");

        firstPlan.addActivity(home);
        firstPlan.addLeg(PopulationUtils.createLeg(TransportMode.motorcycle));
        firstPlan.addActivity(ptInteraction);
        firstPlan.addLeg(PopulationUtils.createLeg(TransportMode.pt));
        firstPlan.addActivity(work);
        firstPlan.addLeg(PopulationUtils.createLeg(TransportMode.walk));
        firstPlan.addActivity(shop);
        person.addPlan(firstPlan);

        Plan secondPlan = PopulationUtils.createPlan();
        Activity leisure = activity("leisure");
        Activity secondHome = activity("home");
        secondPlan.addActivity(leisure);
        secondPlan.addLeg(PopulationUtils.createLeg(TransportMode.pt));
        secondPlan.addActivity(secondHome);
        person.addPlan(secondPlan);

        AnnotateHomeSideTripAttributes annotator = new AnnotateHomeSideTripAttributes();
        annotator.run(person);
        int homeAttributeCount = home.getAttributes().size();
        int workAttributeCount = work.getAttributes().size();
        int leisureAttributeCount = leisure.getAttributes().size();

        annotator.run(person);

        assertSame(home.getAttributes(), TripStructureUtils.getTrips(firstPlan).get(0).getTripAttributes());
        assertEndpointTypes(home, "home", "work");
        assertEndpointTypes(work, "work", "shop");
        assertEndpointTypes(leisure, "leisure", "home");
        assertEquals("preserved", home.getAttributes().getAttribute("unrelated"));

        assertNull(ptInteraction.getAttributes().getAttribute(
                HomeSideTripAttributes.SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE));
        assertNull(ptInteraction.getAttributes().getAttribute(
                HomeSideTripAttributes.SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE));
        assertEquals("untouched", ptInteraction.getAttributes().getAttribute("stageAttribute"));

        assertEquals(homeAttributeCount, home.getAttributes().size());
        assertEquals(workAttributeCount, work.getAttributes().size());
        assertEquals(leisureAttributeCount, leisure.getAttributes().size());
    }

    @Test
    void metadataSurvivesNormalMatsimPlanCopy() {
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId("copy-person"));
        Plan source = PopulationUtils.createPlan();
        source.addActivity(activity("home"));
        source.addLeg(PopulationUtils.createLeg(TransportMode.pt));
        source.addActivity(activity("work"));
        person.addPlan(source);

        new AnnotateHomeSideTripAttributes().run(person);

        Plan copy = PopulationUtils.createPlan();
        PopulationUtils.copyFromTo(source, copy);
        TripStructureUtils.Trip copiedTrip = TripStructureUtils.getTrips(copy).get(0);

        assertEquals("home", copiedTrip.getTripAttributes().getAttribute(
                HomeSideTripAttributes.SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE));
        assertEquals("work", copiedTrip.getTripAttributes().getAttribute(
                HomeSideTripAttributes.SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE));
    }

    private static Activity activity(String type) {
        return PopulationUtils.createActivityFromCoord(type, null);
    }

    private static void assertEndpointTypes(Activity activity, String originType, String destinationType) {
        assertEquals(originType, activity.getAttributes().getAttribute(
                HomeSideTripAttributes.SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE));
        assertEquals(destinationType, activity.getAttributes().getAttribute(
                HomeSideTripAttributes.SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE));
    }
}
