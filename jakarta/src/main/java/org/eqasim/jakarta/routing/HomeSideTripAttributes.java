package org.eqasim.jakarta.routing;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.utils.objectattributes.attributable.Attributes;

/**
 * Shared metadata contract for the Jakarta PT private-motorcycle home-side rule.
 */
public final class HomeSideTripAttributes {
    public static final String SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE =
            "org.eqasim.jakarta.routing.homeSide.substantiveOriginActivityType";
    public static final String SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE =
            "org.eqasim.jakarta.routing.homeSide.substantiveDestinationActivityType";

    private HomeSideTripAttributes() {
    }

    public static boolean isHome(String type) {
        return type != null && "home".equalsIgnoreCase(type);
    }

    public static void annotate(Attributes tripAttributes, Activity origin, Activity destination) {
        putOrRemove(tripAttributes, SUBSTANTIVE_ORIGIN_ACTIVITY_TYPE, origin.getType());
        putOrRemove(tripAttributes, SUBSTANTIVE_DESTINATION_ACTIVITY_TYPE, destination.getType());
    }

    private static void putOrRemove(Attributes attributes, String key, String value) {
        if (value == null) {
            attributes.removeAttribute(key);
        } else {
            attributes.putAttribute(key, value);
        }
    }
}
