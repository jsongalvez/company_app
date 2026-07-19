# Javalin Framework Instructions

Javalin 7.2.2 — lightweight web framework for Java and Kotlin, built on Jetty 12.

## Project setup

- Requires Java 17+
- Dependency: `io.javalin:javalin:7.2.2`
- Bundle (includes Jackson, Logback, testing): `io.javalin:javalin-bundle:7.2.2`
- SLF4J is the only required dependency — add Logback or another implementation for logging

## Core pattern

All configuration (routes, plugins, lifecycle) goes inside `Javalin.create { config -> ... }`:

```kotlin
fun main() {
    val app = Javalin.create { config ->
        config.routes.get("/") { ctx -> ctx.result("Hello World") }
    }.start(7070)
}
```

## Routing

Routes are defined via `config.routes`:

```kotlin
config.routes.get("/users") { ctx -> ctx.json(userDao.getAll()) }
config.routes.get("/users/{id}") { ctx ->
    val id = ctx.pathParamAsClass("id", Int::class.java).get()
    ctx.json(userDao.getById(id))
}
config.routes.post("/users") { ctx ->
    val user = ctx.bodyAsClass(User::class.java)
    userDao.create(user)
    ctx.status(201)
}
config.routes.put("/users/{id}") { ctx -> /* ... */ }
config.routes.patch("/users/{id}") { ctx -> /* ... */ }
config.routes.delete("/users/{id}") { ctx -> /* ... */ }
```

Supported methods: `get`, `post`, `put`, `patch`, `delete`, `query`, `head`, `options`.

Path parameters: `{param}` (no slashes) or `<param>` (allows slashes).
Wildcard: `/path/*` matches anything (but value cannot be extracted — use `<param>` instead).

Handlers have void return. Set response with:
- `ctx.result("text")` — plain text
- `ctx.json(object)` — JSON
- `ctx.html("<h1>Hi</h1>")` — HTML
- `ctx.status(code)` — HTTP status
- `ctx.redirect("/path")` — redirect
- `ctx.future(completableFuture)` — async

## Before/after handlers

```kotlin
config.routes.before { ctx -> /* runs before every request */ }
config.routes.after { ctx -> /* runs after every request */ }
config.routes.beforeMatched { ctx -> /* only if a route matched */ }
config.routes.afterMatched { ctx -> /* only if a route matched */ }
config.routes.before("/api/*") { ctx -> /* path-scoped */ }
```

**Important**: Routes MUST be configured inside `config.routes` during `Javalin.create {}`.
You cannot add routes after `.start()`.

## Validation

```kotlin
// Path parameter validation
val id = ctx.pathParamAsClass("id", Int::class.java)
    .check({ i -> i > 0 }, "ID must be positive")
    .get()

// Query parameter validation
val page = ctx.queryParamAsClass("page", Int::class.java)
    .getOrDefault(1)

// Body validation
val user = ctx.bodyValidator(User::class.java)
    .check({ u -> u.name != null }, "Name required")
    .get()
```

## WebSockets

```kotlin
config.routes.ws("/websocket") { ws ->
    ws.onConnect { ctx -> /* WsConnectContext */ }
    ws.onMessage { ctx -> ctx.send("Echo: " + ctx.message()) }
    ws.onClose { ctx -> /* WsCloseContext */ }
    ws.onError { ctx -> /* WsErrorContext */ }
}
```

## Server-Sent Events

```kotlin
config.routes.sse("/sse") { client ->
    client.sendEvent("message", "Hello SSE")
    client.onClose { /* cleanup */ }
    client.keepAlive()
}
```

## Exception and error mapping

```kotlin
config.error.exception(NotFoundException::class.java) { e, ctx ->
    ctx.status(404).result(e.message)
}
config.error.error(404) { ctx ->
    ctx.result("Page not found")
}
```

## Default HTTP responses

Throw typed exceptions for standard error responses (JSON body if client accepts JSON):

| Exception | Status |
|---|---|
| `BadRequestResponse` | 400 |
| `UnauthorizedResponse` | 401 |
| `ForbiddenResponse` | 403 |
| `NotFoundResponse` | 404 |
| `MethodNotAllowedResponse` | 405 |
| `ConflictResponse` | 409 |
| `GoneResponse` | 410 |
| `InternalServerErrorResponse` | 500 |

All extend `HttpResponseException`. Pass additional details:
```kotlin
throw BadRequestResponse("msg", mapOf("detail" to "value"))
```

## Access management

```kotlin
config.accessManager { handler, ctx, routeRoles ->
    val userRole = getUserRole(ctx)
    if (routeRoles.contains(userRole)) handler.handle(ctx)
    else ctx.status(403).result("Forbidden")
}
```

## Plugin configuration

```kotlin
Javalin.create { config ->
    // Bundled plugins
    config.bundledPlugins.enableCors { cors -> cors.addRule { it.anyHost() } }
    config.bundledPlugins.enableRouteOverview("/routes")
    config.bundledPlugins.enableDevLogging()

    // Static files
    config.staticFiles.add("/public", Location.CLASSPATH)
}
```

