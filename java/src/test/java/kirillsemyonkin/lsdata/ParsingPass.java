package kirillsemyonkin.lsdata;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

class ParsingPass {

    public static <T> void assertEquals(Class<T> type, T expected, T actual) {
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void nothing() {
        var text = new ByteArrayInputStream("".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.Level()
        );
    }

    @Test
    void comment() {
        var text = new ByteArrayInputStream(
                """
                    # test1
                    # test2
                """.getBytes()
        );

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.Level()
        );
    }

    @Test
    void list() {
        var text = new ByteArrayInputStream("[]".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.List()
        );
    }

    @Test
    void level() {
        var text = new ByteArrayInputStream("{}".getBytes());

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.Level()
        );
    }

    @Test
    void basic() {
        var text = new ByteArrayInputStream(
                """
                    a 10 # comment
                    b 20
                    c a  \"test string\\nand spaces\"  b
                    d 'also\"string'
                    glued\" key\" test
                """.getBytes()
        );

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.Level(Map.of(
                        "a", new LSD.Value("10"),
                        "b", new LSD.Value("20"),
                        "c", new LSD.Value("a  test string\nand spaces  b"),
                        "d", new LSD.Value("also\"string"),
                        "glued key", new LSD.Value("test")
                ))
        );
    }

    @Test
    void nestedLevel() {
        var text = new ByteArrayInputStream(
                """
                    a {
                        a 10
                    }
                    b{}
                    c{ c 30 }
                    d{ d 40
                       2 50 }
                """.getBytes()
        );

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.Level(Map.of(
                        "a", new LSD.Level(Map.of("a", new LSD.Value("10"))),
                        "b", new LSD.Level(),
                        "c", new LSD.Level(Map.of("c", new LSD.Value("30"))),
                        "d", new LSD.Level(Map.of(
                                "d", new LSD.Value("40"),
                                "2", new LSD.Value("50")
                        ))
                ))
        );
    }

    @Test
    void nestedList() {
        var text = new ByteArrayInputStream(
                """
                    a [
                        a 10
                    ]
                    b[]
                    c[ 1 2 {} 3 4 ]
                    d[ 1 2
                       3 4 ]
                """.getBytes()
        );

        var lsd = assertDoesNotThrow(() -> LSD.parse(text));

        assertEquals(
                LSD.class,
                lsd,
                new LSD.Level(Map.of(
                        "a", new LSD.List(new LSD.Value("a 10")),
                        "b", new LSD.List(),
                        "c", new LSD.List(
                                new LSD.Value("1 2"),
                                new LSD.Level(),
                                new LSD.Value("3 4")
                        ),
                        "d", new LSD.List(
                                new LSD.Value("1 2"),
                                new LSD.Value("3 4")
                        )
                ))
        );
    }
}
