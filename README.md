# Hello World API

A small Spring Boot web service with a single endpoint, `GET /hello-world`.

| Request | Status | Body |
|---|---|---|
| `GET /hello-world?name=alice` (first letter A–M) | `200 OK` | `{"message": "Hello Alice"}` |
| `GET /hello-world?name=zoe` (first letter N–Z) | `400 Bad Request` | `{"error": "Invalid Input"}` |
| `GET /hello-world` or `?name=` (missing / empty) | `400 Bad Request` | `{"error": "Invalid Input"}` |

## Requirements

- Java 21
- Maven is **not** required: the included Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads the
  correct version on first use.
- Docker (optional, only to run the container image)
- Apache Tomcat **10.1 or later** (optional, only to deploy the WAR; tested on Tomcat 11.0.29).
  Tomcat 9 and older do not work: Spring Boot 3 uses the `jakarta.servlet` APIs.

## Running the application

The build produces one file, `target/hello-world-api.war`, that can either run on its own
(with an embedded Tomcat) or be deployed to an external Tomcat.

```bash
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd` instead of `./mvnw`. The service starts on port 8080:

```bash
curl -i "http://localhost:8080/hello-world?name=alice"
```

To run the packaged WAR on its own instead:

```bash
./mvnw clean package
java -jar target/hello-world-api.war
```

Use `--server.port=9090` (or the `SERVER_PORT` environment variable) to change the port.

### From IntelliJ IDEA

Use the shared run configuration **HelloWorldApplication** (stored in
[.run/HelloWorldApplication.run.xml](.run/HelloWorldApplication.run.xml)). It has
*Add dependencies with "provided" scope to classpath* enabled, which the embedded Tomcat needs
because `spring-boot-starter-tomcat` is `provided` (see below).

If you start the app another way (for example the gutter icon creates a new temporary
configuration) and it fails with *"Unable to start ServletWebServerApplicationContext due to
missing ServletWebServerFactory bean"*, open the run configuration, choose
**Modify options → Add dependencies with "provided" scope to classpath**, and run again.

### Deploying to Apache Tomcat

1. Build the WAR:

   ```bash
   ./mvnw clean package
   ```

The WAR keeps its name, `hello-world-api.war`, and is still served at the root path, so the
endpoint has the same URL as when running from IntelliJ or with `java -jar`. Normally Tomcat
uses the file name as the URL prefix (`/hello-world-api/...`); a small context file overrides
that.

**One-time setup** (`$CATALINA_HOME` is the Tomcat folder):

1. Move Tomcat's own welcome application out of `webapps`, because it also uses the root
   path. Move it outside `webapps` rather than renaming it in place: Tomcat deploys every
   folder inside `webapps`, so `webapps/ROOT.bak` would become `/ROOT.bak`.

   ```bash
   mv "$CATALINA_HOME/webapps/ROOT" "$CATALINA_HOME/ROOT.bak"
   ```

2. Create a `deployments` folder **outside** `webapps`. The WAR must not be inside `webapps`,
   otherwise Tomcat deploys it a second time at `/hello-world-api`.

   ```bash
   mkdir "$CATALINA_HOME/deployments"
   ```

3. Create `$CATALINA_HOME/conf/Catalina/localhost/ROOT.xml`. The file name `ROOT.xml` maps it
   to the root path, and `docBase` points at the WAR:

   ```xml
   <Context docBase="D:\dev\workspace\apache-tomcat-11.0.29\deployments\hello-world-api.war" />
   ```

**Deploying a build** (first time and every update):

1. Build the WAR:

   ```bash
   ./mvnw clean package
   ```

2. **Stop Tomcat** (`bin/shutdown.bat` on Windows, `bin/shutdown.sh` on Linux/macOS).
3. Copy the WAR over the old one:

   ```bash
   cp target/hello-world-api.war "$CATALINA_HOME/deployments/hello-world-api.war"
   ```

