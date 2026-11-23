package org.example.helloworld;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * End-to-end tests against the real embedded server. Requests are written to a raw socket
 * so that query strings reach the server byte-for-byte, exactly as a client would send
 * them (URL decoding, '+' as space, malformed encodings). Scenario IDs refer to
 * docs/test-scenarios.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloWorldApiIntegrationTest {

    private static final String INVALID_INPUT = "{\"error\":\"Invalid Input\"}";

    @LocalServerPort
    private int port;

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            ?name=alice                | Hello Alice
            ?name=mary+jane            | Hello Mary jane
            ?name=mary%20jane          | Hello Mary jane
            ?name=%61lice              | Hello Alice
            ?name=%20alice%20          | Hello Alice
            ?name=d%27Artagnan         | Hello D'Artagnan
            ?name=alice&foo=bar        | Hello Alice
            """)
    @DisplayName("V1, V6, V7, V9, V10, W1: query strings are decoded before validation")
    void greetsValidNames(String query, String message) throws IOException {
        HttpResult result = get("/hello-world" + query);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.contentType()).startsWith("application/json");
        assertThat(result.body()).isEqualTo("{\"message\":\"" + message + "\"}");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "",                         // M1 missing
            "?name=",                   // M2 empty
            "?name",                    // M3 no '='
            "?name=+++",                // M4 '+' decodes to spaces
            "?name=%20%20",             // M4
            "?name=%09",                // M5 tab
            "?name=zoe",                // S1
            "?name=%C3%81d%C3%A1m",     // N5 "Ádám" (UTF-8)
            "?name=%D0%90%D0%BD%D0%BD%D0%B0", // N6 "Анна"
            "?name=%F0%9F%98%80alice",  // N8 emoji
            "?name=%00alice",           // N9 null character
            "?name=alice&name=zoe",     // D1
            "?name=%ZZ"                 // D3 malformed percent-encoding
    })
    @DisplayName("M, S, N, D: invalid requests return 400 Invalid Input")
    void rejectsInvalidRequests(String query) throws IOException {
        HttpResult result = get("/hello-world" + query);

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.contentType()).startsWith("application/json");
        assertThat(result.body()).isEqualTo(INVALID_INPUT);
    }

    @Test
    @DisplayName("R6-style check over HTTP: response is UTF-8 JSON")
    void returnsUtf8Json() throws IOException {
        HttpResult result = get("/hello-world?name=a%C3%A9");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).isEqualTo("{\"message\":\"Hello Aé\"}");
    }

    @Test
    @DisplayName("H6: HEAD returns 200 with an empty body")
    void headReturnsEmptyBody() throws IOException {
        HttpResult result = send("HEAD", "/hello-world?name=alice");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    @DisplayName("OPS1: health endpoints report UP")
    void healthEndpointsReportUp(String path) throws IOException {
        HttpResult result = get(path);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("OPS2: other actuator endpoints are not exposed")
    void otherActuatorEndpointsAreHidden() throws IOException {
        assertThat(get("/actuator/env").status()).isEqualTo(404);
        assertThat(get("/actuator/beans").status()).isEqualTo(404);
    }

    private HttpResult get(String pathAndQuery) throws IOException {
        return send("GET", pathAndQuery);
    }

    private HttpResult send(String method, String pathAndQuery) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            // HTTP/1.0 keeps the response un-chunked and closes the connection afterwards.
            String request = method + " " + pathAndQuery + " HTTP/1.0\r\nHost: localhost\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            return HttpResult.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private record HttpResult(int status, String contentType, String body) {

        static HttpResult parse(String raw) {
            int headerEnd = raw.indexOf("\r\n\r\n");
            String[] headerLines = raw.substring(0, headerEnd).split("\r\n");
            int status = Integer.parseInt(headerLines[0].split(" ")[1]);
            String contentType = "";
            for (String line : headerLines) {
                if (line.toLowerCase().startsWith("content-type:")) {
                    contentType = line.substring("content-type:".length()).strip();
                }
            }
            return new HttpResult(status, contentType, raw.substring(headerEnd + 4));
        }
    }
}
