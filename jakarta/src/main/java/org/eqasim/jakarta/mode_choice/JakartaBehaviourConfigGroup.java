package org.eqasim.jakarta.mode_choice;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

/** Paths follow the existing Jakarta convention: relative to process working directory. */
public final class JakartaBehaviourConfigGroup extends ReflectiveConfigGroup {
    public static final String GROUP_NAME = "jakartaBehaviour";
    private String commuterModeParametersPath;
    private String nonCommuterModeParametersPath;
    private String feederPolicyParametersPath;
    public JakartaBehaviourConfigGroup() { super(GROUP_NAME); }
    public static JakartaBehaviourConfigGroup get(Config config) {
        return ConfigUtils.addOrGetModule(config, JakartaBehaviourConfigGroup.class);
    }
    @StringGetter("commuterModeParametersPath")
    public String getCommuterModeParametersPath() { return commuterModeParametersPath; }
    @StringSetter("commuterModeParametersPath")
    public void setCommuterModeParametersPath(String value) { commuterModeParametersPath = value; }
    @StringGetter("nonCommuterModeParametersPath")
    public String getNonCommuterModeParametersPath() { return nonCommuterModeParametersPath; }
    @StringSetter("nonCommuterModeParametersPath")
    public void setNonCommuterModeParametersPath(String value) { nonCommuterModeParametersPath = value; }
    @StringGetter("feederPolicyParametersPath")
    public String getFeederPolicyParametersPath() { return feederPolicyParametersPath; }
    @StringSetter("feederPolicyParametersPath")
    public void setFeederPolicyParametersPath(String value) { feederPolicyParametersPath = value; }
}
