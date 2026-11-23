# Test Scenarios — `GET /hello-world`

This document lists every test scenario for the `/hello-world` endpoint, including edge cases,
invalid inputs and null/missing inputs. Scenario IDs are referenced from test names and the README.

## Specification summary

| Condition | Status | Body |
|---|---|---|
| First letter of `name` is A–M / a–m | `200 OK` | `{"message": "Hello <Name>"}` |
| First letter of `name` is N–Z / n–z | `400 Bad Request` | `{"error": "Invalid Input"}` |
| `name` missing or empty | `400 Bad Request` | `{"error": "Invalid Input"}` |

Notation used below:

- **200** = `200 OK` with body `{"message": "Hello <Name>"}`
- **400** = `400 Bad Request` with body `{"error": "Invalid Input"}`
- All responses use `Content-Type: application/json`.

## Design decisions (assumptions)

The spec leaves these cases open. They are fixed here and must also be listed in the README.

| # | Topic | Decision |
|---|---|---|
| A1 | Framework | Spring Boot (Spring Web) with Maven. JUnit 5 + MockMvc for tests. |
| A2 | Capitalization | Only the first letter is upper-cased; the rest of the name is returned unchanged (`aLICE` → `Hello ALICE`). Upper-casing uses `Locale.ROOT`. |
| A3 | Whitespace | Leading/trailing whitespace is trimmed before validation. A name that is blank after trimming is treated as empty → 400. |
| A4 | Allowed first character | Only ASCII `A–M` / `a–m` are valid. Accented letters, non-Latin scripts, fullwidth letters, digits, symbols, emoji and control characters → 400. |
| A5 | Repeated parameter | `?name=x&name=y` is ambiguous and is rejected → 400. |
| A6 | Maximum length | Names longer than **100** characters (after trimming) → 400. |
| A7 | Combining characters | Only the first `char` is checked, so `a` + combining accent is accepted → 200. |
| A8 | Unknown paths / methods | Framework defaults: unknown path → 404, wrong method → 405, trailing slash → 404 (Spring Boot 3 default). |

---

## 1. Valid names (A–M) → 200

| ID | Request | Expected body |
|---|---|---|
| V1 | `?name=alice` | `{"message": "Hello Alice"}` |
| V2 | `?name=Alice` | `{"message": "Hello Alice"}` |
| V3 | `?name=ALICE` | `{"message": "Hello ALICE"}` (A2) |
| V4 | `?name=bob` / `?name=Kate` | `Hello Bob` / `Hello Kate` |
| V5 | `?name=a` | `Hello A` |
| V6 | `?name=mary+jane` / `?name=mary%20jane` | `Hello Mary jane` |
| V7 | `?name=d'Artagnan` / `?name=jean-luc` | `Hello D'Artagnan` / `Hello Jean-luc` |
| V8 | `?name=alice123` | `Hello Alice123` |
| V9 | `?name=%61lice` | `Hello Alice` (percent-decoded) |
| V10 | `?name=alice&foo=bar` | `Hello Alice` (unknown params ignored) |

## 2. Boundary letters

