package com.companyb.companyapp.api

import com.companyb.companyapp.registerAllRoutes
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.event.HandlerMetaInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every route names its gate where it is declared (`api/Gate.kt`), so no access rule hides in a filter elsewhere. */
class RouteGateTest {
    @Test
    fun `every route names its gate`() {
        assertEquals(emptyList(), problems(::registerAllRoutes), FIX)
    }

    @Test
    fun `a route without a gate is caught`() {
        val found =
            problems { config ->
                config.get("/api/gated", Gate.SignedIn("test")) { it.result("ok") }
                config.routes.get("/api/bare") { it.result("ok") }
                config.routes.before("/api/gated") { }
            }
        assertEquals(listOf("GET /api/bare has no gate", "BEFORE /api/gated is a filter"), found)
    }

    @Test
    fun `routes that hide their action from this test only go down`() {
        val hidden = gates(::registerAllRoutes).count { it is Gate.Before || it is Gate.InHandler }
        assertEquals(HIDDEN_ACTION_ROUTES, hidden, HIDDEN_FIX)
    }

    @Test
    fun `a new route that hides its action is counted`() {
        val gates =
            gates { config ->
                config.get("/api/open", Gate.SignedIn("test")) { it.result("ok") }
                config.get("/api/before", Gate.Before { }) { it.result("ok") }
                config.get("/api/handler", Gate.InHandler("test")) { it.result("ok") }
            }
        assertEquals(2, gates.count { it is Gate.Before || it is Gate.InHandler })
    }

    private fun gates(register: (JavalinConfig) -> Unit): List<Gate> {
        val added = mutableListOf<HandlerMetaInfo>()
        Javalin.create { cfg ->
            cfg.events.handlerAdded { added += it }
            register(cfg)
        }
        return added.mapNotNull { (it.handler as? GatedHandler)?.gate }
    }

    private fun problems(register: (JavalinConfig) -> Unit): List<String> {
        val added = mutableListOf<HandlerMetaInfo>()
        Javalin.create { cfg ->
            cfg.events.handlerAdded { added += it }
            register(cfg)
        }
        return added.mapNotNull {
            when {
                it.httpMethod.isHttpMethod && it.handler !is GatedHandler -> "${it.httpMethod} ${it.path} has no gate"
                !it.httpMethod.isHttpMethod -> "${it.httpMethod} ${it.path} is a filter"
                else -> null
            }
        }
    }

    private companion object {
        /** Routes whose gate is a lambda or an in-handler check: no test can read the action they ask for. */
        const val HIDDEN_ACTION_ROUTES = 127
        const val HIDDEN_FIX =
            "Gate a new route with an action this test can read, and lower HIDDEN_ACTION_ROUTES when a route stops " +
                "hiding its action. The policy table (docs/rebuild/plan.md) replaces these gates."
        const val FIX =
            "Declare each route with config.get/post/put/patch/delete(path, gate) from api/Gate.kt, and put its " +
                "access check in Gate.Before instead of a config.routes.before filter."
    }
}
