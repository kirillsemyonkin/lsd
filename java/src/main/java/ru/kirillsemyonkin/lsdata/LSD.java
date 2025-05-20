package ru.kirillsemyonkin.lsdata;

import ru.kirillsemyonkin.lsdata.LSD.KeyPathPart.Index;
import ru.kirillsemyonkin.lsdata.LSD.KeyPathPart.Key;
import ru.kirillsemyonkin.lsdata.LSD.Level;
import ru.kirillsemyonkin.lsdata.LSD.List;
import ru.kirillsemyonkin.lsdata.LSD.Option.None;
import ru.kirillsemyonkin.lsdata.LSD.Option.Some;
import ru.kirillsemyonkin.lsdata.LSD.ParseException.*;
import ru.kirillsemyonkin.lsdata.LSD.Value;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * LSD (Less Syntax Data) configuration/data transfer format.
 *
 * <p>
 * It is made out three variants - values (strings/words), lists (`[]`) and
 * levels (`{}`). File may not contain only a value (it will be considered a
 * level).
 *
 * <pre>{@code
 * try (var stream = new FileInputStream("example.lsd")){
 *     var fileLSD = LSD.parse(stream);
 *     var langKey = "java";
 *     var langName = lsd
 *         .value(
 *             LanguageNameIsNotAValueException::new,
 *             "languages", langKey, "name"
 *         )
 *         .orElseThrow(CouldNotFindLanguageNameException::new);
 *     System.out.println(langName);
 * }
 * }</pre>
 *
 * Visit [LSD repository](https://github.com/kirillsemyonkin/lsd) for a full LSD description.
 *
 * @see Value
 * @see List
 * @see Level
 */
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // netbeans/vscode Oracle.oracle-java bug
public sealed interface LSD permits Value, List, Level {
    /**
     * Value of the {@link LSD} (strings/words).
     *
     * @param get String that represents the value.
     */
    record Value(String get) implements LSD {
        @Override
        public Some<Value> asValue() {
            return new Some<>(this);
        }

        @Override
        public Option<LSD> inner(Object... parts) {
            return switch (parts.length) {
                case 0 -> new Some<>(this);
                default -> new None<>();
            };
        }
    }

    /**
     * List of {@link LSD} (`[]`).
     *
     * @param get List that represents {@link LSD}s.
     */
    record List(ArrayList<LSD> get) implements LSD {
        /**
         * Creates an empty list.
         */
        public List() {
            this(new ArrayList<>());
        }

        /**
         * Creates a list from a {@link java.util.List}.
         *
         * @param list List to make a list from.
         */
        public List(java.util.List<LSD> list) {
            this(new ArrayList<>(list));
        }

        /**
         * Creates a list from multiple {@link LSD}s.
         *
         * @param list {@link LSD}s to put into a list.
         */
        public List(LSD... list) {
            this(new ArrayList<>(java.util.List.of(list)));
        }

        @Override
        public Some<List> asList() {
            return new Some<>(this);
        }

        @Override
        public Option<LSD> inner(Object... parts) {
            return switch (parts.length) {
                case 0 -> new Some<>(this);
                default -> {
                    var key = KeyPathPart.from(parts[0]);
                    var rest = Arrays.copyOfRange(parts, 1, parts.length);
                    yield switch (key) {
                        case Index(var i) -> Option.of(i >= 0 && i < get.size(), () -> get.get(i))
                            .flatMap(lsd -> lsd.inner(rest));
                        case Key(var ignored) -> new None<>();
                    };
                }
            };
        }
    }

    /**
     * Level of {@link LSD} (`{}`).
     *
     * @param get Map that represents the level {@link LSD}s.
     */
    record Level(LinkedHashMap<String, LSD> get) implements LSD {
        /**
         * Creates an empty level.
         */
        public Level() {
            this(new LinkedHashMap<>());
        }

        /**
         * Creates a level from a map.
         *
         * @param map Map to make a level from.
         */
        public Level(Map<String, LSD> map) {
            this(new LinkedHashMap<>(map));
        }

        @Override
        public Some<Level> asLevel() {
            return new Some<>(this);
        }

        @Override
        public Option<LSD> inner(Object... parts) {
            return switch (parts.length) {
                case 0 -> new Some<>(this);
                default -> {
                    var key = KeyPathPart.from(parts[0]);
                    var rest = Arrays.copyOfRange(parts, 1, parts.length);
                    yield Option
                        .of(get.get(key.toString()))
                        .flatMap(lsd -> lsd.inner(rest));
                }
            };
        }
    }

    //
    // Parse
    //

    /**
     * All exceptions thrown by the {@link LSD} parser.
     */
    sealed abstract class ParseException extends Exception permits
        ReadFailure,
        UnexpectedCharAtFileEnd,
        UnexpectedStringEnd,
        UnexpectedCharEscapeEnd,
        UnexpectedCharInByteEscape,
        UnexpectedCharInUnicodeEscape,
        ExpectedKeyOrEnd,
        ExpectedKeyPartAfterKeySeparator,
        ExpectedLSDAfterKey,
        ExpectedListLSDOrEnd,
        KeyCollisionShouldBeLevelButIsNot,
        KeyCollisionKeyAlreadyExists {
        /**
         * Make a ParseException with no cause.
         */
        public ParseException() {
        }

        /**
         * Make a ParseException with a cause.
         *
         * @param cause Cause of the exception (e.g. {@link IOException}).
         */
        public ParseException(Exception cause) {
            super(cause);
        }

        /**
         * {@link IOException}s thrown during reading.
         */
        public static final class ReadFailure extends ParseException {
            /**
             * Create exception.
             *
             * @param cause Cause of the read failure.
             */
            public ReadFailure(IOException cause) {
                super(cause);
            }
        }

        /**
         * Any kind of characters have occurred after a list or a level as the
         * file root.
         */
        public static final class UnexpectedCharAtFileEnd extends ParseException {
            /**
             * Create exception.
             */
            public UnexpectedCharAtFileEnd() {
            }
        }

        /**
         * File ended after a string start (`"` or `'`).
         */
        public static final class UnexpectedStringEnd extends ParseException {
            /**
             * Create exception.
             */
            public UnexpectedStringEnd() {
            }
        }

        /**
         * File ended after a char escape (`\` and such).
         */
        public static final class UnexpectedCharEscapeEnd extends ParseException {
            /**
             * Create exception.
             */
            public UnexpectedCharEscapeEnd() {
            }
        }

        /**
         * Invalid UTF-8 escape sequence (invalid syntax or byte sequence).
         */
        public static final class UnexpectedCharInByteEscape extends ParseException {
            /**
             * Create exception.
             */
            public UnexpectedCharInByteEscape() {
            }
        }

        /**
         * Invalid UTF-16 escape sequence (invalid syntax or byte sequence).
         */
        public static final class UnexpectedCharInUnicodeEscape extends ParseException {
            /**
             * Create exception.
             */
            public UnexpectedCharInUnicodeEscape() {
            }
        }

        /**
         * During a level parse, expected key or end of level (`}`).
         */
        public static final class ExpectedKeyOrEnd extends ParseException {
            /**
             * Create exception.
             */
            public ExpectedKeyOrEnd() {
            }
        }

        /**
         * During a level parse, expected a key part after a key part separator
         * (`.`).
         */
        public static final class ExpectedKeyPartAfterKeySeparator extends ParseException {
            /**
             * Create exception.
             */
            public ExpectedKeyPartAfterKeySeparator() {
            }
        }

        /**
         * During a level parse, expected an LSD after a key.
         */
        public static final class ExpectedLSDAfterKey extends ParseException {
            /**
             * Create exception.
             */
            public ExpectedLSDAfterKey() {
            }
        }

        /**
         * During a level parse, expected a list of LSDs or end of list (`]`).
         */
        public static final class ExpectedListLSDOrEnd extends ParseException {
            /**
             * Create exception.
             */
            public ExpectedListLSDOrEnd() {
            }
        }

        /**
         * Were not able to merge a level into a non-level.
         */
        public static final class KeyCollisionShouldBeLevelButIsNot extends ParseException {
            /**
             * Create exception.
             */
            public KeyCollisionShouldBeLevelButIsNot() {
            }
        }

        /**
         * Key repeated twice in the same level or after a merge.
         */
        public static final class KeyCollisionKeyAlreadyExists extends ParseException {
            /**
             * javadoc bug asks me to document this
             * 
             * @hidden
             */
            private final String key;

            /**
             * Create exception.
             *
             * @param key Key that caused the collision.
             */
            public KeyCollisionKeyAlreadyExists(String key) {
                this.key = key;
            }

            /**
             * Get the key stored in this exception.
             *
             * @return Key that caused the collision.
             */
            public String key() {
                return key;
            }
        }
    }

    /**
     * Utility class that represents an optional value. Used instead of
     * {@link java.util.Optional} for the pattern matching niceties.
     *
     * @param <T> Stored value type.
     */
    sealed interface Option<T> permits Some, None {
        /**
         * Present value. Similar to {@link java.util.Optional#isPresent()}.
         *
         * @param <T> Stored value type.
         * @param get Stored value.
         */
        record Some<T>(T get) implements Option<T> {
            /**
             * Constructs new {@link Some}.
             *
             * @param get Value to store. Must not be {@code null}.
             */
            public Some {
                Objects.requireNonNull(get);
            }

            @Override
            public Optional<T> java() {
                return Optional.of(get);
            }

            @Override
            public <E extends Throwable> T orElseThrow(Supplier<E> error) {
                return get;
            }

            @Override
            public <T2> Some<T2> map(Function<T, T2> map) {
                return new Some<>(map.apply(get));
            }

            @Override
            public <T2, E extends Throwable> Some<T2> mapThrowing(MapThrowing<T, T2, E> map) throws E {
                return new Some<>(map.apply(get));
            }

            @Override
            public <T2> Option<T2> flatMap(Function<T, Option<T2>> map) {
                return map.apply(get);
            }

            @Override
            public Option<T> filter(Predicate<T> filter) {
                return filter.test(get)
                    ? this
                    : new None<>();
            }
        }

        /**
         * Empty value. Similar to {@link java.util.Optional#isEmpty()}.
         *
         * @param <T> Supposedly stored value type, but None does not have it.
         */
        record None<T>() implements Option<T> {
            @Override
            public <T2> None<T2> map(Function<T, T2> map) {
                return new None<>();
            }

            @Override
            public <T2, E extends Throwable> None<T2> mapThrowing(MapThrowing<T, T2, E> map) {
                return new None<>();
            }

            @Override
            public <T2> None<T2> flatMap(Function<T, Option<T2>> map) {
                return new None<>();
            }

            @Override
            public None<T> filter(Predicate<T> filter) {
                return this;
            }
        }

        /**
         * Creates an Option object from a nullable value.
         *
         * @param <T>      Stored value type.
         * @param nullable The nullable value to be wrapped in an Option.
         * @return A {@link Some} wrapping the nullable value, or a {@link None}
         * if the nullable value is null.
         */
        static <T> Option<T> of(T nullable) {
            return nullable == null
                ? new None<>()
                : new Some<>(nullable);
        }

        /**
         * Creates an Option object from a nullable value, as well as check a
         * boolean.
         *
         * @param <T>      Stored value type.
         * @param check    Boolean to check.
         * @param nullable The nullable value to be wrapped in an Option.
         * @return A {@link Some} wrapping the nullable value, or a {@link None}
         * if the nullable value is null or the check is false.
         */
        static <T> Option<T> of(boolean check, Supplier<T> nullable) {
            return check && nullable != null
                ? new Some<>(nullable.get())
                : new None<>();
        }

        /**
         * Try to get the value, throwing an exception if it is not present.
         *
         * @return Stored value if present.
         * @throws NoSuchElementException If the value is not present.
         */
        default T get() throws NoSuchElementException {
            throw new NoSuchElementException();
        }

        /**
         * Convert to a {@link java.util.Optional} mirror.
         *
         * @return {@link java.util.Optional}.
         */
        default Optional<T> java() {
            return Optional.empty();
        }

        /**
         * Try to get the value, throwing an exception if it is not present.
         *
         * @param <E>   Exception type.
         * @param error Supplier of the exception.
         * @return Stored value if present.
         * @throws E If the value is not present.
         */
        default <E extends Throwable> T orElseThrow(Supplier<E> error) throws E {
            throw error.get();
        }

        /**
         * Convert the underlying value to another type using a mapping
         * function.
         *
         * @param <T2> New type.
         * @param map  Mapping function.
         * @return Option with new value.
         */
        <T2> Option<T2> map(Function<T, T2> map);

        /**
         * Helper for {@link #mapThrowing(MapThrowing)} that lets you throw
         * while mapping.
         *
         * @param <I> Input type.
         * @param <O> Output type.
         * @param <E> Exception type.
         */
        @FunctionalInterface
        interface MapThrowing<I, O, E extends Throwable> {
            /**
             * Apply the mapping function.
             *
             * @param value Input value.
             * @return Output value.
             * @throws E If the mapping function throws.
             */
            O apply(I value) throws E;
        }

        /**
         * Convert the underlying value to another type using a throwing mapping
         * function.
         *
         * @param <T2> New type.
         * @param <E>  Exception type.
         * @param map  Mapping function.
         * @return Option with new value.
         * @throws E If the mapping function throws.
         */
        <T2, E extends Throwable> Option<T2> mapThrowing(MapThrowing<T, T2, E> map) throws E;

        /**
         * Convert the underlying value to an Option using a mapping function
         * and flatten.
         *
         * @param <T2> New type.
         * @param map  Mapping function.
         * @return Option given by the mapping function.
         */
        <T2> Option<T2> flatMap(Function<T, Option<T2>> map);

        /**
         * Filter the Option using a predicate.
         *
         * @param predicate Filter predicate.
         * @return Option filtered by the predicate.
         */
        Option<T> filter(Predicate<T> predicate);
    }

    /**
     * Utility class for reading, as well as looking ahead by one character.
     */
    class PeekableChars {
        private final Reader reader;

        private Option<Peek> peeked = new None<>();

        /**
         * Peeked character with ability to advance by accepting it.
         */
        public class Peek {
            private final char ch;

            private Peek(char ch) {
                peeked = new Some<>(this);
                this.ch = ch;
            }

            /**
             * Accept the peeked character. Advances the reader.
             *
             * @return The accepted character.
             */
            public char accept() {
                peeked = new None<>();
                return ch;
            }

            /**
             * Get the peeked character.
             *
             * @return Peeked character.
             */
            public char get() {
                return ch;
            }
        }

        /**
         * Create a peekable reader.
         *
         * @param reader Reader to wrap.
         */
        public PeekableChars(Reader reader) {
            this.reader = reader;
        }

        /**
         * Peek next character.
         *
         * @return Peeked character.
         * @throws IOException If unable to read.
         */
        public Option<Peek> peek() throws IOException {
            if (peeked instanceof Some(var peek))
                return new Some<>(peek);

            var res = reader.read();
            if (res < 0) return new None<>();
            return new Some<>(new Peek((char) res));
        }

        /**
         * Peek and immediately accept next character.
         *
         * @return Read character.
         * @throws IOException If unable to read.
         */
        public Option<Character> read() throws IOException {
            if (peek() instanceof Some(var peek))
                return new Some<>(peek.accept());
            return new None<>();
        }
    }

    /**
     * Parse an {@link LSD} from an input stream.
     *
     * <pre>{@code
     * try (var stream = new FileInputStream("example.lsd")) {
     *    var lsd = LSD.parse(stream);
     *    System.out.println(lsd);
     * }
     * }</pre>
     *
     * @param inputStream LSD file stream.
     * @return Parsed LSD.
     * @throws ru.kirillsemyonkin.lsdata.LSD.ParseException If unable to parse.
     */
    static LSD parse(InputStream inputStream) throws ParseException {
        var reader = new BufferedReader(new InputStreamReader(inputStream));
        var stream = new PeekableChars(reader);

        readNWS(stream);

        if (readLevel(stream) instanceof Some(var level)) {
            readNWS(stream);

            if (peek(stream) instanceof Some(var ignored))
                throw new UnexpectedCharAtFileEnd();

            return level;
        }

        if (readList(stream) instanceof Some(var list)) {
            readNWS(stream);

            if (peek(stream) instanceof Some(var ignored))
                throw new UnexpectedCharAtFileEnd();

            return list;
        }

        return readLevelInner(stream, false);
    }

    /**
     * Parse an {@link LSD} from a file.
     *
     * @param file LSD file.
     * @return Parsed LSD.
     * @throws ru.kirillsemyonkin.lsdata.LSD.ParseException If unable to parse.
     * @see #parse(InputStream)
     */
    static LSD parse(File file) throws ParseException {
        try (var stream = new FileInputStream(file)) {
            return parse(stream);
        } catch (IOException e) {
            throw new ReadFailure(e);
        }
    }

    /**
     * Parse an {@link LSD} from a source code string.
     *
     * @param content LSD formatted text.
     * @return Parsed LSD.
     * @throws ru.kirillsemyonkin.lsdata.LSD.ParseException If unable to parse.
     * @see #parse(InputStream)
     */
    static LSD parse(String content) throws ParseException {
        return parse(new ByteArrayInputStream(content.getBytes()));
    }

    /**
     * Parse an {@link LSD} from a file by the given path.
     *
     * @param path Path to an LSD file.
     * @return Parsed LSD.
     * @throws ru.kirillsemyonkin.lsdata.LSD.ParseException If unable to parse.
     * @see #parse(InputStream)
     */
    static LSD parse(Path path) throws ParseException {
        try (var stream = Files.newInputStream(path)) {
            return parse(stream);
        } catch (IOException e) {
            throw new ReadFailure(e);
        }
    }

    private static Option<PeekableChars.Peek> peek(PeekableChars stream) throws ParseException {
        try {
            return stream.peek();
        } catch (IOException e) {
            throw new ReadFailure(e);
        }
    }

    private static Option<Character> read(PeekableChars stream) throws ParseException {
        try {
            return stream.read();
        } catch (IOException e) {
            throw new ReadFailure(e);
        }
    }

    private static String readIWS(PeekableChars stream) throws ParseException {
        var result = new StringBuilder();
        while (peek(stream) instanceof Some(var peek)
            && (peek.get() == ' ' || peek.get() == '\t'))
            result.append(peek.accept());
        return result.toString();
    }

    private static boolean readNWS(PeekableChars stream) throws ParseException {
        readIWS(stream);

        var hasNewline = false;
        var inComment = false;
        while (true) {
            var inCommentFinal = inComment;
            switch (peek(stream)) {
                case Some(var peek) when peek.get() == '\r' || peek.get() == '\n' -> {
                    peek.accept();
                    inComment = false;
                    hasNewline = true;
                }
                case Some(var peek) when inCommentFinal -> {
                    peek.accept();
                    continue;
                }
                case Some(var peek) when peek.get() == '#' -> {
                    peek.accept();
                    inComment = true;
                }
                default -> {
                    return hasNewline;
                }
            }

            readIWS(stream);
        }
    }

    private static Option<LSD> readLSD(
        PeekableChars stream,
        Option<Character> valueIgnoreChar
    ) throws ParseException {
        if (readList(stream) instanceof Some(var list))
            return new Some<>(list);
        if (readLevel(stream) instanceof Some(var level))
            return new Some<>(level);
        if (readValue(stream, valueIgnoreChar) instanceof Some(var value))
            return new Some<>(value);
        return new None<>();
    }

    private static Option<Value> readValue(
        PeekableChars stream,
        Option<Character> ignoreChar
    ) throws ParseException {
        if (!(readValuePart(stream, ignoreChar) instanceof Some(var firstPart)))
            return new None<>();
        var result = new StringBuilder(firstPart);

        while (true) {
            var iws = readIWS(stream);
            switch (readValuePart(stream, ignoreChar)) {
                case Some(var part) -> {
                    result.append(iws);
                    result.append(part);
                }
                case None() -> {
                    return new Some<>(new Value(result.toString()));
                }
            }
        }
    }

    private static Option<String> readValuePart(
        PeekableChars stream,
        Option<Character> ignoreChar
    ) throws ParseException {
        if (readWord(stream, ignoreChar) instanceof Some(var word))
            return new Some<>(word);
        if (readString(stream) instanceof Some(var string))
            return new Some<>(string);
        return new None<>();
    }

    private static Option<String> readWord(
        PeekableChars stream,
        Option<Character> ignoreChar
    ) throws ParseException {
        var result = new StringBuilder();
        loop:
        while (true)
            switch (peek(stream)) {
                case None() -> {
                    break loop;
                }
                case Some(var peek) when " \t\r\n'\"#".indexOf(peek.get()) >= 0 -> {
                    break loop;
                }
                case Some(var peek) when new Some<>(peek.get()).equals(ignoreChar) -> {
                    break loop;
                }
                case Some(var peek) -> result.append(peek.accept());
            }
        return result.isEmpty()
            ? new None<>()
            : new Some<>(result.toString());
    }

    private static Option<String> readString(PeekableChars stream) throws ParseException {
        char closingChar;
        switch (peek(stream)) {
            case Some(var peek) when peek.get() == '"' || peek.get() == '\'' ->
                closingChar = peek.accept();
            default -> {
                return new None<>();
            }
        }

        final class Util {
            static final class HexException extends Exception {
            }

            static byte u4FromHexChar(char ch) throws HexException {
                if (ch >= 'a' && ch <= 'f')
                    return (byte) (ch - 'a' + 10);
                if (ch >= 'A' && ch <= 'F')
                    return (byte) (ch - 'A' + 10);
                if (ch >= '0' && ch <= '9')
                    return (byte) (ch - '0');
                throw new HexException();
            }

            static byte u8From2HexChars(char ch1, char ch2) throws HexException {
                return (byte) (u4FromHexChar(ch1) << 4 | u4FromHexChar(ch2));
            }
        }

        var result = new StringBuilder();
        while (true)
            switch (read(stream).orElseThrow(UnexpectedStringEnd::new)) {
                case '\\' -> {
                    switch (read(stream).orElseThrow(UnexpectedCharEscapeEnd::new)) {
                        case '"' -> result.append('"');
                        case '\\' -> result.append('\\');
                        case '\'' -> result.append('\'');
                        case '0' -> result.append('\0');
                        case 'a', 'A' -> result.append('\u0007');
                        case 'b', 'B' -> result.append('\u0008');
                        case 't', 'T' -> result.append('\t');
                        case 'n', 'N' -> result.append('\n');
                        case 'v', 'V' -> result.append('\u000b');
                        case 'f', 'F' -> result.append('\u000c');
                        case 'r', 'R' -> result.append('\r');
                        case 'x', 'X' -> {
                            byte firstByte;
                            try {
                                firstByte = Util.u8From2HexChars(
                                    read(stream).orElseThrow(UnexpectedStringEnd::new),
                                    read(stream).orElseThrow(UnexpectedStringEnd::new)
                                );
                            } catch (Util.HexException e) {
                                throw new UnexpectedCharInByteEscape();
                            }

                            // xxxxxxxx -> xxxxxxxx 0000... -> yyyyyyyy 1111...
                            // 00000000 -> 00000000 0000... -> 11111111 1111... -> 0
                            // 11111111 -> 11111111 0000... -> 00000000 1111... -> 8
                            var leadingOnes = Integer.numberOfLeadingZeros(~(firstByte << 24));

                            var bytes = new byte[1 + leadingOnes];
                            bytes[0] = firstByte;

                            // does not guarantee only 4, can do 8, does not check for 10...
                            for (var i = 0; i < leadingOnes; i++) {
                                if (read(stream).orElseThrow(UnexpectedStringEnd::new) != '\\'
                                    && "xX".indexOf(read(stream).orElseThrow(UnexpectedStringEnd::new)) < 0)
                                    throw new UnexpectedCharInByteEscape();

                                byte currByte;
                                try {
                                    currByte = Util.u8From2HexChars(
                                        read(stream).orElseThrow(UnexpectedStringEnd::new),
                                        read(stream).orElseThrow(UnexpectedStringEnd::new)
                                    );
                                } catch (Util.HexException e) {
                                    throw new UnexpectedCharInByteEscape();
                                }

                                bytes[i + 1] = currByte;
                            }

                            try {
                                var buf = ByteBuffer.wrap(bytes);
                                result.append(UTF_8.newDecoder().decode(buf));
                            } catch (CharacterCodingException e) {
                                throw new UnexpectedCharInByteEscape();
                            }
                        }
                        case 'u' | 'U' -> {
                            // read first possibly-surrogate HHHH escape
                            byte firstSurrogate1, firstSurrogate2;
                            try {
                                firstSurrogate1 = Util.u8From2HexChars(
                                    read(stream).orElseThrow(UnexpectedStringEnd::new),
                                    read(stream).orElseThrow(UnexpectedStringEnd::new)
                                );
                                firstSurrogate2 = Util.u8From2HexChars(
                                    read(stream).orElseThrow(UnexpectedStringEnd::new),
                                    read(stream).orElseThrow(UnexpectedStringEnd::new)
                                );
                            } catch (Util.HexException e) {
                                throw new UnexpectedCharInUnicodeEscape();
                            }

                            // try checking if first surrogate is enough
                            try {
                                var buf = ByteBuffer.wrap(new byte[]{firstSurrogate1,
                                    firstSurrogate2});
                                result.append(UTF_16BE.newDecoder().decode(buf));
                                continue;
                            } catch (CharacterCodingException ignored) {
                            }
                            // not enough - read second HHHH escape and try to parse as pair

                            if (read(stream).orElseThrow(UnexpectedStringEnd::new) != '\\'
                                && "uU".indexOf(read(stream).orElseThrow(UnexpectedStringEnd::new)) < 0)
                                throw new UnexpectedCharInUnicodeEscape();

                            byte secondSurrogate1, secondSurrogate2;
                            try {
                                secondSurrogate1 = Util.u8From2HexChars(
                                    read(stream).orElseThrow(UnexpectedStringEnd::new),
                                    read(stream).orElseThrow(UnexpectedStringEnd::new)
                                );
                                secondSurrogate2 = Util.u8From2HexChars(
                                    read(stream).orElseThrow(UnexpectedStringEnd::new),
                                    read(stream).orElseThrow(UnexpectedStringEnd::new)
                                );
                            } catch (Util.HexException e) {
                                throw new UnexpectedCharInUnicodeEscape();
                            }

                            try {
                                var buf = ByteBuffer.wrap(new byte[]{firstSurrogate1,
                                    firstSurrogate2, secondSurrogate1, secondSurrogate2});
                                result.append(UTF_16BE.newDecoder().decode(buf));
                            } catch (CharacterCodingException e) {
                                throw new UnexpectedCharInUnicodeEscape();
                            }
                        }
                        default -> throw new UnexpectedCharEscapeEnd();
                    }
                }
                case Character ch when ch == closingChar -> {
                    return new Some<>(result.toString());
                }
                case Character ch -> result.append(ch);
            }
    }

    private static Option<Level> readLevel(PeekableChars stream) throws ParseException {
        switch (peek(stream)) {
            case Some(var peek) when peek.get() == '{' -> peek.accept();
            default -> {
                return new None<>();
            }
        }

        readNWS(stream);

        return new Some<>(readLevelInner(stream, true));
    }

    private static Level readLevelInner(
        PeekableChars stream,
        boolean levelEndsWithClose
    ) throws ParseException {
        var results = new Level();
        while (true) {
            if (levelEndsWithClose)
                if (peek(stream) instanceof Some(var peek)
                    && peek.get() == '}') {
                    peek.accept();
                    return results;
                }

            ArrayList<String> key;
            switch (readKeyPath(stream)) {
                case Some(var k) -> key = k;
                case None() when levelEndsWithClose -> throw new ExpectedKeyOrEnd();
                case None() -> {
                    return results;
                }
            }

            readNWS(stream);

            var lsd = readLSD(stream, new Some<>('}')).orElseThrow(ExpectedLSDAfterKey::new);

            readNWS(stream);

            final class Util {
                static void mergeLevel(Level insertInto, Level level) throws ParseException {
                    for (var entry : level.get().entrySet()) {
                        var key = entry.getKey();
                        switch (entry.getValue()) {
                            case Value value -> {
                                if (insertInto.get().put(key, value) != null)
                                    throw new KeyCollisionKeyAlreadyExists(key);
                            }
                            case List list -> {
                                if (insertInto.get().put(key, list) != null)
                                    throw new KeyCollisionKeyAlreadyExists(key);
                            }
                            case Level lvl -> {
                                switch (insertInto.get().computeIfAbsent(key, k -> new Level())) {
                                    case Value ignored ->
                                        throw new KeyCollisionShouldBeLevelButIsNot();
                                    case List ignored ->
                                        throw new KeyCollisionShouldBeLevelButIsNot();
                                    case Level insertInto2 -> mergeLevel(insertInto2, lvl);
                                }
                            }
                        }
                    }
                }
            }

            var result = new Level();
            var insertInto = result.get();

            for (var i = 0; i < key.size(); i++) {
                var part = key.get(i);

                if (key.size() - 1 == i) {
                    insertInto.put(part, lsd);
                    break;
                }

                insertInto = ((Level) insertInto.computeIfAbsent(part, k -> new Level())).get();
            }

            Util.mergeLevel(results, result);
        }
    }

    private static Option<String> readKeyWord(PeekableChars stream) throws ParseException {
        var result = new StringBuilder();
        loop:
        while (true)
            switch (peek(stream)) {
                case None() -> {
                    break loop;
                }
                case Some(var peek) when " \t\r\n'\"#{}[].".indexOf(peek.get()) >= 0 -> {
                    break loop;
                }
                case Some(var peek) -> result.append(peek.accept());
            }
        return result.isEmpty()
            ? new None<>()
            : new Some<>(result.toString());
    }

    private static Option<String> readKeyPart(PeekableChars stream) throws ParseException {
        var result = new StringBuilder();
        while (true) {
            if (readKeyWord(stream) instanceof Some(var word)) {
                result.append(word);
                continue;
            }

            if (readString(stream) instanceof Some(var string)) {
                result.append(string);
                continue;
            }

            return result.isEmpty()
                ? new None<>()
                : new Some<>(result.toString());
        }
    }

    private static Option<ArrayList<String>> readKeyPath(PeekableChars stream) throws ParseException {
        var result = new ArrayList<String>();

        switch (readKeyPart(stream)) {
            case Some(var keyPart) -> result.add(keyPart);
            case None() -> {
                return new None<>();
            }
        }

        while (peek(stream) instanceof Some(var peek) && peek.get() == '.') {
            peek.accept();
            result.add(readKeyPart(stream).orElseThrow(ExpectedKeyPartAfterKeySeparator::new));
        }

        return new Some<>(result);
    }

    private static Option<LSD> readListLSD(PeekableChars stream) throws ParseException {
        if (readList(stream) instanceof Some(var list))
            return new Some<>(list);
        if (readLevel(stream) instanceof Some(var level))
            return new Some<>(level);
        if (readListValue(stream) instanceof Some(var value))
            return new Some<>(value);
        return new None<>();
    }

    private static Option<String> readListWord(PeekableChars stream) throws ParseException {
        var result = new StringBuilder();
        loop:
        while (true)
            switch (peek(stream)) {
                case None() -> {
                    break loop;
                }
                case Some(var peek) when " \t\r\n'\"#{}[]".indexOf(peek.get()) >= 0 -> {
                    break loop;
                }
                case Some(var peek) -> result.append(peek.accept());
            }
        return Option.of(!result.isEmpty(), result::toString);
    }

    private static Option<String> readListPart(PeekableChars stream) throws ParseException {
        var result = new StringBuilder();
        while (true) {
            if (readListWord(stream) instanceof Some(var word)) {
                result.append(word);
                continue;
            }

            if (readString(stream) instanceof Some(var string)) {
                result.append(string);
                continue;
            }

            return Option.of(!result.isEmpty(), result::toString);
        }
    }

    private static Option<Value> readListValue(PeekableChars stream) throws ParseException {
        if (!(readListPart(stream) instanceof Some(var firstPart)))
            return new None<>();
        var result = new StringBuilder(firstPart);

        while (true) {
            var iws = readIWS(stream);
            switch (readListPart(stream)) {
                case Some(var part) -> {
                    result.append(iws);
                    result.append(part);
                }
                case None() -> {
                    return new Some<>(new Value(result.toString()));
                }
            }
        }
    }

    private static Option<List> readList(PeekableChars stream) throws ParseException {
        switch (peek(stream)) {
            case Some(var peek) when peek.get() == '[' -> peek.accept();
            default -> {
                return new None<>();
            }
        }

        readNWS(stream);

        var results = new List();
        while (true) {
            switch (peek(stream)) {
                case Some(var peek) when peek.get() == ']' -> {
                    peek.accept();
                    return new Some<>(results);
                }
                default -> {
                }
            }

            results.get().add(readListLSD(stream).orElseThrow(ExpectedListLSDOrEnd::new));

            readNWS(stream);
        }
    }

    //
    // KeyPath
    //

    /**
     * Key path part - key (string) or index (number).
     *
     * @see KeyPathPart#from(Object)
     */
    sealed interface KeyPathPart permits Key, Index {
        /**
         * Key path part - key (string).
         *
         * @param get Key.
         */
        record Key(String get) implements KeyPathPart {
            @Override
            public String toString() {
                return get;
            }
        }

        /**
         * Key path part - index (number).
         *
         * @param get Index.
         */
        record Index(int get) implements KeyPathPart {
            @Override
            public String toString() {
                return Integer.toString(get);
            }
        }

        /**
         * Create key path part from any object. Usually converts to a string,
         * with an exception for numbers or strings containing numbers.
         *
         * @param object Object to convert.
         * @return Key path part.
         */
        static KeyPathPart from(Object object) {
            var string = Objects.toString(object);
            try {
                return new Index(Integer.parseInt(string));
            } catch (NumberFormatException e) {
                return new Key(string);
            }
        }
    }

    /**
     * Try to cast to {@link Value}.
     *
     * @return Value if castable.
     */
    default Option<Value> asValue() {
        return new None<>();
    }

    /**
     * Try to cast to {@link List}.
     *
     * @return List if castable.
     */
    default Option<List> asList() {
        return new None<>();
    }

    /**
     * Try to cast to {@link Level}.
     *
     * @return Level if castable.
     */
    default Option<Level> asLevel() {
        return new None<>();
    }

    /**
     * Try to get an inner LSD given by the path.
     *
     * @param parts Path parts.
     * @return Inner LSD.
     */
    Option<LSD> inner(Object... parts);

    /**
     * Try to find an inner {@link Value} given by the path.
     *
     * @param <E>     Exception type.
     * @param invalid Exception to throw if found value is not a {@link Value}.
     * @param parts   Path parts.
     * @return Value if found.
     * @throws E If found value is not a {@link Value}.
     */
    default <E extends Throwable> Option<Value> value(
        Supplier<E> invalid,
        Object... parts
    ) throws E {
        return inner(parts)
            .mapThrowing(lsd -> lsd.asValue().orElseThrow(invalid));
    }

    /**
     * Try to find a value given by the path, and parse it as the given type.
     * <p>
     * For Java built-in types, does `Class.parseClass` methods. For other
     * types, tries to do call a `parse` method and a constructor, ignoring any
     * exceptions, using given exception instead.
     *
     * @param <T>     Type to parse.
     * @param <E>     Exception type.
     * @param type    Type to parse.
     * @param invalid Exception to throw if unable to parse.
     * @param parts   Path parts.
     * @return Parsed value if found.
     * @throws E If unable to parse.
     */
    @SuppressWarnings("unchecked")
    default <T, E extends Throwable> Option<T> parsed(
        Class<T> type,
        Supplier<E> invalid,
        Object... parts
    ) throws E {
        if (!(value(invalid, parts) instanceof Some(Value(var value))))
            return new None<>();
        if (type == boolean.class || type == Boolean.class)
            try {
                return new Some<>((T) (Object) Boolean.parseBoolean(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == byte.class || type == Byte.class)
            try {
                return new Some<>((T) (Object) Byte.parseByte(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == short.class || type == Short.class)
            try {
                return new Some<>((T) (Object) Short.parseShort(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == int.class || type == Integer.class)
            try {
                return new Some<>((T) (Object) Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == long.class || type == Long.class)
            try {
                return new Some<>((T) (Object) Long.parseLong(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == float.class || type == Float.class)
            try {
                return new Some<>((T) (Object) Float.parseFloat(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == double.class || type == Double.class)
            try {
                return new Some<>((T) (Object) Double.parseDouble(value));
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == String.class)
            try {
                return new Some<>((T) value);
            } catch (NumberFormatException e) {
                throw invalid.get();
            }
        if (type == UUID.class)
            try {
                return new Some<>((T) UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                throw invalid.get();
            }
        try {
            return new Some<>(
                type.cast(
                    type.getMethod("parse", String.class)
                        .invoke(null, value)
                )
            );
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return new Some<>(
                type.getConstructor(String.class)
                    .newInstance(value)
            );
        } catch (ReflectiveOperationException ignored) {
        }
        throw invalid.get();
    }

    /**
     * Try to find an inner {@link List} given by the path.
     *
     * @param <E>     Exception type.
     * @param invalid Exception to throw if found value is not a {@link List}.
     * @param parts   Path parts.
     * @return List if found.
     * @throws E If found value is not a {@link List}.
     */
    default <E extends Throwable> Option<List> list(
        Supplier<E> invalid,
        Object... parts
    ) throws E {
        return inner(parts)
            .mapThrowing(lsd -> lsd.asList().orElseThrow(invalid));
    }

    /**
     * Try to find an inner {@link Level} given by the path.
     *
     * @param <E>     Exception type.
     * @param invalid Exception to throw if found value is not a {@link Level}.
     * @param parts   Path parts.
     * @return Level if found.
     * @throws E If found value is not a {@link Level}.
     */
    default <E extends Throwable> Option<Level> level(
        Supplier<E> invalid,
        Object... parts
    ) throws E {
        return inner(parts)
            .mapThrowing(lsd -> lsd.asLevel().orElseThrow(invalid));
    }
}
