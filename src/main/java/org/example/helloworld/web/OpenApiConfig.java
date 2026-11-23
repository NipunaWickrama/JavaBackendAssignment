package org.example.helloworld.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** API-level metadata for the generated OpenAPI document. */
@Configuration
public class OpenApiConfig {

    /** Version of the API contract (not the build); see the versioning note in the README. */
    static final String API_VERSION = "1";

    @Bean
    OpenAPI helloWorldOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Hello World API")
                .version(API_VERSION)
                .description("""
                        Greets a name whose first letter is in the first half of the English \
                        alphabet (A–M, case-insensitive). Any other input returns \
                        `400 {"error": "Invalid Input"}`."""));
    }
}
