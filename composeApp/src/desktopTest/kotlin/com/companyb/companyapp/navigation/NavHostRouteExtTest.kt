package com.companyb.companyapp.navigation

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalSerializationApi::class)
class NavHostRouteExtTest {
    @Test
    fun serialNameFromPatternStripsPathAndQueryArgs() {
        assertEquals(
            "com.companyb.companyapp.navigation.Route.Login",
            serialNameFromPattern("com.companyb.companyapp.navigation.Route.Login"),
        )
        assertEquals(
            "com.companyb.companyapp.navigation.Route.ClientDetail",
            serialNameFromPattern("com.companyb.companyapp.navigation.Route.ClientDetail/{clientId}"),
        )
        assertEquals(
            "com.companyb.companyapp.navigation.Route.AuditLogHistory",
            serialNameFromPattern(
                "com.companyb.companyapp.navigation.Route.AuditLogHistory/{tableName}/{recordId}",
            ),
        )
        assertEquals(
            "com.companyb.companyapp.navigation.Route.SessionDetail",
            serialNameFromPattern(
                "com.companyb.companyapp.navigation.Route.SessionDetail/{sessionId}?row={row}",
            ),
        )
    }

    @Test
    fun routeMapCoversEverySealedSubclass() {
        // sealedSubclasses is recursive — direct AND indirect subclasses
        val subclasses = Route::class.sealedSubclasses
        assertEquals(
            subclasses.size,
            ROUTES_BY_SERIAL_NAME.size,
            "route serial-name map must cover every Route subclass — a new subclass " +
                "without a map entry silently degrades the shell (currentRoute() returns null)",
        )
        for (subclass in subclasses) {
            val serialName = subclass.serializer().descriptor.serialName
            assertTrue(
                ROUTES_BY_SERIAL_NAME.containsKey(serialName),
                "missing map entry for $serialName",
            )
        }
    }
}
