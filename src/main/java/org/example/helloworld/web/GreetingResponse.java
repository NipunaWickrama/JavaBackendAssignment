package org.example.helloworld.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Successful response body: {@code {"message": "Hello Alice"}}. */
@Schema(description = "Greeting for a valid name")
public record GreetingResponse(
        @Schema(description = "Greeting with the first letter of the name upper-cased",
                example = "Hello Alice", requiredMode = Schema.RequiredMode.REQUIRED)
        String message) {
}
