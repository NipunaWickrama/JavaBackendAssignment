package org.example.helloworld.greeting;

import org.springframework.stereotype.Service;

/**
 * Builds a greeting for a name, accepting only names whose first letter is in the
 * first half of the English alphabet (A–M, case-insensitive).
 */
@Service
public class GreetingService {

    /** Longest accepted name, measured after surrounding whitespace is removed. */
    public static final int MAX_NAME_LENGTH = 100;

    /**
     * Returns {@code "Hello <Name>"} with the first letter upper-cased and the rest unchanged.
     *
     * @param name the raw name; may be {@code null}
     * @throws InvalidNameException if the name is missing, blank, too long, or does not
     *                              start with an ASCII letter from A to M
     */
    public String greet(String name) {
        if (name == null) {
            throw new InvalidNameException("Name is missing");
        }

        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidNameException("Name is blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidNameException("Name exceeds " + MAX_NAME_LENGTH + " characters");
        }

        char firstLetter = trimmed.charAt(0);
        if (!isInFirstHalfOfAlphabet(firstLetter)) {
            throw new InvalidNameException("Name does not start with a letter from A to M");
        }

        return "Hello " + Character.toUpperCase(firstLetter) + trimmed.substring(1);
    }

    /**
     * Deliberately limited to ASCII: {@link Character#isLetter(char)} would also accept
     * letters such as 'Á' or 'а' (Cyrillic), which are not part of the English alphabet.
     */
    private static boolean isInFirstHalfOfAlphabet(char c) {
        return (c >= 'a' && c <= 'm') || (c >= 'A' && c <= 'M');
    }
}
