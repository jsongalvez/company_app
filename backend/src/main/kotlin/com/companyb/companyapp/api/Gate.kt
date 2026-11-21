package com.companyb.companyapp.api

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.http.Handler

/**
 * Who may call a route. Every route names its gate where it is declared, as in
 * `config.get(path, Gate.Before { Access.require(it, Act.SEE_BRANCH, ...) }) { ... }`, and
 * `RouteGateTest` fails for any route registered without one. What each gate lets each role do is
 * pinned per route in `backend/src/test/resources/route-access.txt` (`RouteAccessTest`).
 */
sealed interface Gate {
    /** Anyone, signed in or not. */
    data class Open(
        val why: String,
    ) : Gate

    /** Anyone signed in. The route only shows or changes the caller's own things, or only what they may see. */
    data class SignedIn(
        val why: String,
    ) : Gate

    /** The handler asks `Access` itself, once it has loaded the row that says which branch or day this is. */
    data class InHandler(
        val why: String,
    ) : Gate

    /** The caller may do [act] everywhere; unlike [Before], a test can read which act the route asks for. */
    data class Can(
        val act: Act,
    ) : Gate

    /** [check] runs before the handler and throws to refuse. */
    class Before(
        val check: (Context) -> Unit,
    ) : Gate
}

/** A route's handler with its [gate] in front. `RouteGateTest` finds every route by this type. */
class GatedHandler(
    val gate: Gate,
    private val handler: Handler,
) : Handler {
    override fun handle(ctx: Context) {
        when (gate) {
            is Gate.Before -> gate.check(ctx)
            is Gate.Can -> Access.require(ctx, gate.act)
            else -> Unit
        }
        handler.handle(ctx)
    }
}

fun JavalinConfig.get(
    path: String,
    gate: Gate,
    handler: Handler,
) {
    routes.get(path, GatedHandler(gate, handler))
}

fun JavalinConfig.post(
    path: String,
    gate: Gate,
    handler: Handler,
) {
    routes.post(path, GatedHandler(gate, handler))
}

fun JavalinConfig.put(
    path: String,
    gate: Gate,
    handler: Handler,
) {
    routes.put(path, GatedHandler(gate, handler))
}

fun JavalinConfig.patch(
    path: String,
    gate: Gate,
    handler: Handler,
) {
    routes.patch(path, GatedHandler(gate, handler))
}

fun JavalinConfig.delete(
    path: String,
    gate: Gate,
    handler: Handler,
) {
    routes.delete(path, GatedHandler(gate, handler))
}