Available add-on artifacts: `javalin-rendering-{engine}` (JTE, Thymeleaf, etc.), `javalin-micrometer`, `javalin-ssl`.

## Handler groups (apiBuilder)

```kotlin
import io.javalin.apibuilder.ApiBuilder.*

config.routes.apiBuilder {
    path("/users") {
        get(UserController::getAllUsers)
        post(UserController::createUser)
        path("/{id}") {
            get(UserController::getUser)
            patch(UserController::updateUser)
            delete(UserController::deleteUser)
        }
    }
}
```

`CrudHandler` shortcut — maps `getAll`, `getOne`, `create`, `update`, `delete` automatically.

## File uploads

```kotlin
config.routes.post("/upload") { ctx ->
    val file = ctx.uploadedFile("myFile")
    // file.filename(), file.content() (InputStream), file.size(), file.contentType()
}
// Multiple files
ctx.uploadedFiles("files") // List<UploadedFile>
```

## Template rendering

Add a rendering engine artifact, e.g. `io.javalin:javalin-rendering-jte:7.2.2`:

```kotlin
config.fileRenderer(JavalinJte())
config.routes.get("/hello") { ctx ->
    ctx.render("hello.jte", mapOf("name" to "World"))
}
```

Available engines: `jte`, `thymeleaf`, `velocity`, `pebble`, `mustache`, `handlebars`, `freemarker`, `commonmark`.

## JSON mapper configuration

Jackson is the default. Customize it:

```kotlin
config.jsonMapper(JavalinJackson().updateMapper { mapper ->
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
})
```

To use a different mapper (e.g. Gson), implement the `JsonMapper` interface.

## Lifecycle events

```kotlin
config.events.serverStarting { /* starting up */ }
config.events.serverStarted { /* ready to serve */ }
config.events.serverStartFailed { /* failed to start */ }
config.events.serverStopping { /* shutting down */ }
config.events.serverStopped { /* stopped */ }
```

## SPA support

```kotlin
config.spaRoot.addFile("/", "/public/index.html")
// or with dynamic handler
config.spaRoot.addHandler("/") { ctx -> ctx.html("...") }
```

## Testing with JavalinTest

`javalin-testtools` provides `JavalinTest.test()` which starts a real server on a random port and provides an HTTP client:

```kotlin
import io.javalin.testtools.JavalinTest

val app = Javalin.create { config ->
    config.routes.get("/users") { ctx -> ctx.json(userService.getAll()) }
    config.routes.post("/users") { ctx ->
        val user = ctx.bodyAsClass(User::class.java)
        userService.create(user)
        ctx.status(201)
    }
}

@Test
fun `GET users returns 200`() {
    JavalinTest.test(app) { server, client ->
        assertThat(client.get("/users").code).isEqualTo(200)
    }
}

@Test
fun `POST users creates user`() {
    JavalinTest.test(app) { server, client ->
        val response = client.post("/users", User("Alice"))
        assertThat(response.code).isEqualTo(201)
    }
}
```

The client supports `get()`, `post()`, `put()`, `patch()`, `delete()` — all return an OkHttp `Response` with `.code` and `.body.string()`. Each test gets a fresh server instance on a random port.

## Context methods quick reference

**Request info:**
- `ctx.body()` — request body as string
- `ctx.bodyAsClass(MyClass::class.java)` — deserialize JSON body
- `ctx.pathParam("id")` — path parameter
- `ctx.queryParam("name")` — query parameter
- `ctx.formParam("field")` — form parameter
- `ctx.header("X-Custom")` — request header
- `ctx.cookie("name")` — cookie value
- `ctx.uploadedFile("file")` — single uploaded file
- `ctx.uploadedFiles("files")` — multiple uploaded files
- `ctx.attribute("key", value)` / `ctx.attribute("key")` — request-scoped attributes
- `ctx.sessionAttribute("key")` — session attribute
- `ctx.method()`, `ctx.url()`, `ctx.ip()`, `ctx.contentType()` — request metadata

**Response:**
- `ctx.result("text")` — set text response
- `ctx.json(myObject)` — serialize to JSON response
- `ctx.html("<h1>Hi</h1>")` — set HTML response
- `ctx.status(201)` — set status code
- `ctx.header("X-Custom", "value")` — set response header
- `ctx.cookie("name", "value")` — set cookie
- `ctx.redirect("/path")` — redirect
- `ctx.render("template.html", model)` — render template
- `ctx.contentType("application/json")` — set content type

## Important Javalin 7 changes (common pitfalls)

1. **Routes MUST be inside `config.routes`** — you cannot add routes after `.start()`
2. **`app.start()` no longer returns `this`** — chain off `Javalin.create()` instead
3. **Template rendering is modular** — add `javalin-rendering-{engine}` artifacts explicitly
4. **Jetty 12** — if configuring Jetty directly, use Jetty 12 APIs
5. **Java 17+** is required
6. **Exception handlers**: use `config.error.exception()` (not `config.routes.exception()`)
