package org.eqasim.jakarta.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.ParameterDefinition;

/** Shared startup-loaded monetary policy, independent of behavioural population. */
public final class JakartaFeederPolicyParameters implements ParameterDefinition {
    public double base_mcodt = 0;
    public double per_km_mcodt = 1.9;
    public double subsidyShare_mcodt = 0;
    public double maxDiscountMU_mcodt = 5;
}
