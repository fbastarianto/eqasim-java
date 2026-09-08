package org.eqasim.jakarta.mode_choice.parameters;

import java.io.File;
import org.eqasim.core.simulation.mode_choice.ParameterDefinition;
import org.matsim.core.config.CommandLine;

/** Startup loading only. No parameter writes occur during person/candidate evaluation. */
public final class JakartaParameterLoader {
    private JakartaParameterLoader() { }
    public static void rejectLegacyOverrides(CommandLine commandLine) {
        for (String option : commandLine.getAvailableOptions()) {
            if (option.startsWith("mode-parameter:"))
                throw new IllegalArgumentException("Ambiguous legacy override " + option
                    + "; use commuter-mode-parameter: or non-commuter-mode-parameter:");
        }
    }
    public static <T extends ParameterDefinition> T load(T defaults, String path, String prefix, CommandLine commandLine) {
        rejectLegacyOverrides(commandLine);
        if (path != null && !"null".equals(path)) ParameterDefinition.applyFile(new File(path), defaults);
        ParameterDefinition.applyCommandLine(prefix, commandLine, defaults);
        return defaults;
    }
}
