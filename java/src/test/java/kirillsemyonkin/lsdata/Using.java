package kirillsemyonkin.lsdata;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

class Using {

    public static <T> void assertEquals(Class<T> type, T expected, T actual) {
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void parse() {
        var text = new ByteArrayInputStream("a 10".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                byte.class,
                assertDoesNotThrow(() -> lsd.parsed(byte.class, Exception::new, "a").get()),
                (byte) 10
        );
    }

    @Test
    void level() {
        var text = new ByteArrayInputStream("a { b 10 }".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                assertDoesNotThrow(() -> lsd.level(Exception::new, "a").get().get().get("b")),
                new LSD.Value("10")
        );
        assertEquals(
                String.class,
                assertDoesNotThrow(() -> lsd.value(Exception::new, "a", "b").get().get()),
                "10"
        );
    }

    @Test
    void list() {
        var text = new ByteArrayInputStream("a [ 10 ]".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                assertDoesNotThrow(() -> lsd.list(Exception::new, "a").get().get().get(0)),
                new LSD.Value("10")
        );
        assertEquals(
                String.class,
                assertDoesNotThrow(() -> lsd.value(Exception::new, "a", 0).get().get()),
                "10"
        );
    }

    @Test
    void nested() {
        var text = new ByteArrayInputStream("a [ { a 10 } ]".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                String.class,
                assertDoesNotThrow(() -> lsd.value(Exception::new, "a", "0", "a").get().get()),
                "10"
        );
    }

    @Test
    void dynamic() {
        var text = new ByteArrayInputStream(
                """
                a [ 
                    10
                    20
                ]
            """
                        .getBytes()
        );

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        var id = 1;
        assertEquals(
                String.class,
                assertDoesNotThrow(() -> lsd.value(Exception::new, "a", id).get().get()),
                "20"
        );
    }
}
