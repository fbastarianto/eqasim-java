package org.eqasim.jakarta;


import org.eqasim.core.analysis.DistanceUnit;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.EqasimConfigurator;
import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.matsim.api.core.v01.Scenario;

import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.jakarta.eventhandling.MyEventHandler1;
//import org.eqasim.jakarta.eventhandling.MyEventHandler2;
//import org.eqasim.jakarta.eventhandling.MyEventHandler3;
//import org.eqasim.jakarta.eventhandling.MyEventHandler4;
//import org.eqasim.jakarta.eventhandling.MyEventHandler5;
//import org.eqasim.jakarta.eventhandling.MyEventHandler6;
//import org.eqasim.jakarta.eventhandling.MyEventHandler7;
import org.eqasim.jakarta.mode_choice.JakartaModeChoiceModule;
//import org.eqasim.jakarta.roadpricing.JakartaMcRoadPricingModule;

import org.matsim.core.controler.AbstractModule;

//import org.matsim.roadpricing.RoadPricingModule;


public class RunSimulation {
	
	public static final String outputDirectory = "output/example7" ;
	
	static public void main(String[] args) throws ConfigurationException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path") //
				.allowPrefixes("commuter-mode-parameter", "non-commuter-mode-parameter", "feeder-policy-parameter", "cost-parameter", "mode-parameter") //
				.build();

		org.eqasim.jakarta.mode_choice.parameters.JakartaParameterLoader.rejectLegacyOverrides(cmd);
		JakartaConfigurator configurator = new JakartaConfigurator(cmd);
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"));
		configurator.updateConfig(config);		//EqasimConfigurator.getConfigGroups());
		EqasimConfigGroup.get(config).setAnalysisInterval(1);
		EqasimConfigGroup.get(config).setDistanceUnit(DistanceUnit.foot);
		cmd.applyConfiguration(config);

		Scenario scenario = ScenarioUtils.createScenario(config);
		configurator.configureScenario(scenario); //EqasimConfigurator.configureScenario(scenario);
		ScenarioUtils.loadScenario(scenario);
        for (var person : scenario.getPopulation().getPersons().values()) {
            org.eqasim.jakarta.mode_choice.behaviour.JakartaSubpopulation.select(person);
            org.eqasim.jakarta.mode_choice.behaviour.JakartaPersonData.read(person, true);
        }
		configurator.adjustScenario(scenario); //EqasimConfigurator.adjustScenario(scenario);
		
		EqasimConfigGroup eqasimConfig = (EqasimConfigGroup) config.getModules().get(EqasimConfigGroup.GROUP_NAME);
		eqasimConfig.setEstimator("walk", "jWalkEstimator");
		eqasimConfig.setEstimator("pt", "jPTEstimator");
		eqasimConfig.setEstimator("motorcycle", "jMotorcycleEstimator");
		eqasimConfig.setEstimator("car", "jCarEstimator");
		eqasimConfig.setEstimator("carodt", "jCarodtEstimator");
		eqasimConfig.setEstimator("mcodt", "jMcodtEstimator");
		
		//DiscreteModeChoiceConfigGroup dmcConfig = DiscreteModeChoiceConfigGroup.getOrCreate(config);
		
		//List<String> availableModes = new ArrayList<>(dmcConfig.getCarModeAvailabilityConfig().getAvailableModes());
		//availableModes.add("car_odt");
		//dmcConfig.getCarModeAvailabilityConfig().setAvailableModes(availableModes);
		
		//config.controler().setOutputDirectory(outputDirectory); // NA in github
		
		Controler controller = new Controler(scenario);
		configurator.configureController(controller);//EqasimConfigurator.configureController(controller);
		//controller.addOverridingModule(new JakartaMcRoadPricingModule());
		// add the events handlers
		controller.addOverridingModule(new AbstractModule(){
			@Override public void install() {
				this.addEventHandlerBinding().toInstance( new MyEventHandler1() );
		//		this.addEventHandlerBinding().toInstance( new MyEventHandler2() );
		//		this.addEventHandlerBinding().toInstance( new MyEventHandler3() );
		//		this.addEventHandlerBinding().toInstance( new MyEventHandler4() );
		//		this.addEventHandlerBinding().toInstance( new MyEventHandler5() );
		//		this.addEventHandlerBinding().toInstance( new MyEventHandler6() );
		//		this.addEventHandlerBinding().toInstance( new MyEventHandler7() );
					}
			  	
				
				});
		// ensure PT predictor accepts motorcycle/mcodt/carodt feeders
		//controller.addOverridingModule(new org.eqasim.jakarta.guice.JakartaFeederModule());

		//controller.addOverridingModule(new RoadPricingModule());
		//controller.addOverridingModule(new MyEventHandler1());
		controller.addOverridingModule(new EqasimAnalysisModule());
		controller.addOverridingModule(new EqasimAnalysisModule());
		controller.addOverridingModule(new EqasimModeChoiceModule());
		controller.addOverridingModule(new JakartaModeChoiceModule(cmd));
		controller.addOverridingModule(new EqasimAnalysisModule());
		controller.run();
	}
}