4. **Start Tomcat** (`bin/startup.bat` / `bin/startup.sh`). It unpacks the new WAR into
   `webapps/ROOT`; don't edit or deploy anything there yourself.
5. Call the endpoint:

   ```bash
   curl -i "http://localhost:8080/hello-world?name=alice"
   ```

Why stop Tomcat first: on Windows, Tomcat cannot replace the unpacked copy of the
application while it is running, because the open jar files are locked. Replacing the WAR
while Tomcat runs leaves a half-deleted `webapps/ROOT` and the API returns 404. If that
happens, stop Tomcat, delete `webapps/ROOT`, and start it again.

Only one Tomcat can run at a time, and Tomcat and the app started from IntelliJ or
`java -jar` both use port 8080, so run only one of them at a time. Starting a second one fails
with `java.net.BindException: Address already in use` in `logs/catalina.<date>.log`.

How the WAR supports both modes:

- `HelloWorldApplication` extends `SpringBootServletInitializer`, which is how an external
  Tomcat starts the Spring application.
- `spring-boot-starter-tomcat` has `provided` scope, so the embedded Tomcat jars are kept out
  of `WEB-INF/lib` (where they would clash with the server's own classes). Spring Boot puts
  them in `WEB-INF/lib-provided`, which is only used by `java -jar`.

In an external Tomcat the `server.*` settings in `application.properties` (port, graceful
shutdown) do not apply; they are configured in Tomcat's own `conf/server.xml`. The health
endpoints are at the same URLs as in the other modes (`/actuator/health`).

### With Docker

```bash
docker build -t hello-world-api .
docker run --rm -p 8080:8080 hello-world-api
```

The image is built in two stages (JDK to build, JRE to run) and runs as a non-root user.

### Health checks

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Overall status: `{"status":"UP"}` |
| `GET /actuator/health/liveness` | Liveness probe (is the process alive?) |
| `GET /actuator/health/readiness` | Readiness probe (can it receive traffic?) |

No other actuator endpoints are exposed. Configuration is in
[application.properties](src/main/resources/application.properties), which also enables
graceful shutdown (in-flight requests get up to 20 seconds to finish) and Java 21 virtual threads.

### API documentation (OpenAPI)

| URL | Contents |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI: browse the API and try requests in the browser |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3 specification (JSON); `/v3/api-docs.yaml` for YAML |

The specification is generated from the code by
[springdoc-openapi](https://springdoc.org/), with the `name` parameter, both response bodies
and examples described by annotations on `HelloWorldController`. `OpenApiDocumentationTest`
checks the generated document so it cannot drift from the real behaviour. Only `/hello-world`
is documented; health endpoints are left out.

Public API docs are often turned off in production. Set the environment variables
`SPRINGDOC_API_DOCS_ENABLED=false` and `SPRINGDOC_SWAGGER_UI_ENABLED=false` to disable both
without rebuilding.

### Logging

Logs go to the console (in an external Tomcat: Tomcat's `logs/catalina.<date>.log`).

- **One summary line per request** with method, path, status and duration:

  ```
  INFO [7f3c9a1e-...] 1234 --- [hello-world-api] [nio-8080-exec-1] o.e.h.web.RequestLoggingFilter : GET /hello-world -> 400 (2 ms)
  ```

- **The reason for every 400**, at INFO and without a stack trace:
  `Rejected request: Name does not start with a letter from A to M`.
- **A request ID on every line** written while handling a request (`[7f3c9a1e-...]` above).
  The ID is also returned in the `X-Request-Id` response header, so a client can quote it
  when reporting a problem. If the client sends its own `X-Request-Id` (up to 64 letters,
  digits, `.`, `_` or `-`), that value is used instead; anything else is replaced with a new
  UUID so it cannot inject text into the logs.

Deliberately **not** logged:

- **Names and query strings.** A person's name is personal data, so only the path and the
  rejection reason are logged.
- **Health checks** (`/actuator/health/**`), which load balancers call every few seconds.
- **Stack traces for 400s**, which are normal client mistakes. Unexpected server errors are
  logged at ERROR with a stack trace.

For log platforms such as ELK, Loki or CloudWatch, switch to JSON lines (Elastic Common
Schema) without rebuilding; the request ID becomes a `requestId` field:

```bash
LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs java -jar target/hello-world-api.war
```

Log levels can be changed the same way, e.g. `LOGGING_LEVEL_ORG_EXAMPLE_HELLOWORLD=WARN`
hides the per-request lines.

## Running the tests

```bash
./mvnw test
```

There are five test classes:

| Test class | Type | What it covers |
|---|---|---|
| `GreetingServiceTest` | Unit | Name rules: alphabet boundaries, case, whitespace, non-English characters, length limits |
| `HelloWorldControllerTest` | Web layer (MockMvc) | Status codes, exact JSON shape, JSON escaping, repeated parameters, 404/405/406 |
| `HelloWorldApiIntegrationTest` | End-to-end (embedded Tomcat) | Real URL decoding (`+`, `%20`, UTF-8), malformed encodings, `HEAD`, health endpoints |
| `OpenApiDocumentationTest` | Application context (MockMvc) | Generated OpenAPI spec matches the contract; Swagger UI is served |
| `RequestLoggingTest` | Application context (MockMvc + captured output) | Request IDs (generated, reused, unsafe ones replaced), summary lines, no names in logs |

Every test scenario, with its ID, is listed in [docs/test-scenarios.md](docs/test-scenarios.md).
Test display names reference those IDs.

### Postman test suite

[postman/HelloWorldAPI.postman_collection.json](postman/HelloWorldAPI.postman_collection.json)
contains 92 requests, one per scenario, grouped into the same sections as the scenarios
document, including health-check, API-documentation and request-ID sections. Each request
checks the status code and, where relevant, the `Content-Type` and the exact JSON body. Every
request also checks that the response has an `X-Request-Id` header.

1. Start the application (`./mvnw spring-boot:run`).
2. In Postman, choose **Import** and select the collection file.
3. Open the collection and choose **Run** to execute every request with the Collection Runner.

The collection uses a `baseUrl` variable (default `http://localhost:8080`); edit it on the
collection's **Variables** tab if the app runs elsewhere. The same `baseUrl` works whether the
app runs from IntelliJ, with `java -jar`, in Docker, or as `ROOT.war` in Tomcat. Query values are stored already
percent-encoded (for example `%C3%81d%C3%A1m` for `Ádám`) so they reach the server exactly
as intended.

To run it from the command line with [Newman](https://www.npmjs.com/package/newman):

```bash
npx newman run postman/HelloWorldAPI.postman_collection.json
```

### Continuous integration

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs on every push to `master` / `develop`
and on pull requests. It builds the project, runs all JUnit tests, starts the application and
runs the Postman collection against it with Newman, then checks that the Docker image builds.

## Project structure

```
src/main/java/org/example/helloworld
├── HelloWorldApplication.java      Spring Boot entry point
├── greeting
│   ├── GreetingService.java        Validation and greeting rules (no web dependencies)
│   └── InvalidNameException.java
└── web
    ├── HelloWorldController.java   GET /hello-world
    ├── ApiExceptionHandler.java    Maps InvalidNameException to 400 {"error": "Invalid Input"}
    ├── RequestLoggingFilter.java   Request IDs and one log line per request
    ├── OpenApiConfig.java          API title, version and description for the OpenAPI document
    ├── GreetingResponse.java
    └── ErrorResponse.java
src/main/resources/application.properties   Server, shutdown, health-check, logging and API-docs settings
.run/HelloWorldApplication.run.xml          Shared IntelliJ IDEA run configuration
docs/test-scenarios.md                      All test scenarios with IDs
postman/                                    Postman collection
.github/workflows/ci.yml                    CI pipeline
Dockerfile                                  Container image
```

## Assumptions

The specification leaves some cases open. These are the decisions taken:

1. **Capitalization.** Only the first letter is upper-cased; the rest of the name is returned
   as given (`aLICE` → `Hello ALICE`). Upper-casing does not depend on the server's locale.
2. **Whitespace.** Leading and trailing whitespace is removed before validation
   (`" alice "` → `Hello Alice`). A name that is only whitespace counts as empty → 400.
3. **English alphabet only.** The first character must be an ASCII letter `A–M` or `a–m`.
   Accented letters (`Ádám`), other scripts (`Анна`), lookalike characters (fullwidth `ａ`),
   digits, symbols, emoji and control characters → 400.
4. **Only the first character is checked.** The rest of the name may contain any characters
   (spaces, hyphens, digits, accents). They are returned safely escaped as JSON.
5. **Repeated parameter.** `?name=alice&name=zoe` is ambiguous and → 400, even if every value
   would be valid. A single value containing a comma (`?name=alice,zoe`) is one name.
6. **Maximum length.** Names longer than 100 characters (after trimming) → 400.
7. **Parameter name is case-sensitive.** `?Name=alice` is treated as a missing `name` → 400.
8. **Other HTTP errors use Spring's defaults.** Unknown path or trailing slash → 404,
   methods other than `GET`/`HEAD` → 405, `Accept` types other than JSON → 406.
   Only the cases in the specification use the `{"error": "Invalid Input"}` body.
9. **Malformed URL encoding** (e.g. `?name=%ZZ`) → 400. Servers handle this differently:
   the embedded Tomcat 10.1 skips the bad parameter (so the name is missing), while Tomcat 11
   rejects the whole query string. Both cases produce `400 {"error": "Invalid Input"}`.
10. **No authentication.** The endpoint is public. The specification defines the response for
    every request without credentials, and requiring them would change that contract
    (`401 Unauthorized` instead of the specified responses). The endpoint reads and changes no
    data. See *Future improvements* for how authentication would be added.
11. **No version in the URL.** The specification defines the path as `/hello-world`, so it is
    kept as is and treated as **version 1** of the API (the `info.version` in the OpenAPI
    document). Backward-compatible changes, such as a new optional field, stay on this path. A
    breaking change would be published as `/v2/hello-world`, with `/hello-world` kept unchanged
    as v1 until clients have migrated.

## Future improvements

These were left out to keep the solution within the scope of the exercise:

- **Authentication and authorization.** Preferably at an API gateway, or in the app as an
  OAuth2 / JWT resource server (Spring Security) requiring a scope such as `greeting:read`.
  Authentication would run before validation: a request without valid credentials gets
  `401`, one without the required scope gets `403`, and only authorized requests reach the
  name rules (`200` / `400`). Health endpoints would stay public for load balancers.
- **Rate limiting** to protect against abuse, normally at the gateway.
- **HTTPS**, normally terminated at the load balancer or gateway.
- **Consistent error bodies.** 404, 405 and 406 currently use Spring's default error JSON; a
  single format (for example RFC 9457 Problem Details) would be easier for clients.
- **Explicit API versioning** once a second version is actually needed: URI versions
  (`/v2/...`) for visibility, or Spring Framework 7's built-in API versioning after an upgrade
  to Spring Boot 4, plus `Deprecation` / `Sunset` response headers on the old version.
- **Metrics** (Micrometer + Prometheus) for request counts, latency and the 200/400 ratio.
- **Distributed tracing** (OpenTelemetry via Micrometer Tracing) to follow a request across
  services; the current request ID only covers this one service.
- **Test coverage reporting** (JaCoCo) and **dependency vulnerability scanning** in CI.
- **Load testing** (Gatling or k6) to measure throughput and latency limits.
