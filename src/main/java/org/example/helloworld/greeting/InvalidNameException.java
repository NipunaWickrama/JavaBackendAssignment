package org.example.helloworld.greeting;

/**
 * Thrown when a name cannot be greeted. The message is written to the log and is never
 * returned to API clients. It must describe the reason only and never include the submitted
 * name, which is personal data.
 */
public class InvalidNameException extends RuntimeException {

    public InvalidNameException(String message) {
        super(message);
    }
}
