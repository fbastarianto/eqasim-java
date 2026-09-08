package org.eqasim.jakarta.mode_choice.behaviour;

/** Neutral routed features: minutes, Euclidean km, and shared monetary units. */
public record JakartaTripFeatures(double time, double access, double waiting, double distance, double cost) { }
