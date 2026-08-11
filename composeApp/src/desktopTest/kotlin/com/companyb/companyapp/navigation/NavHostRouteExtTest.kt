package com.companyb.companyapp.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavHostRouteExtTest {
    @Test
    fun routeMapCoversEverySealedSubclass() {
        val subclasses = Route::class.sealedSubclasses
        assertEquals(
            subclasses.size,
            ROUTES_BY_SERIAL_NAME.size,
            "route serial-name map must cover every Route subclass — a new subclass " +
                "without a map entry silently degrades the shell (currentRoute() returns null)",
        )
        for (subclass in subclasses) {
            val serialName = subclass.qualifiedName
            assertTrue(
                ROUTES_BY_SERIAL_NAME.containsKey(serialName),
                "missing map entry for $serialName",
            )
        }
    }
}
