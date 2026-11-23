package org.example.helloworld.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the greeting rules. Scenario IDs refer to docs/test-scenarios.md.
 */
class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    private void assertRejected(String name) {
        assertThatThrownBy(() -> service.greet(name)).isInstanceOf(InvalidNameException.class);
    }

    @Nested
    @DisplayName("Valid names (A–M)")
    class ValidNames {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource(delimiter = '|', textBlock = """
                alice      | Hello Alice
                Alice      | Hello Alice
                ALICE      | Hello ALICE
                aLICE      | Hello ALICE
                bob        | Hello Bob
                Kate       | Hello Kate
                a          | Hello A
                mary jane  | Hello Mary jane
                d'Artagnan | Hello D'Artagnan
                jean-luc   | Hello Jean-luc
                alice123   | Hello Alice123
                """)
        @DisplayName("V1–V8: first letter upper-cased, rest unchanged")
        void greetsValidNames(String name, String expected) {
            assertThat(service.greet(name)).isEqualTo(expected);
        }

        @Test
        @DisplayName("R1/R2: special characters after the first letter are kept as-is")
        void keepsSpecialCharacters() {
            assertThat(service.greet("a\"b")).isEqualTo("Hello A\"b");
            assertThat(service.greet("a\\b")).isEqualTo("Hello A\\b");
            assertThat(service.greet("a\nb")).isEqualTo("Hello A\nb");
            assertThat(service.greet("a<script>")).isEqualTo("Hello A<script>");
        }

        @Test
        @DisplayName("N10: a combining accent after 'a' is accepted (only the first char is checked)")
        void acceptsCombiningMarkAfterFirstLetter() {
            assertThat(service.greet("á")).isEqualTo("Hello Á");
        }

        @Test
        @DisplayName("R6: upper-casing is locale-independent (Turkish 'i')")
        void upperCasingIgnoresDefaultLocale() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                assertThat(service.greet("i")).isEqualTo("Hello I");
            } finally {
                Locale.setDefault(original);
            }
        }
    }

    @Nested
    @DisplayName("Alphabet boundaries")
    class Boundaries {

        @ParameterizedTest(name = "[{index}] \"{0}\" accepted")
        @ValueSource(strings = {"a", "A", "m", "M"})
        @DisplayName("B1/B2: A and M are accepted")
        void acceptsFirstHalfBoundaries(String name) {
            assertThat(service.greet(name)).isEqualTo("Hello " + name.toUpperCase(Locale.ROOT));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" rejected")
        @ValueSource(strings = {"n", "N", "z", "Z"})
        @DisplayName("B3/B4: N and Z are rejected")
        void rejectsSecondHalfBoundaries(String name) {
            assertRejected(name);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" rejected")
        @ValueSource(strings = {"@x", "[x", "`x", "{x"})
        @DisplayName("B5–B8: ASCII neighbours of A, Z, a and z are rejected")
        void rejectsAsciiNeighbours(String name) {
            assertRejected(name);
        }
    }

    @Nested
    @DisplayName("Second-half names (N–Z)")
    class SecondHalfNames {

        @ParameterizedTest(name = "[{index}] \"{0}\" rejected")
        @ValueSource(strings = {"nick", "Zoe", "peter", "o'brien", "NANCY"})
        @DisplayName("S1–S3")
        void rejectsSecondHalfNames(String name) {
            assertRejected(name);
        }
    }

    @Nested
    @DisplayName("Missing, empty and blank names")
    class MissingNames {

        @ParameterizedTest(name = "[{index}] {0}")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n", " \t\n ", " "})
        @DisplayName("M1, M2, M4, M5: null, empty and whitespace-only names are rejected")
        void rejectsMissingOrBlankNames(String name) {
            assertRejected(name);
        }
    }

    @Nested
    @DisplayName("Surrounding whitespace")
    class Whitespace {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {" alice", "alice  ", "\talice\n", " alice "})
        @DisplayName("W1, W2, W4: whitespace is trimmed before validation")
        void trimsWhitespace(String name) {
            assertThat(service.greet(name)).isEqualTo("Hello Alice");
        }

        @Test
        @DisplayName("W3: trimming does not make a second-half name valid")
        void rejectsSecondHalfNameAfterTrim() {
            assertRejected(" zoe");
        }
    }

    @Nested
    @DisplayName("First character is not an English letter")
    class NonEnglishFirstCharacter {

        @ParameterizedTest(name = "[{index}] \"{0}\" rejected")
        @ValueSource(strings = {
                "1alice", "_bob", "-amy", "'amy", "@alice", "#x", "<script>",  // N1–N4
                "Ádám", "émile", "ñora",                                       // N5 accented Latin
                "Анна", "李",                                                   // N6 non-Latin
                "ａlice",                                                       // N7 fullwidth a
                "😀alice",                                                      // N8 emoji
                "\u0000alice", "\u0007alice"                                   // N9 control chars
        })
        @DisplayName("N1–N9")
        void rejectsNonEnglishFirstCharacter(String name) {
            assertRejected(name);
        }
    }

    @Nested
    @DisplayName("Name length")
    class Length {

        @Test
        @DisplayName("R7: exactly the maximum length is accepted")
        void acceptsMaximumLength() {
            String name = "a".repeat(GreetingService.MAX_NAME_LENGTH);
            assertThat(service.greet(name)).isEqualTo("Hello A" + "a".repeat(GreetingService.MAX_NAME_LENGTH - 1));
        }

        @Test
        @DisplayName("R8: one character over the maximum is rejected")
        void rejectsMaximumLengthPlusOne() {
            assertRejected("a".repeat(GreetingService.MAX_NAME_LENGTH + 1));
        }

        @Test
        @DisplayName("R9: a very long name is rejected without errors")
        void rejectsVeryLongName() {
            assertRejected("a".repeat(10_000));
        }

        @Test
        @DisplayName("R10: length is measured after trimming")
        void measuresLengthAfterTrimming() {
            String name = "  " + "a".repeat(GreetingService.MAX_NAME_LENGTH) + "  ";
            assertThat(service.greet(name)).startsWith("Hello A");
        }
    }
}
