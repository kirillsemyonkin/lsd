package ru.kirillsemyonkin.lsdata;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.kirillsemyonkin.lsdata.LSD.ParseException.*;

@SuppressWarnings("ThrowableResultIgnored")
class ParsingError {

    @Test
    void unexpectedCharAtFileEnd() {
        assertThrows(
                UnexpectedCharAtFileEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("[] test".getBytes()))
        );
        assertThrows(
                UnexpectedCharAtFileEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("{} test".getBytes()))
        );
    }

    @Test
    void unexpectedStringEnd() {
        assertThrows(
                UnexpectedStringEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"".getBytes()))
        );
        assertThrows(
                UnexpectedStringEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\u".getBytes()))
        );
        assertThrows(
                UnexpectedStringEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\udfff".getBytes()))
        );
        assertThrows(
                UnexpectedStringEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\x".getBytes()))
        );
        assertThrows(
                UnexpectedStringEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\xff".getBytes()))
        );
    }

    @Test
    void unexpectedCharEscapeEnd() {
        assertThrows(
                UnexpectedCharEscapeEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\".getBytes()))
        );
        assertThrows(
                UnexpectedCharEscapeEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\j".getBytes()))
        );
    }

    @Test
    void unexpectedCharInByteEscape() {
        assertThrows(
                UnexpectedCharInByteEscape.class,
                () -> LSD.parse(new ByteArrayInputStream("test \"\\xffNO".getBytes()))
        );
        assertThrows(
                UnexpectedCharInByteEscape.class,
                () -> LSD.parse(new ByteArrayInputStream(
                        "test \"\\xf0\\x00\\x00\\x00\\x00\"".getBytes()
                ))
        );
    }

    @Test
    void unexpectedCharInUnicodeEscape() {
        assertThrows(
                UnexpectedCharInUnicodeEscape.class,
                () -> LSD.parse(new ByteArrayInputStream(
                        "test \"\\udfffNO\"".getBytes()
                ))
        );
        assertThrows(
                UnexpectedCharInUnicodeEscape.class,
                () -> LSD.parse(new ByteArrayInputStream(
                        "test \"\\udfff\\udfff\"".getBytes()
                ))
        );
    }

    @Test
    void expectedKeyOrEnd() {
        assertThrows(
                ExpectedKeyOrEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("{".getBytes()))
        );
    }

    @Test
    void expectedKeyPartAfterKeySeparator() {
        assertThrows(
                ExpectedKeyPartAfterKeySeparator.class,
                () -> LSD.parse(new ByteArrayInputStream("{a.".getBytes()))
        );
    }

    @Test
    void expectedLSDAfterKey() {
        assertThrows(
                ExpectedLSDAfterKey.class,
                () -> LSD.parse(new ByteArrayInputStream("{a ".getBytes()))
        );
    }

    @Test
    void expectedListLSDOrEnd() {
        assertThrows(
                ExpectedListLSDOrEnd.class,
                () -> LSD.parse(new ByteArrayInputStream("[".getBytes()))
        );
    }

    @Test
    void keyCollisionShouldBeLevelButIsNot() {
        assertThrows(
                KeyCollisionShouldBeLevelButIsNot.class,
                () -> LSD.parse(new ByteArrayInputStream(
                        """
                            a 10
                            a.b 20
                        """.getBytes()
                ))
        );
    }

    @Test
    void keyCollisionKeyAlreadyExists() {
        assertThrows(
                KeyCollisionKeyAlreadyExists.class,
                () -> LSD.parse(new ByteArrayInputStream(
                        """
                            a 10
                            a 20
                        """.getBytes()
                ))
        );
    }
}
