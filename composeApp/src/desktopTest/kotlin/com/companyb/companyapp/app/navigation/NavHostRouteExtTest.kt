package com.companyb.companyapp.app.navigation

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalSerializationApi::class)
class NavHostRouteExtTest {
    @Test
    fun serialNameFromPatternStripsPathAndQueryArgs() {
        assertEquals(
            "com.companyb.companyapp.app.navigation.Route.Login",
            serialNameFromPattern("com.companyb.companyapp.app.navigation.Route.Login"),
        )
        assertEquals(
            "com.companyb.companyapp.app.navigation.Route.ClientDetail",
            serialNameFromPattern("com.companyb.companyapp.app.navigation.Route.ClientDetail/{clientId}"),
        )
        assertEquals(
            "com.companyb.companyapp.app.navigation.Route.AuditLogHistory",
            serialNameFromPattern(
                "com.companyb.companyapp.app.navigation.Route.AuditLogHistory/{tableName}/{recordId}",
            ),
        )
        assertEquals(
            "com.companyb.companyapp.app.navigation.Route.SessionDetail",
            serialNameFromPattern(
                "com.companyb.companyapp.app.navigation.Route.SessionDetail/{sessionId}?row={row}",
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

    @Test
    fun publicRoutesStayChromeFree() {
        // #487 — every public route renders no drawer, no badge poll: the shell
        // classifier must stay false for all four (Login/BranchSelect precedent
        // plus AcceptInvite/ForgotPassword).
        assertFalse((Route.Login as? Route).isPostClockIn())
        assertFalse((Route.BranchSelect as? Route).isPostClockIn())
        assertFalse((Route.AcceptInvite as? Route).isPostClockIn())
        assertFalse((Route.ForgotPassword as? Route).isPostClockIn())
        val noRoute: Route? = null
        assertFalse(noRoute.isPostClockIn())
    }

    @Test
    fun postClockInRoutesKeepChrome() {
        assertTrue((Route.Dashboard() as? Route).isPostClockIn())
        assertTrue((Route.Clients as? Route).isPostClockIn())
        assertTrue((Route.Finance as? Route).isPostClockIn())
        assertTrue((Route.Notifications as? Route).isPostClockIn())
        assertTrue((Route.SessionCreate as? Route).isPostClockIn())
        assertTrue((Route.Profile as? Route).isPostClockIn())
    }
}
