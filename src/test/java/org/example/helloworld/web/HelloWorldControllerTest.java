package org.example.helloworld.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.helloworld.greeting.GreetingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Web-layer tests: status codes, JSON shape and HTTP behaviour.
 * Scenario IDs refer to docs/test-scenarios.md.
 */
@WebMvcTest(HelloWorldController.class)
@Import(GreetingService.class)
class HelloWorldControllerTest {

    private static final String INVALID_INPUT_JSON = """
            {"error": "Invalid Input"}
            """;

    @Autowired
    private MockMvc mockMvc;

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    private void expectGreeting(MockHttpServletRequestBuilder request, String message) throws Exception {
        perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"message\": \"" + message + "\"}", JsonCompareMode.STRICT));
    }

    private void expectInvalidInput(MockHttpServletRequestBuilder request) throws Exception {
        perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(INVALID_INPUT_JSON, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("V1/R4: valid name returns 200 with only a message field")
    void greetsValidName() throws Exception {
        expectGreeting(get("/hello-world").param("name", "alice"), "Hello Alice");
    }

    @Test
    @DisplayName("V10: unknown query parameters are ignored")
    void ignoresUnknownParameters() throws Exception {
        expectGreeting(get("/hello-world").param("name", "alice").param("foo", "bar"), "Hello Alice");
    }

    @ParameterizedTest(name = "[{index}] name=\"{0}\"")
    @ValueSource(strings = {"nick", "Zoe", "n", "Z", "1alice", "Ádám", "", "   "})
    @DisplayName("S/B/N/M/R5: invalid names return 400 with only an error field")
    void rejectsInvalidNames(String name) throws Exception {
        expectInvalidInput(get("/hello-world").param("name", name));
    }

    @Test
    @DisplayName("M1: missing name parameter returns 400")
    void rejectsMissingName() throws Exception {
        expectInvalidInput(get("/hello-world"));
    }

    @ParameterizedTest(name = "[{index}] ?{0}=alice")
    @ValueSource(strings = {"Name", "NAME", "nme"})
    @DisplayName("M6/M7: parameter name is case-sensitive and must be spelled correctly")
    void rejectsWrongParameterName(String parameterName) throws Exception {
        expectInvalidInput(get("/hello-world").param(parameterName, "alice"));
    }

    @Test
    @DisplayName("D1: repeated name parameter with mixed values returns 400")
    void rejectsRepeatedNameWithMixedValues() throws Exception {
        expectInvalidInput(get("/hello-world").param("name", "alice", "zoe"));
    }

    @Test
    @DisplayName("D2: repeated name parameter returns 400 even when all values are valid")
    void rejectsRepeatedNameWithValidValues() throws Exception {
        expectInvalidInput(get("/hello-world").param("name", "alice", "bob"));
    }

    @Test
    @DisplayName("Comma inside a single value is not treated as a repeated parameter")
    void acceptsCommaInSingleValue() throws Exception {
        expectGreeting(get("/hello-world").param("name", "alice,zoe"), "Hello Alice,zoe");
    }

    @Test
    @DisplayName("R1/R2: quotes, backslashes and newlines are JSON-escaped")
    void escapesSpecialCharacters() throws Exception {
        perform(get("/hello-world").param("name", "a\"b\\c\nd"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"message\":\"Hello A\\\"b\\\\c\\nd\"}"));
    }

    @Test
    @DisplayName("R3: HTML in the name is returned as JSON data")
    void returnsHtmlAsJsonData() throws Exception {
        expectGreeting(get("/hello-world").param("name", "a<script>alert(1)</script>"),
                "Hello A<script>alert(1)</script>");
    }

    @Test
    @DisplayName("R8: name over the maximum length returns 400")
    void rejectsTooLongName() throws Exception {
        expectInvalidInput(get("/hello-world").param("name", "a".repeat(GreetingService.MAX_NAME_LENGTH + 1)));
    }

    @Test
    @DisplayName("H1: POST, PUT and DELETE return 405")
    void rejectsOtherMethods() throws Exception {
        perform(post("/hello-world").param("name", "alice")).andExpect(status().isMethodNotAllowed());
        perform(put("/hello-world").param("name", "alice")).andExpect(status().isMethodNotAllowed());
        perform(delete("/hello-world").param("name", "alice")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("H2: trailing slash returns 404")
    void rejectsTrailingSlash() throws Exception {
        perform(get("/hello-world/").param("name", "alice")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H3/H4: other paths return 404")
    void rejectsUnknownPaths() throws Exception {
        perform(get("/Hello-World").param("name", "alice")).andExpect(status().isNotFound());
        perform(get("/unknown")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H5: requesting XML returns 406")
    void rejectsUnsupportedAcceptHeader() throws Exception {
        perform(get("/hello-world").param("name", "alice").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    /**
     * Tomcat 11 (Servlet 6.1) throws IllegalStateException from getParameterValues when the
     * query string is malformed; the embedded Tomcat 10.1 used by the other tests does not, so
     * the container behaviour is simulated here.
     */
    @Test
    @DisplayName("D3: query string the container cannot decode returns 400")
    void rejectsUndecodableQueryString() throws Exception {
        expectInvalidInput(get("/hello-world").with(request -> new MockHttpServletRequestThrowingOnParameters()));
    }

    private static final class MockHttpServletRequestThrowingOnParameters
            extends org.springframework.mock.web.MockHttpServletRequest {

        MockHttpServletRequestThrowingOnParameters() {
            super("GET", "/hello-world");
        }

        @Override
        public String[] getParameterValues(@NonNull String name) {
            throw new IllegalStateException("Character decoding failed");
        }
    }

    /** The empty body for HEAD is added by the servlet container; see the integration test. */
    @Test
    @DisplayName("H6: HEAD is routed to the endpoint and returns 200")
    void supportsHead() throws Exception {
        perform(head("/hello-world").param("name", "alice")).andExpect(status().isOk());
    }
}
