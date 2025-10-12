package org.eqasim.jakarta.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.eqasim.jakarta.mode_choice.utilities.predictors.JakartaPtPredictor;

public class JakartaFeederModule extends AbstractModule {
    @Override
    protected void configure() {
        // ✅ Keep this only if we need to make the custom predictor injectable.
        bind(JakartaPtPredictor.class).in(Scopes.SINGLETON);

        // ❌ Remove or comment out any line like:
        // bind(PtPredictor.class).to(JakartaPtPredictor.class);
    }
}
