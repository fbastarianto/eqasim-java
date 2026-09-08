package org.eqasim.jakarta.mode_choice.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import static org.eqasim.jakarta.mode_choice.behaviour.JakartaBehaviourTest.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.jakarta.mode_choice.parameters.*;
import org.eqasim.jakarta.mode_choice.costs.*;
import org.eqasim.jakarta.mode_choice.utilities.predictors.*;
import org.eqasim.jakarta.mode_choice.utilities.estimators.*;
import org.eqasim.jakarta.mode_choice.JakartaModeAvailability;
import org.eqasim.jakarta.mode_choice.constraints.NoMotorcycleEgressExceptHome;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.DefaultRoutedTripCandidate;

class JakartaPricingAndFeederTest {
    static Activity interaction() { return PopulationUtils.createActivityFromCoord("pt interaction",new Coord(0,0)); }
    static List<PlanElement> feeders(double km) {
        return List.of(leg("mcodt",7,km),interaction(),leg("pt",20,5),interaction(),leg("walk",2,.1));
    }
    @ParameterizedTest @ValueSource(strings={"Baseline","car","mc","carMc","mcOdt","all"})
    void allPricingScenariosHaveIdenticalMonetaryCostsAcrossGroups(String scenario) throws Exception {
        String direct=switch(scenario) {case "car","mc","carMc" -> scenario;case "all" -> "carMc";default -> null;};
        var cost=JakartaParameterLoader.load(JakartaCostParameters.buildDefault(),direct==null?null:CONFIG.resolve("JktCostParams_scen_"+direct+"_25pct.yml").toString(),"cost-parameter",cli());
        String feeder=(scenario.equals("mcOdt")||scenario.equals("all"))?"scen_mcOdt_25pct":"Baseline";
        var f=JakartaParameterLoader.load(new JakartaFeederPolicyParameters(),CONFIG.resolve("FeederPolicy_"+feeder+"_3_0_0.yml").toString(),"feeder-policy-parameter",cli());
        var b=behaviour(commuter(),nonCommuter(),f,cost);var t=trip("home","work");
        assertEquals(1.9,f.per_km_mcodt);
        Map<String,Double> expected=Map.of("car",(Set.of("car","carMc","all").contains(scenario)?3.6875:2.95)*6,
            "motorcycle",(Set.of("mc","carMc","all").contains(scenario)?.7375:.59)*6,"mcodt",19.0,"carodt",33.0);
        for(String mode:expected.keySet()) {
            var elements=List.<PlanElement>of(leg(mode,20,6));
            double c=b.features(mode,person("commuter","m"),t,elements).cost();double n=b.features(mode,person("non_commuter","m"),t,elements).cost();
            assertEquals(c,n); assertEquals(expected.get(mode),c,1e-10);
        }
        for(double km:List.of(2.0,20.0)) {
            double gross=1.9*km;double expectedPt=4+gross-Math.min(f.subsidyShare_mcodt*gross,5);
            var elements=feeders(km);
            for(String group:List.of("commuter","non_commuter")) assertEquals(expectedPt,b.features("pt",person(group,"m"),t,elements).cost(),1e-10);
        }
    }
    @Test void feederEligibilityTimeBaseFareAndCapMatchProductionRules() {
        var f=new JakartaFeederPolicyParameters();f.base_mcodt=2;f.subsidyShare_mcodt=.25;
        var predictor=pt(f);var t=trip("home","work");
        var elements=List.<PlanElement>of(leg("mcodt",7,2),interaction(),leg("pt",20,5),interaction(),leg("mcodt",3,1),leg("walk",2,.1));
        for(String group:List.of("commuter","non_commuter")) {
            var v=predictor.predictVariables(person(group,"m"),t,elements);
            assertEquals(10,v.accessEgressTime_min);assertEquals(4+(4+1.9*3)*.75,v.cost_MU,1e-10);
            // Same trip with a changed candidate must not return the previous result.
            var changed=predictor.predictVariables(person(group,"m"),t,feeders(20));
            assertEquals(9,changed.accessEgressTime_min);assertEquals(4+2+38-5,changed.cost_MU,1e-10);
            var noAdjacent=List.<PlanElement>of(leg("mcodt",7,20),leg("pt",20,5));
            assertEquals(4,predictor.predictVariables(person(group,"m"),t,noAdjacent).cost_MU);
            var zeroTime=List.<PlanElement>of(leg("mcodt",0,20),interaction(),leg("pt",20,5));
            assertEquals(4,predictor.predictVariables(person(group,"m"),t,zeroTime).cost_MU);
        }
    }
    @Test void concurrentInterleavedCandidatesHaveNoStaleFeederResultsOrDoubleAugmentation() throws Exception {
        var f=new JakartaFeederPolicyParameters();f.subsidyShare_mcodt=.25;var predictor=pt(f);var t=trip("home","work");
        var cp=commuter();var np=nonCommuter();
        var b=behaviour(cp,np,f,JakartaCostParameters.buildDefault());
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> jobs=new ArrayList<>();
            for(int i=0;i<200;i++) {
                final int j=i;
                jobs.add(()->{
                    var p=person(j%2==0?"commuter":"non_commuter",new String("m"));double km=1+j%20;
                    var e=feeders(km);double expected=4+1.9*km-Math.min(.25*1.9*km,5);
                    assertEquals(expected,predictor.predictVariables(p,t,e).cost_MU,1e-10);
                    assertEquals(expected,predictor.predictVariables(p,t,e).cost_MU,1e-10);
                    var data=JakartaPersonData.read(p,true);var v=b.features("pt",p,t,e);
                    double u=j%2==0?new JakartaCommuterFormula(cp).evaluate("pt",data,v):new JakartaNonCommuterFormula(np).evaluate("pt",data,v);
                    assertEquals(u,b.estimate("pt",p,t,e),1e-10); return null;
                });
            }
            for(var job:pool.invokeAll(jobs)) job.get();
        }
    }
    @Test void guardsAndSharedAvailabilityAreIdenticalForBothPopulations() throws Exception {
        var b=behaviour(commuter(),nonCommuter(),new JakartaFeederPolicyParameters(),JakartaCostParameters.buildDefault());
        var estimator=new JakartaPTUtilityEstimator(b);var constraint=new NoMotorcycleEgressExceptHome();
        for(String group:List.of("commuter","non_commuter")) {
            var p=person(group,"m");
            assertEquals(Double.NEGATIVE_INFINITY,estimator.estimateUtility(p,trip("work","home"),List.of(leg("motorcycle",1,1),leg("pt",20,5))));
            assertEquals(Double.NEGATIVE_INFINITY,estimator.estimateUtility(p,trip("home","work"),List.of(leg("pt",20,5),leg("motorcycle",1,1))));
            var intervening=List.<PlanElement>of(leg("pt",20,5),interaction(),leg("walk",1,.1),leg("motorcycle",1,1));
            assertFalse(constraint.validateAfterEstimation(trip("home","work"),new DefaultRoutedTripCandidate(0,"pt",intervening,0),List.of()));
            // Preserve existing constraint treatment of a PT-labelled walk-only alternative.
            assertTrue(constraint.validateAfterEstimation(trip("home","work"),new DefaultRoutedTripCandidate(0,"pt",List.of(leg("walk",1,.1)),0),List.of()));
            var availability=new JakartaModeAvailability().getAvailableModes(p,List.of());assertFalse(availability.contains("bike"));
            p.getAttributes().putAttribute("outside",true);p.getAttributes().putAttribute("isPassenger",true);
            availability=new JakartaModeAvailability().getAvailableModes(p,List.of());assertTrue(availability.containsAll(List.of("outside","car_passenger")));
        }
    }
    @Test void realCorePtPredictorWaitingAndSharedBusRailFareAreUnchanged() {
        Scenario scenario=ScenarioUtils.createScenario(ConfigUtils.createConfig());var schedule=scenario.getTransitSchedule();var factory=schedule.getFactory();
        var a=factory.createTransitStopFacility(Id.create("a",TransitStopFacility.class),new Coord(0,0),false);
        var z=factory.createTransitStopFacility(Id.create("z",TransitStopFacility.class),new Coord(5000,0),false);
        a.setLinkId(Id.createLinkId("a"));z.setLinkId(Id.createLinkId("b"));schedule.addStopFacility(a);schedule.addStopFacility(z);
        List<Leg> legs=new ArrayList<>();
        for(String mode:List.of("bus","rail")) {
            var line=factory.createTransitLine(Id.create(mode,TransitLine.class));
            var route=factory.createTransitRoute(Id.create(mode,TransitRoute.class),null,List.of(factory.createTransitRouteStop(a,0,0),factory.createTransitRouteStop(z,1200,1200)),mode);
            line.addRoute(route);schedule.addTransitLine(line);
            var r=new DefaultTransitPassengerRoute(a,line,route,z);r.setBoardingTime(mode.equals("bus")?120:180);r.setDistance(5000);
            var l=PopulationUtils.createLeg("pt");l.setDepartureTime(0);l.setTravelTime(mode.equals("bus")?1320:1380);l.setRoute(r);legs.add(l);
        }
        var costs=new JakartaPtCostModel(JakartaCostParameters.buildDefault(),new JakartaPersonPredictor(),scenario);
        var predictor=new JakartaPtPredictor(new PtPredictor(costs),schedule,new JakartaFeederPolicyParameters());var t=trip("home","work");
        for(String group:List.of("commuter","non_commuter")) {
            var p=person(group,"f");
            List<PlanElement> e=List.of(leg("mcodt",7,2),interaction(),legs.get(0),interaction(),legs.get(1),interaction(),leg("walk",2,.1));
            var v=predictor.predictVariables(p,t,e);
            assertEquals(40,v.inVehicleTime_min);assertEquals(3,v.waitingTime_min);assertEquals(9,v.accessEgressTime_min);assertEquals(1,v.numberOfLineSwitches);assertEquals(13.8,v.cost_MU,1e-10);
            // Same trip identity, a single bus candidate: fare/IVT/waiting must be freshly computed.
            var single=predictor.predictVariables(p,t,List.of(legs.get(0)));assertEquals(4,single.cost_MU);assertEquals(20,single.inVehicleTime_min);assertEquals(0,single.waitingTime_min);
        }
    }
}
