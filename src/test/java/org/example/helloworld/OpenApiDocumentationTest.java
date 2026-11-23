package org.example.helloworld;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that the generated OpenAPI document describes the real contract, so the docs cannot
 * silently drift from the code. Scenario IDs refer to docs/test-scenarios.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode spec;

    @BeforeEach
    void loadSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        spec = objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("DOC1: spec is OpenAPI 3 with API version 1 and documents only /hello-world")
    void documentsOnlyThePublicEndpoint() {
        assertThat(spec.path("openapi").asText()).startsWith("3.");
        assertThat(spec.at("/info/title").asText()).isEqualTo("Hello World API");
        assertThat(spec.at("/info/version").asText()).isEqualTo("1");

        JsonNode paths = spec.path("paths");
        assertThat(paths.size()).isEqualTo(1);
        assertThat(paths.has("/hello-world")).isTrue();
        assertThat(paths.path("/hello-world").has("get")).isTrue();
    }

    @Test
    @DisplayName("DOC2: the only parameter is an optional 'name' query parameter")
    void documentsNameParameter() {
        JsonNode parameters = spec.at("/paths/~1hello-world/get/parameters");

        assertThat(parameters.size()).isEqualTo(1);
        JsonNode name = parameters.get(0);
        assertThat(name.path("name").asText()).isEqualTo("name");
        assertThat(name.path("in").asText()).isEqualTo("query");
        assertThat(name.path("required").asBoolean(false)).isFalse();
        assertThat(name.at("/schema/maxLength").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("DOC3: 200 and 400 responses reference the message and error schemas")
    void documentsResponses() {
        JsonNode responses = spec.at("/paths/~1hello-world/get/responses");

        assertThat(responses.at("/200/content/application~1json/schema/$ref").asText())
                .endsWith("/GreetingResponse");
        assertThat(responses.at("/400/content/application~1json/schema/$ref").asText())
                .endsWith("/ErrorResponse");

        JsonNode schemas = spec.at("/components/schemas");
        assertThat(schemas.at("/GreetingResponse/properties").has("message")).isTrue();
        assertThat(schemas.at("/GreetingResponse/required").toString()).contains("message");
        assertThat(schemas.at("/ErrorResponse/properties").has("error")).isTrue();
        assertThat(schemas.at("/ErrorResponse/properties/error/example").asText()).isEqualTo("Invalid Input");
    }

    @Test
    @DisplayName("DOC4: Swagger UI page is served")
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    @DisplayName("DOC5: /swagger-ui.html redirects to the Swagger UI page")
    void swaggerUiShortcutRedirects() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
