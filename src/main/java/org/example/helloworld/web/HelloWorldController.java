package org.example.helloworld.web;

import org.example.helloworld.greeting.GreetingService;
import org.example.helloworld.greeting.InvalidNameException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@Tag(name = "Greeting")
public class HelloWorldController {

    static final String NAME_PARAM = "name";

    private final GreetingService greetingService;

    public HelloWorldController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    // The "name" parameter is documented explicitly because it is read from the request
    // rather than bound as a method argument (see singleName).
    @Operation(
            summary = "Greet a name",
            description = """
                    Returns a greeting when the first letter of `name` is A–M (case-insensitive). \
                    Surrounding whitespace is trimmed and only the first letter is upper-cased.""",
            parameters = @Parameter(
                    name = NAME_PARAM, in = ParameterIn.QUERY, required = false,
                    description = """
                            Name to greet. Must start with an English letter A–M, be at most 100 \
                            characters after trimming, and appear only once.""",
                    schema = @Schema(type = "string", maxLength = 100),
                    examples = {
                            @ExampleObject(name = "valid", value = "alice", summary = "200: Hello Alice"),
                            @ExampleObject(name = "secondHalf", value = "zoe", summary = "400: N–Z"),
                    }),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Name starts with A–M",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = GreetingResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Hello Alice\"}"))),
                    @ApiResponse(responseCode = "400", description = """
                            Name starts with N–Z or a non-letter, or is missing, empty, too long \
                            or repeated""",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ErrorResponse.class),
                                    examples = @ExampleObject(value = "{\"error\": \"Invalid Input\"}"))),
            })
    @GetMapping(path = "/hello-world", produces = MediaType.APPLICATION_JSON_VALUE)
    public GreetingResponse helloWorld(@Parameter(hidden = true) HttpServletRequest request) {
        return new GreetingResponse(greetingService.greet(singleName(request)));
    }

    /**
     * Reads the {@code name} parameter directly from the request rather than through
     * {@code @RequestParam}:
     * <ul>
     *   <li>A repeated parameter can be detected. Binding to a String would silently join the
     *       values ("alice,zoe"), and binding to a List would split a single value on commas.</li>
     *   <li>Servlet 6.1 containers (e.g. Tomcat 11) throw {@link IllegalStateException} when the
     *       query string cannot be decoded (e.g. {@code ?name=%ZZ}). That is invalid client input,
     *       so it is reported as 400 rather than 500. Older containers skip the parameter instead,
     *       which also results in 400 because the name is then missing.</li>
     * </ul>
     */
    private static String singleName(HttpServletRequest request) {
        String[] names;
        try {
            names = request.getParameterValues(NAME_PARAM);
        } catch (IllegalStateException ex) {
            // The container's message quotes the raw parameter value, so it is not passed on to
            // the log.
            throw new InvalidNameException("Query string could not be decoded");
        }

        if (names == null || names.length == 0) {
            return null;
        }
        if (names.length > 1) {
            throw new InvalidNameException("Name parameter supplied " + names.length + " times");
        }
        return names[0];
    }
}
