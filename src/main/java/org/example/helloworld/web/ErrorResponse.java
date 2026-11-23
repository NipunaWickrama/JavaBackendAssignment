package org.example.helloworld.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Error response body: {@code {"error": "Invalid Input"}}. */
@Schema(description = "Returned for any invalid, missing or empty name")
public record ErrorResponse(
        @Schema(description = "Always \"Invalid Input\"", example = "Invalid Input",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String error) {

    public static final ErrorResponse INVALID_INPUT = new ErrorResponse("Invalid Input");
}
