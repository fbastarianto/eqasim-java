package org.eqasim.jakarta.mode_choice.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import static org.eqasim.jakarta.mode_choice.behaviour.JakartaBehaviourTest.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.jakarta.JakartaConfigurator;
import org.eqasim.jakarta.mode_choice.*;
import org.eqasim.jakarta.mode_choice.parameters.*;
import org.matsim.core.config.*;
import org.matsim.core.controler.*;
import org.matsim.core.controler.corelisteners.ControlerDefaultCoreListenersModule;
import org.matsim.core.scenario.*;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

class JakartaReleaseConfigTest {
    @org.junit.jupiter.api.io.TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"BaselineModel","scen_car_25pct","scen_mc_25pct","scen_carMc_25pct","scen_mcOdt_25pct","scen_all_25pct"})
    void everyReleaseConfigLoadsAndUsesTheApprovedGroupStrategiesAndPolicies(String scenario) throws Exception {
        var cmd=cli();var configurator=new JakartaConfigurator(cmd);
        Config config=ConfigUtils.loadConfig(CONFIG.resolve("jakarta_config_"+scenario+"_3_0_0.xml").toString());
        configurator.updateConfig(config);
        var settings=config.replanning().getStrategySettings();assertEquals(4,settings.size());
        for(String group:List.of("commuter","non_commuter")) {
            var groupSettings=settings.stream().filter(s->group.equals(s.getSubpopulation())).toList();assertEquals(2,groupSettings.size());
            var weights=groupSettings.stream().collect(Collectors.toMap(s->s.getStrategyName(),s->s.getWeight()));
            assertEquals(Map.of("DiscreteModeChoice",.1,"KeepLastSelected",.9),weights);
            assertTrue(groupSettings.stream().allMatch(s->s.getDisableAfter()==-1));
        }
        assertEquals("jakarta_population_subpop.xml.gz",config.plans().getInputFile());
        var behaviour=JakartaBehaviourConfigGroup.get(config);
        assertEquals("input/UtilityParams_Commuter_3_0_0.yml",behaviour.getCommuterModeParametersPath());
        assertEquals("input/UtilityParams_NonCommuter_3_0_0.yml",behaviour.getNonCommuterModeParametersPath());
        String feeder=(scenario.equals("scen_mcOdt_25pct")||scenario.equals("scen_all_25pct"))?"scen_mcOdt_25pct":"Baseline";
        assertEquals("input/FeederPolicy_"+feeder+"_3_0_0.yml",behaviour.getFeederPolicyParametersPath());
        String cost=switch(scenario) {case "scen_car_25pct","scen_mc_25pct","scen_carMc_25pct" -> "input/JktCostParams_"+scenario+".yml";case "scen_all_25pct" -> "input/JktCostParams_scen_carMc_25pct.yml";default -> null;};
        assertEquals(cost,EqasimConfigGroup.get(config).getCostParametersPath());assertNull(EqasimConfigGroup.get(config).getModeParametersPath());
        // Resolve deployment input/ paths against external source directory for read-only loading checks.
        for(String path:List.of(behaviour.getCommuterModeParametersPath(),behaviour.getNonCommuterModeParametersPath(),behaviour.getFeederPolicyParametersPath())) assertTrue(Files.isRegularFile(CONFIG.resolve(Path.of(path).getFileName())));
        behaviour.setCommuterModeParametersPath(CONFIG.resolve("UtilityParams_Commuter_3_0_0.yml").toString());
        behaviour.setNonCommuterModeParametersPath(CONFIG.resolve("UtilityParams_NonCommuter_3_0_0.yml").toString());
        behaviour.setFeederPolicyParametersPath(CONFIG.resolve("FeederPolicy_"+feeder+"_3_0_0.yml").toString());
        var module=new JakartaModeChoiceModule(cmd);
        assertEquals(-1.37,module.provideModeChoiceParameters(config,EqasimConfigGroup.get(config)).car.alpha_u);
        assertEquals(-3.5,module.provideNonCommuterParameters(config).jPT.generic.constant);
        assertEquals(feeder.equals("Baseline")?0:.25,module.provideFeederPolicyParameters(config).subsidyShare_mcodt);
    }
    @Test void policyAndLatentFieldsAreRejectedByBehaviourSchemas() throws Exception {
        for(String key:List.of("jPT.odt.per_km_mcodt","jPT.class1.constant","jPT.class4.cost")) {
            var command=cli("--non-commuter-mode-parameter:"+key,"1");
            assertThrows(IllegalStateException.class,()->JakartaParameterLoader.load(new JakartaNonCommuterParameters(),null,"non-commuter-mode-parameter",command));
            var c=cli("--commuter-mode-parameter:"+key,"1");
            assertThrows(IllegalStateException.class,()->JakartaParameterLoader.load(JakartaModeParameters.buildDefault(),null,"commuter-mode-parameter",c));
        }
        var config=ConfigUtils.createConfig();var eqasim=new EqasimConfigGroup();eqasim.setModeParametersPath("ambiguous.yml");
        var module=new JakartaModeChoiceModule(cli());
        assertThrows(IllegalArgumentException.class,()->module.provideModeChoiceParameters(config,eqasim));
    }
    @Test void productionModuleBindsBothParameterSetsAndAllModeAliasesWithoutRunningSimulation() throws Exception {
        var cmd=cli();var configurator=new JakartaConfigurator(cmd);var config=ConfigUtils.createConfig();configurator.updateConfig(config);
        var behaviour=JakartaBehaviourConfigGroup.get(config);
        behaviour.setCommuterModeParametersPath(CONFIG.resolve("UtilityParams_Commuter_3_0_0.yml").toString());
        behaviour.setNonCommuterModeParametersPath(CONFIG.resolve("UtilityParams_NonCommuter_3_0_0.yml").toString());
        var eq=EqasimConfigGroup.get(config);
        eq.setCostModel("car",JakartaModeChoiceModule.CAR_COST_MODEL_NAME);
        eq.setCostModel("pt",JakartaModeChoiceModule.PT_COST_MODEL_NAME);
        eq.setCostModel("motorcycle",JakartaModeChoiceModule.MOTORCYCLE_COST_MODEL_NAME);
        eq.setCostModel("mcodt",JakartaModeChoiceModule.MCODT_COST_MODEL_NAME);
        eq.setCostModel("carodt",JakartaModeChoiceModule.CARODT_COST_MODEL_NAME);
        config.transit().setUseTransit(true);
        config.controller().setOutputDirectory(temp.resolve("binding-test").toString());
        var scenario=ScenarioUtils.createScenario(config);
        AbstractModule standard=new AbstractModule(){ public void install(){
            install(new NewControlerModule());install(new ControlerDefaultCoreListenersModule());
            install(new ControlerDefaultsModule());install(new ScenarioByInstanceModule(scenario));
        }};
        AbstractModule overrides=AbstractModule.emptyModule();
        for(var module:configurator.getModules(config)) overrides=AbstractModule.override(List.of(overrides),module);
        var injector=org.matsim.core.controler.Injector.createInjector(config,AbstractModule.override(List.of(standard),overrides));
        assertEquals(-1.37,injector.getInstance(JakartaModeParameters.class).car.alpha_u);
        assertEquals(-.514,injector.getInstance(JakartaNonCommuterParameters.class).car.alpha_u);
        var b=injector.getInstance(JakartaBehaviour.class);assertSame(b,injector.getInstance(JakartaBehaviour.class));
        Map<String,Provider<UtilityEstimator>> estimators=injector.getInstance(Key.get(new TypeLiteral<Map<String,Provider<UtilityEstimator>>>(){}));
        for(String alias:List.of("jCarEstimator","jPTEstimator","jWalkEstimator","jMotorcycleEstimator","jMcodtEstimator","jCarodtEstimator")) assertNotNull(estimators.get(alias).get());
    }
}
