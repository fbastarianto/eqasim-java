package org.eqasim.jakarta.mode_choice.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.jakarta.mode_choice.parameters.*;
import org.eqasim.jakarta.mode_choice.costs.*;
import org.eqasim.jakarta.mode_choice.utilities.predictors.*;
import org.eqasim.jakarta.mode_choice.utilities.estimators.*;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.CommandLine;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

class JakartaBehaviourTest {
    static final Path CONFIG = Path.of(System.getProperty("jakarta.release.config", "/Users/fazafawzan/Codex/matsim_jar_rev/config"));
    @TempDir Path temp;
    static CommandLine cli(String... args) throws Exception {
        return new CommandLine.Builder(args).allowPrefixes("commuter-mode-parameter", "non-commuter-mode-parameter",
            "feeder-policy-parameter", "cost-parameter", "mode-parameter").build();
    }
    static JakartaModeParameters commuter() throws Exception {
        return JakartaParameterLoader.load(JakartaModeParameters.buildDefault(), CONFIG.resolve("UtilityParams_Commuter_3_0_0.yml").toString(), "commuter-mode-parameter", cli());
    }
    static JakartaNonCommuterParameters nonCommuter() throws Exception {
        return JakartaParameterLoader.load(new JakartaNonCommuterParameters(), CONFIG.resolve("UtilityParams_NonCommuter_3_0_0.yml").toString(), "non-commuter-mode-parameter", cli());
    }
    static Person person(String group, String sex) {
        Person p = PopulationUtils.getFactory().createPerson(Id.createPersonId("person-300-" + group));
        if (group != null) PopulationUtils.putSubpopulation(p, group);
        p.getAttributes().putAttribute("age", 40);
        p.getAttributes().putAttribute("hhlIncome", 8000.0);
        p.getAttributes().putAttribute("sex", sex);
        p.getAttributes().putAttribute("employment", "yes");
        return p;
    }
    static DiscreteModeChoiceTrip trip(String origin, String destination) {
        Activity a = PopulationUtils.createActivityFromCoord(origin, new Coord(0,0));
        Activity b = PopulationUtils.createActivityFromCoord(destination, new Coord(5000,0));
        return new DiscreteModeChoiceTrip(a,b,"pt",List.of(),1,2,0,a.getAttributes());
    }
    static Leg leg(String mode, double minutes, double km) {
        Leg l = PopulationUtils.createLeg(mode); l.setTravelTime(minutes*60);
        var route = RouteUtils.createGenericRouteImpl(Id.createLinkId("a"),Id.createLinkId("b"));
        route.setDistance(km*1000); route.setTravelTime(minutes*60); l.setRoute(route); return l;
    }
    static JakartaPtPredictor pt(JakartaFeederPolicyParameters p) {
        return new JakartaPtPredictor(new PtPredictor((person,trip,elements) -> 4) {
            @Override public PtVariables predict(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
                return new PtVariables(elements.size()*20, 3, 0, 1, 4, 5);
            }
        }, null, p);
    }
    static JakartaBehaviour behaviour(JakartaModeParameters c, JakartaNonCommuterParameters n, JakartaFeederPolicyParameters f, JakartaCostParameters cost) {
        return new JakartaBehaviour(c,n,pt(f),new JakartaCarCostModel(cost),new JakartaMotorcycleCostModel(cost),
            new JakartaMcodtCostModel(cost),new JakartaCarodtCostModel(cost));
    }
    @Test void selectorAcceptsOnlyExactGroups() {
        assertEquals(JakartaSubpopulation.COMMUTER,JakartaSubpopulation.select(person("commuter","m")));
        assertEquals(JakartaSubpopulation.NON_COMMUTER,JakartaSubpopulation.select(person("non_commuter","m")));
        Person p = person(null,"m");
        var error = assertThrows(IllegalArgumentException.class,()->JakartaSubpopulation.select(p));
        assertTrue(error.getMessage().contains(p.getId().toString())); assertTrue(error.getMessage().contains("null"));
    }
    @ParameterizedTest @ValueSource(strings={"unknown","non_commuters","Commuter","COMMUTER","Non_commuter","NON_COMMUTER"," commuter",""})
    void selectorRejectsInvalidValues(String value) {
        Person p=person(value,"m");
        var e=assertThrows(IllegalArgumentException.class,()->JakartaSubpopulation.select(p));
        assertTrue(e.getMessage().contains(p.getId().toString())); assertTrue(e.getMessage().contains("subpopulation="+value));
    }
    @Test void selectorDoesNotInferFromEmployment() {
        for(String group:List.of("commuter","non_commuter")) for(String employment:List.of("no","student","yes")) {
            Person p=person(group,"m"); p.getAttributes().putAttribute("employment",employment);
            assertEquals(group.equals("commuter")?JakartaSubpopulation.COMMUTER:JakartaSubpopulation.NON_COMMUTER,JakartaSubpopulation.select(p));
            assertEquals(employment.equals("yes"),JakartaPersonData.read(p,true).fullTime());
        }
    }
    @Test void invalidRequiredAttributesFailWithPersonId() {
        for(String key:List.of("age","hhlIncome","sex")) {
            List<Object> bad = key.equals("sex") ? Arrays.asList(null,"F","x") : Arrays.asList(null,-1.0,Double.NaN,Double.POSITIVE_INFINITY,"40");
            for(Object value:bad) {
                Person p=person("commuter","f"); p.getAttributes().putAttribute(key,value);
                var e=assertThrows(IllegalArgumentException.class,()->JakartaPersonData.read(p,true));
                assertTrue(e.getMessage().contains(p.getId().toString())); assertTrue(e.getMessage().contains(key));
            }
        }
        Person p=person("non_commuter","m"); p.getAttributes().putAttribute("hhlIncome",0);
        assertThrows(IllegalArgumentException.class,()->new JakartaPersonPredictor().predictVariables(p,trip("home","work"),List.of()));
        p.getAttributes().putAttribute("hhlIncome",8000); p.getAttributes().putAttribute("age",40.5);
        assertThrows(IllegalArgumentException.class,()->JakartaPersonData.read(p,true));
    }
    @ParameterizedTest @ValueSource(strings={"car","pt","walk","motorcycle","mcodt","carodt"})
    void calibratedCommuterEquations(String mode) throws Exception {
        var a=new JakartaPersonData(8000,40,new String("f"),true);
        var v=new JakartaTripFeatures(20,7,3,5,30);
        double d=Math.pow(5/7.67,-.75),h=Math.pow(8000.0/5327,-.06),cost=-.019*30;
        double expected=switch(mode) {
            case "car" -> -1.370-.054*20+cost*d*h+.102*5+.026*40;
            case "pt" -> -1.100-.052*7-.013*20+cost*d*h-.031*40+.808;
            case "walk" -> -1.070-.054*20-1500;
            case "motorcycle" -> -.330-.054*20+cost*h;
            case "mcodt" -> -2.820-.054*20-.017*40+.83+cost*d*h;
            case "carodt" -> -4.924-.0826*20-.017*40+.83+cost*h;
            default -> throw new AssertionError();
        };
        assertEquals(expected,new JakartaCommuterFormula(commuter()).evaluate(mode,a,v),1e-10);
    }
    @ParameterizedTest @ValueSource(strings={"car","pt","walk","motorcycle","mcodt","carodt"})
    void recoveredNonCommuterEquations(String mode) throws Exception {
        var a=new JakartaPersonData(8000,40,new String("m"),true);
        var v=new JakartaTripFeatures(20,7,3,5,30);
        double d=Math.pow(5/7.67,-.75),h=Math.pow(8000.0/5327,-.06),cost=-.0208*30;
        double expected=switch(mode) {
            case "car" -> -.514-.04*20-.007*7+cost*d*h;
            case "pt" -> -3.5-.05*7-.005*20-.05*3+cost*d;
            case "walk" -> -4.5-.007*20+.0103*40;
            case "motorcycle" -> -.0948*20-.007*7-.0083*40+cost*d*h;
            case "mcodt" -> -2.5-.1652*20-.0132*40-1.15+cost*d*h;
            case "carodt" -> -5-.0826*20-.0132*40-.42+cost*d*h;
            default -> throw new AssertionError();
        };
        assertEquals(expected,new JakartaNonCommuterFormula(nonCommuter()).evaluate(mode,a,v),1e-10);
    }
    @Test void sexUsesContentEqualityWithOppositeGroupDummies() throws Exception {
        var f=new JakartaPersonData(8000,40,new String("f"),true);
        var m=new JakartaPersonData(8000,40,new String("m"),true);
        var v=new JakartaTripFeatures(20,0,0,5,30);
        var c=new JakartaCommuterFormula(commuter()); var n=new JakartaNonCommuterFormula(nonCommuter());
        for(String mode:List.of("mcodt","carodt")) {
            assertEquals(.83,c.evaluate(mode,f,v)-c.evaluate(mode,m,v),1e-10);
            assertEquals(mode.equals("mcodt")?-1.15:-.42,n.evaluate(mode,m,v)-n.evaluate(mode,f,v),1e-10);
        }
    }
    @Test void nonCommuterPtHasNoIncomeAgeEmploymentEffect() throws Exception {
        var n=new JakartaNonCommuterFormula(nonCommuter());var v=new JakartaTripFeatures(20,7,3,5,30);
        assertEquals(n.evaluate("pt",new JakartaPersonData(100,10,"m",false),v),n.evaluate("pt",new JakartaPersonData(100000,80,"f",true),v));
        var c=new JakartaCommuterFormula(commuter());
        assertNotEquals(c.evaluate("pt",new JakartaPersonData(100,10,"m",false),v),c.evaluate("pt",new JakartaPersonData(100000,80,"f",true),v));
    }
    @Test void nonCommuterWalkHasNoDistancePenaltyAndCommuterThresholdIsPreserved() throws Exception {
        var a=new JakartaPersonData(8000,40,"m",false);var n=new JakartaNonCommuterFormula(nonCommuter());var c=new JakartaCommuterFormula(commuter());
        for(double distance:List.of(1.8,1.801,3.747,3.748,20.0)) {
            var v=new JakartaTripFeatures(20,0,0,distance,0);
            assertEquals(-4.5-.007*20+.0103*40,n.evaluate("walk",a,v),1e-10);
            assertEquals(-1.070-.054*20+(distance>3.747?-1500:0),c.evaluate("walk",a,v),1e-10);
        }
    }
    @Test void independentDefaultsYamlAndCliPrecedence() throws Exception {
        Path c=temp.resolve("c.yml"),n=temp.resolve("n.yml");Files.writeString(c,"car.alpha_u: 11\n");Files.writeString(n,"car.alpha_u: 22\n");
        assertEquals(-.50,JakartaParameterLoader.load(JakartaModeParameters.buildDefault(),null,"commuter-mode-parameter",cli()).car.alpha_u);
        assertEquals(-.514,JakartaParameterLoader.load(new JakartaNonCommuterParameters(),null,"non-commuter-mode-parameter",cli()).car.alpha_u);
        assertEquals(11,JakartaParameterLoader.load(JakartaModeParameters.buildDefault(),c.toString(),"commuter-mode-parameter",cli()).car.alpha_u);
        assertEquals(22,JakartaParameterLoader.load(new JakartaNonCommuterParameters(),n.toString(),"non-commuter-mode-parameter",cli()).car.alpha_u);
        var cmd=cli("--commuter-mode-parameter:car.alpha_u","33","--non-commuter-mode-parameter:car.alpha_u","44");
        var cp=JakartaParameterLoader.load(JakartaModeParameters.buildDefault(),c.toString(),"commuter-mode-parameter",cmd);
        var np=JakartaParameterLoader.load(new JakartaNonCommuterParameters(),n.toString(),"non-commuter-mode-parameter",cmd);
        assertEquals(33,cp.car.alpha_u);assertEquals(44,np.car.alpha_u);
        var onlyC=cli("--commuter-mode-parameter:car.alpha_u","55");
        assertEquals(22,JakartaParameterLoader.load(new JakartaNonCommuterParameters(),n.toString(),"non-commuter-mode-parameter",onlyC).car.alpha_u);
        var onlyN=cli("--non-commuter-mode-parameter:car.alpha_u","66");
        assertEquals(11,JakartaParameterLoader.load(JakartaModeParameters.buildDefault(),c.toString(),"commuter-mode-parameter",onlyN).car.alpha_u);
    }
    @Test void sharedPolicyPrecedenceAndAmbiguousCliRejection() throws Exception {
        Path f=temp.resolve("f.yml"),d=temp.resolve("d.yml"); Files.writeString(f,"subsidyShare_mcodt: 0.25\n");Files.writeString(d,"carCost_KIDR_km: 3.6875\n");
        assertEquals(0,new JakartaFeederPolicyParameters().subsidyShare_mcodt);assertEquals(2.95,JakartaCostParameters.buildDefault().carCost_KIDR_km);
        assertEquals(.25,JakartaParameterLoader.load(new JakartaFeederPolicyParameters(),f.toString(),"feeder-policy-parameter",cli()).subsidyShare_mcodt);
        assertEquals(3.6875,JakartaParameterLoader.load(JakartaCostParameters.buildDefault(),d.toString(),"cost-parameter",cli()).carCost_KIDR_km);
        var cmd=cli("--feeder-policy-parameter:subsidyShare_mcodt","0.5","--cost-parameter:carCost_KIDR_km","4");
        assertEquals(.5,JakartaParameterLoader.load(new JakartaFeederPolicyParameters(),f.toString(),"feeder-policy-parameter",cmd).subsidyShare_mcodt);
        assertEquals(4,JakartaParameterLoader.load(JakartaCostParameters.buildDefault(),d.toString(),"cost-parameter",cmd).carCost_KIDR_km);
        var bad=cli("--mode-parameter:car.alpha_u","-1");assertThrows(IllegalArgumentException.class,()->JakartaParameterLoader.rejectLegacyOverrides(bad));
    }
    @ParameterizedTest @ValueSource(strings={"car","pt","walk","motorcycle","mcodt","carodt"})
    void actualModeAliasesDispatchAndInterleaveWithoutLeaks(String mode) throws Exception {
        var cp=commuter();var np=nonCommuter();var b=behaviour(cp,np,new JakartaFeederPolicyParameters(),JakartaCostParameters.buildDefault());
        var estimator=switch(mode) {
            case "car" -> new JakartaCarUtilityEstimator(b);case "pt" -> new JakartaPTUtilityEstimator(b);
            case "walk" -> new JakartaWalkUtilityEstimator(b);case "motorcycle" -> new JakartaMotorcycleUtilityEstimator(b);
            case "mcodt" -> new JakartaMcodtUtilityEstimator(b);case "carodt" -> new JakartaCarodtUtilityEstimator(b);
            default -> throw new AssertionError();
        };
        var trip=trip("home","work");List<PlanElement> elements=List.of(leg(mode,20,6));
        for(int i=0;i<30;i++) for(String group:List.of("commuter","non_commuter")) {
            var person=person(group,new String("m"));var data=JakartaPersonData.read(person,true);var v=b.features(mode,person,trip,elements);
            double expected=group.equals("commuter")?new JakartaCommuterFormula(cp).evaluate(mode,data,v):new JakartaNonCommuterFormula(np).evaluate(mode,data,v);
            assertEquals(expected,estimator.estimateUtility(person,trip,elements),1e-10);
        }
    }
    @Test void carAccessAndParkingParametersAreScopedAfterPhysicalPrediction() throws Exception {
        var c=commuter();var n=nonCommuter();c.car.constantParkingSearchPenalty_min=100;c.car.additionalAccessEgressWalkTime_min=200;
        n.car.constantParkingSearchPenalty_min=2;n.car.additionalAccessEgressWalkTime_min=3;
        var b=behaviour(c,n,new JakartaFeederPolicyParameters(),JakartaCostParameters.buildDefault());var p=person("non_commuter","m");var t=trip("home","work");
        List<PlanElement> elements=List.of(leg("walk",7,0.5),leg("car",20,6));
        var v=b.features("car",p,t,elements); assertEquals(20,v.time());assertEquals(7,v.access());
        assertEquals(-.514-.04*22-.007*10-.0208*17.7*Math.pow(5/7.67,-.75)*Math.pow(8000.0/5327,-.06),b.estimate("car",p,t,elements),1e-10);
        var mc=List.<PlanElement>of(leg("motorcycle",20,6));
        assertEquals(0,b.features("motorcycle",p,t,mc).access());
        assertEquals(-.0948*20-.007*3-.0083*40-.0208*3.54*Math.pow(5/7.67,-.75)*Math.pow(8000.0/5327,-.06),b.estimate("motorcycle",p,t,mc),1e-10);
    }
}
