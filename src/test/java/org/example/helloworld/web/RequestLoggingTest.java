package org.example.helloworld.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Request IDs and log output. Scenario IDs refer to docs/test-scenarios.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** Distinctive names so the assertions cannot match unrelated log text. */
    private static final String VALID_SECRET_NAME = "abigailvalidsecret";
    private static final String INVALID_SECRET_NAME = "zacharyinvalidsecret";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("LOG1: a request ID is generated and returned when the client sends none")
    void generatesRequestId() throws Exception {
        mockMvc.perform(get("/hello-world").param("name", "alice"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)));
    }

    @Test
    @DisplayName("LOG2: a safe client request ID is reused, returned and written to the log")
    void reusesClientRequestId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/hello-world").param("name", "alice")
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "client-id-42"))
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "client-id-42"));

        assertThat(output).contains("[client-id-42]").contains("GET /hello-world -> 200");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "has spaces",
            "line\nbreak",
            "fake] INFO injected",
            "this-request-id-is-far-too-long-to-be-accepted-by-the-server-0123456789"
    })
    @DisplayName("LOG3: an unsafe or over-long client request ID is replaced")
    void replacesUnsafeRequestId(String unsafeId) throws Exception {
        MvcResult result = mockMvc.perform(get("/hello-world").param("name", "alice")
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, unsafeId))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                .isNotEqualTo(unsafeId)
                .matches(UUID_PATTERN);
    }

    @Test
    @DisplayName("LOG4: one summary line per request with method, path, status and duration; no name")
    void logsSummaryWithoutName(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/hello-world").param("name", VALID_SECRET_NAME))
                .andExpect(status().isOk());

        assertThat(output).containsPattern("GET /hello-world -> 200 \\(\\d+ ms\\)");
        assertThat(output).doesNotContain(VALID_SECRET_NAME);
    }

    @Test
    @DisplayName("LOG5: the reason for a 400 is logged at INFO without the name or a stack trace")
    void logsRejectionReasonWithoutName(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/hello-world").param("name", INVALID_SECRET_NAME))
                .andExpect(status().isBadRequest());

        assertThat(output)
                .containsPattern("INFO .*Rejected request: Name does not start with a letter from A to M")
                .contains("GET /hello-world -> 400")
                .doesNotContain(INVALID_SECRET_NAME)
                .doesNotContain("InvalidNameException");
    }

    @Test
    @DisplayName("LOG6: health checks get a request ID but no summary line")
    void doesNotLogHealthChecks(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));

        assertThat(output).doesNotContain("/actuator/health ->");
    }

    @Test
    @DisplayName("LOG7: an undecodable query string is logged without the raw value")
    void doesNotLogRawValueOfUndecodableQuery(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/hello-world").with(request -> new UndecodableRequest()))
                .andExpect(status().isBadRequest());

        assertThat(output)
                .contains("Rejected request: Query string could not be decoded")
                .doesNotContain(INVALID_SECRET_NAME);
    }

    /** Simulates Tomcat 11, whose exception message quotes the raw parameter value. */
    private static final class UndecodableRequest extends MockHttpServletRequest {

        UndecodableRequest() {
            super("GET", "/hello-world");
        }

        @Override
        public String[] getParameterValues(String name) {
            throw new IllegalStateException(
                    "Character decoding failed. Parameter [name] with value [" + INVALID_SECRET_NAME + "]");
        }
    }
}
