package org.eqasim.jakarta.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HomeSideTripAttributesTest {
    @Test
    void usesExactCaseInsensitiveHomePredicate() {
        assertTrue(HomeSideTripAttributes.isHome("home"));
        assertTrue(HomeSideTripAttributes.isHome("HOME"));
        assertTrue(HomeSideTripAttributes.isHome("Home"));

        assertFalse(HomeSideTripAttributes.isHome("work"));
        assertFalse(HomeSideTripAttributes.isHome(null));
        assertFalse(HomeSideTripAttributes.isHome("home_other"));
    }
}
