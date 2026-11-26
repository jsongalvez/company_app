package com.companyb.companyapp.api

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.http.Handler

/**
 * Who may call a route. Every route names its gate where it is declared, as in
 * `config.get(path, Gate.At(Act.SEE_BRANCH) { Place.Branch(...) }) { ... }`, and
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

    /** The caller may do [act] everywhere. */
    data class Can(
        val act: Act,
        val message: String? = null,
    ) : Gate

    /**
     * The caller may do [act] at the place [where] finds. [where] only finds the place, and may answer 404
     * for a missing row; [GatedHandler] asks `Access` for [act] itself, so the act a test reads is the one checked.
     */
    class At(
        val act: Act,
        val message: String? = null,
        val where: (Context) -> Place,
    ) : Gate

    /**
     * The act depends on the request: [pick] reads it and names what the caller must do, and where, in the
     * order to check. Every picked act is one of [acts], so a test reads which acts the route can ask for;
     * [GatedHandler] fails the request when [pick] names any other.
     */
    class Pick(
        val acts: Set<Act>,
        val pick: (Context) -> List<Need>,
    ) : Gate

    /** One act a [Pick] asks for, at its place. */
    data class Need(
        val act: Act,
        val place: Place,
    )
}

/** A route's handler with its [gate] in front. `RouteGateTest` finds every route by this type. */
class GatedHandler(
    val gate: Gate,
    private val handler: Handler,
) : Handler {
    override fun handle(ctx: Context) {
        when (gate) {
            is Gate.Can -> {
                require(ctx, gate.act, Place.Everywhere, gate.message)
            }

            is Gate.At -> {
                require(ctx, gate.act, gate.where(ctx), gate.message)
            }

            is Gate.Pick -> {
                gate.pick(ctx).forEach { need ->
                    check(need.act in gate.acts) { "${need.act} is not one of the acts this route declares" }
                    require(ctx, need.act, need.place, null)
                }
            }

            else -> {
                Unit
            }
        }
        handler.handle(ctx)
    }

    private fun require(
        ctx: Context,
        act: Act,
        place: Place,
        message: String?,
    ) = if (message == null) Access.require(ctx, act, place) else Access.require(ctx, act, place, message)
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
