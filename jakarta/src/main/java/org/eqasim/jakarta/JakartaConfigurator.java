package org.eqasim.jakarta;

import org.eqasim.core.simulation.EqasimConfigurator;
import org.eqasim.jakarta.mode_choice.JakartaModeChoiceModule;
import org.matsim.core.config.CommandLine;

public class JakartaConfigurator extends EqasimConfigurator {
    public JakartaConfigurator(CommandLine cmd) {
        super (cmd);

        registerModule(new JakartaModeChoiceModule(cmd));
    }
}
