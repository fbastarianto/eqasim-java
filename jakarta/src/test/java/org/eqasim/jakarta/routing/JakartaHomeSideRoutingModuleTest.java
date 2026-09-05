package org.eqasim.jakarta.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.JakartaHomeSideRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import org.eqasim.jakarta.JakartaConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.ControlerDefaultsModule;
import org.matsim.core.controler.NewControlerModule;
import org.matsim.core.controler.PersonPrepareForSimAlgorithm;
import org.matsim.core.controler.corelisteners.ControlerDefaultCoreListenersModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scenario.ScenarioByInstanceModule;

class JakartaHomeSideRoutingModuleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void configuratorRegistersHomeSideModuleAfterSwissRailRaptor() throws Exception {
        CommandLine commandLine = new CommandLine.Builder(new String[0]).build();
        JakartaConfigurator configurator = new JakartaConfigurator(commandLine);
        Config config = ConfigUtils.createConfig();
        configurator.updateConfig(config);

        List<AbstractModule> modules = configurator.getModules(config);
        int swissRailRaptorIndex = indexOf(modules, SwissRailRaptorModule.class);
        int homeSideIndex = indexOf(modules, JakartaHomeSideRoutingModule.class);

        assertTrue(swissRailRaptorIndex >= 0);
        assertTrue(homeSideIndex > swissRailRaptorIndex);
    }

    @Test
    void homeSideModuleOverridesTheDefaultStopFinderBinding() {
        Config config = ConfigUtils.createConfig(new SwissRailRaptorConfigGroup());
        config.transit().setUseTransit(true);
        config.controller().setOutputDirectory(temporaryDirectory.resolve("output").toString());
        Scenario scenario = ScenarioUtils.createScenario(config);

        AbstractModule standardModules = new AbstractModule() {
            @Override
            public void install() {
                install(new NewControlerModule());
                install(new ControlerDefaultCoreListenersModule());
                install(new ControlerDefaultsModule());
                install(new ScenarioByInstanceModule(scenario));
            }
        };
        // Mirror Controler.addOverridingModule(): SwissRailRaptor is installed
        // first, then Jakarta's module overrides its RaptorStopFinder binding.
        AbstractModule earlierOverrides = AbstractModule.override(
                List.of(AbstractModule.emptyModule()),
                new SwissRailRaptorModule());
        AbstractModule effectiveOverrides = AbstractModule.override(
                List.of(earlierOverrides),
                new JakartaHomeSideRoutingModule());
        AbstractModule effectiveModules = AbstractModule.override(
                List.of(standardModules),
                effectiveOverrides);
        Injector injector = org.matsim.core.controler.Injector.createInjector(config, effectiveModules);

        assertEquals(
                JakartaHomeSideRaptorStopFinder.class,
                injector.getInstance(RaptorStopFinder.class).getClass());

        Set<PersonPrepareForSimAlgorithm> prepareAlgorithms = injector.getInstance(
                Key.get(new TypeLiteral<Set<PersonPrepareForSimAlgorithm>>() {
                }));
        assertTrue(prepareAlgorithms.stream().anyMatch(AnnotateHomeSideTripAttributes.class::isInstance));
    }

    private static int indexOf(List<AbstractModule> modules, Class<?> type) {
        for (int index = 0; index < modules.size(); index++) {
            if (type.isInstance(modules.get(index))) {
                return index;
            }
        }
        return -1;
    }
}
