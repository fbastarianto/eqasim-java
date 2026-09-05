package org.eqasim.jakarta.routing;

import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.JakartaHomeSideRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import com.google.inject.Scopes;
import org.matsim.core.controler.AbstractModule;

/** Installs the shared startup/general and DMC PT routing restriction. */
public final class JakartaHomeSideRoutingModule extends AbstractModule {
    @Override
    public void install() {
        addPersonPrepareForSimAlgorithm().to(AnnotateHomeSideTripAttributes.class);

        bind(HomeSideRaptorDiagnostics.class).in(Scopes.SINGLETON);
        addControlerListenerBinding().to(HomeSideRaptorDiagnostics.class);

        // Registered after SwissRailRaptorModule, so this overriding module wins.
        // MATSim disables just-in-time Guice bindings, hence the explicit
        // concrete delegate binding after replacing the interface binding.
        bind(DefaultRaptorStopFinder.class);
        bind(RaptorStopFinder.class).to(JakartaHomeSideRaptorStopFinder.class);
    }
}