| ID | Request | Expected |
|---|---|---|
| B1 | `?name=a`, `?name=A` | 200 (lower bound) |
| B2 | `?name=m`, `?name=M` | 200 (last of first half) |
| B3 | `?name=n`, `?name=N` | 400 (first of second half) |
| B4 | `?name=z`, `?name=Z` | 400 (upper bound) |
| B5 | `?name=%40x` (`@`, char before `A`) | 400 |
| B6 | `?name=%5Bx` (`[`, char after `Z`) | 400 |
| B7 | `?name=%60x` (`` ` ``, char before `a`) | 400 |
| B8 | `?name=%7Bx` (`{`, char after `z`) | 400 |

## 3. Second-half names (N–Z) → 400

| ID | Request |
|---|---|
| S1 | `?name=nick`, `?name=Zoe`, `?name=peter` |
| S2 | `?name=o'brien` |
| S3 | `?name=NANCY` |

## 4. Missing / empty / blank → 400

| ID | Request | Notes |
|---|---|---|
| M1 | `/hello-world` | Parameter absent (null) |
| M2 | `?name=` | Empty string |
| M3 | `?name` | Parameter present without `=` |
| M4 | `?name=%20%20`, `?name=+++` | Whitespace only (A3) |
| M5 | `?name=%09`, `?name=%0A` | Tab / newline only (A3) |
| M6 | `?Name=alice`, `?NAME=alice` | Parameter name is case-sensitive → treated as missing |
| M7 | `?nme=alice` | Misspelled parameter → treated as missing |

## 5. Surrounding whitespace (A3)

| ID | Request | Expected |
|---|---|---|
| W1 | `?name=%20alice` | 200 `Hello Alice` |
| W2 | `?name=alice%20%20` | 200 `Hello Alice` |
| W3 | `?name=%20zoe` | 400 |
| W4 | `?name=%09bob%0A` | 200 `Hello Bob` |

## 6. First character is not an ASCII letter → 400 (A4)

| ID | Request | Notes |
|---|---|---|
| N1 | `?name=1alice` | Digit |
| N2 | `?name=_bob`, `?name=-amy`, `?name='amy` | Punctuation |
| N3 | `?name=%40alice`, `?name=%23x` | Symbols `@`, `#` |
| N4 | `?name=%3Cscript%3E` | `<script>` |
| N5 | `?name=Ádám`, `?name=émile`, `?name=ñora` | Accented Latin (`Character.isLetter` would wrongly accept) |
| N6 | `?name=Анна`, `?name=李` | Non-Latin scripts |
| N7 | `?name=ａlice` | Fullwidth `ａ` (U+FF41) |
| N8 | `?name=😀alice` | Surrogate pair; must not throw |
| N9 | `?name=%00alice` | Null / control character |

Exception to the rule above:

| ID | Request | Expected |
|---|---|---|
| N10 | `?name=a%CC%81` (`a` + combining acute accent) | 200, message is `Hello ` + `A` + the combining accent (A7) |

## 7. Response / serialization correctness

| ID | Scenario | Expected |
|---|---|---|
| R1 | `?name=a%22b` (`a"b`) | 200, valid JSON: `{"message":"Hello A\"b"}` |
| R2 | `?name=a%5Cb`, `?name=a%0Ab` | 200, backslash / newline correctly JSON-escaped |
| R3 | `?name=a%3Cscript%3Ealert(1)%3C%2Fscript%3E` | 200, returned as data, `Content-Type: application/json` |
| R4 | Any 200 response | Body contains **only** `message` |
| R5 | Any 400 response | Body contains **only** `error`, value exactly `Invalid Input` |
| R6 | `?name=i` with JVM default locale `tr` | `Hello I` (not `Hello İ`) (A2) |
| R7 | Name of exactly 100 chars starting with `a` | 200 (A6 boundary) |
| R8 | Name of 101 chars starting with `a` | 400 (A6 boundary) |
| R9 | Name of 10,000+ chars | 400, no exception (unit test). Over HTTP the Postman check uses 5,000 chars: beyond about 8 KB the server rejects the request line itself, before the app runs |
| R10 | `?name=%20` + 100 chars + `%20` (101+ before trim, 100 after) | 200 — length is checked after trimming |

## 8. Duplicate / malformed parameters

| ID | Request | Expected |
|---|---|---|
| D1 | `?name=alice&name=zoe` | 400 (A5) |
| D2 | `?name=alice&name=bob` | 400 (A5 — even if both are valid) |
| D2b | `?name=alice,zoe` (comma inside one value) | 200 `Hello Alice,zoe` — one value, not two names; the comma is not a separator |
| D3 | `?name=%ZZ` (malformed percent-encoding) | 400, never 500 (Tomcat 10.1 skips the parameter; Tomcat 11 throws `IllegalStateException`, which the controller maps to 400) |

## 9. HTTP-level behaviour (A8)

| ID | Scenario | Expected |
|---|---|---|
| H1 | `POST` / `PUT` / `DELETE` `/hello-world?name=alice` | 405 Method Not Allowed |
| H2 | `GET /hello-world/?name=alice` | 404 |
| H3 | `GET /Hello-World?name=alice` | 404 |
| H4 | `GET /unknown` | 404 |
| H5 | `GET /hello-world?name=alice` with `Accept: application/xml` | 406 Not Acceptable, never 500 |
| H6 | `HEAD /hello-world?name=alice` | 200, empty body (empty body verified end-to-end; MockMvc does not strip HEAD bodies) |

## 10. Operations (health checks)

| ID | Scenario | Expected |
|---|---|---|
| OPS1 | `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | 200, `"status": "UP"` |
| OPS2 | `GET /actuator/env`, `/actuator/beans` | 404 — only health is exposed |

## 11. API documentation (OpenAPI)

| ID | Scenario | Expected |
|---|---|---|
| DOC1 | `GET /v3/api-docs` | 200, OpenAPI 3 document, `info.version` = `1`, only `/hello-world` (GET) documented |
| DOC2 | Parameters of `GET /hello-world` in the spec | Exactly one: `name`, in `query`, optional, `maxLength` 100 |
| DOC3 | Responses of `GET /hello-world` in the spec | `200` → `GreetingResponse` (`message`), `400` → `ErrorResponse` (`error`, example `Invalid Input`) |
| DOC4 | `GET /swagger-ui/index.html` | 200, `text/html` |
| DOC5 | `GET /swagger-ui.html` | Redirect to `/swagger-ui/index.html` |

## 12. Logging and request IDs

| ID | Scenario | Expected |
|---|---|---|
| LOG1 | Any request without `X-Request-Id` | Response has `X-Request-Id` with a generated UUID |
| LOG2 | `X-Request-Id: client-id-42` | Same value returned in the header and written in the log lines |
| LOG3 | `X-Request-Id` with spaces, a line break, `]`, or over 64 characters | Replaced with a generated UUID (prevents log injection) |
| LOG4 | `GET /hello-world?name=<distinctive valid name>` | One line `GET /hello-world -> 200 (N ms)`; the name never appears in the log |
| LOG5 | `GET /hello-world?name=<distinctive N–Z name>` | INFO `Rejected request: <reason>` and `-> 400` line; no name, no stack trace |
| LOG6 | `GET /actuator/health` | Has `X-Request-Id`, but no summary line is logged |
| LOG7 | Query string the container cannot decode (Tomcat 11) | Logged as `Query string could not be decoded`, without the raw value |

---

## Test layer mapping

| Layer | Tooling | Scenarios |
|---|---|---|
| Unit — validation & greeting logic | JUnit 5, `@ParameterizedTest`, `@NullAndEmptySource`, `@CsvSource` | V1–V8, B1–B8, S1–S3, M1, M2, M4, M5, W1–W4, N1–N10, R6–R10 |
| Web layer — controller + error handling | `@WebMvcTest` + MockMvc | V10, M1, M6, M7, D1, D2, D2b, R1–R5, R8, H1–H6, plus representative 200 and 400 cases |
| End-to-end — real embedded Tomcat | `@SpringBootTest(RANDOM_PORT)` + raw HTTP | V1, V6, V7, V9, V10, W1, M1–M5, S1, N5, N6, N8, N9, D1, D3, H6, OPS1, OPS2 (URL decoding behaves as in production) |
| Generated API documentation | `@SpringBootTest` + MockMvc | DOC1–DOC5 |
| Logging and request IDs | `@SpringBootTest` + MockMvc + `OutputCaptureExtension` | LOG1–LOG7 |
| Postman collection | Postman / Newman against a running app | All of the above except R6 (needs the server's JVM locale) and LOG4–LOG7 (log content is not visible over HTTP). R4/R5 are checked by every 200/400 request comparing the whole body; R9 uses 5,000 characters; the presence of `X-Request-Id` is also checked on every request. |
