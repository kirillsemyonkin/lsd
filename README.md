# LSD

LSD (Less Syntax Data, also LSData, `.lsd`) is a human-readable configuration / data transfer format
intended to have a very simple syntax (less characters) without complex rules or limitations
(necessary indents of same type, necessary quotes, closing things with repeating same things, etc).

## Implementations / Usage

LSD libraries are available in following implementations:

- [Rust](rust)
- [Java](java)

More implementations may be added here (if you are an author - give us a PR to add a link to this
list or to add a whole implementation).

Each folder has own `README.md` file that describes installation.

### Paths

Implementations provide a way for reading values from LSD files with deeply nested values. The keys
are represented by one-dimensional arrays, containing either only strings or dynamically typed
arrays with optimization for numerical indices (lists may be accessed with non-negative integers).
Here are examples with [Rust](rust) and [Java](java):

<!-- Note for editors: adding more languages to this example is not necessary.
     Use individual README.md files. -->

```rust
let inner_lsd = lsd.inner(key!["users" 0 "name of the user"]);
```

```java
var inner_lsd = lsd.inner("users", 0, "name of the user");
```

## Syntax

LSD files have only 3 types of inner LSDs (also may be called *elements*): *values*, *levels* and
*lists*. Line-comments are also supported with the `#` character.

```grammar
IWS ::= (' ' | '\t')*
NWS ::= IWS (('#' (_ - '\r' - '\n')*)? ('\r' | '\n') IWS)*

# Value can read [ and { so do not go to it first
LSD ::= List | Level | Value

main ::= NWS (Level NWS | List NWS | LevelInner)
```

### Values

Value is a simple piece of data. It has no inner LSDs. Numbers are not distinguished from strings
(you will have to distinguish them in your code instead).

You may optionally wrap a value in quotes (single `'...'` or double `"..."`), which enables you to
use string formatting using escape sequences (starting with a backslash `\`):

| Escape sequence    | Result                                                                    |
|--------------------|---------------------------------------------------------------------------|
| `\"`, `\'`, `\\`   | character, as-is (`\x22`, `\x27`, `\x5C`)                                 |
| `\0`               | null (`\x00`)                                                             |
| `\a`, `\A`         | alert / bell (`\x07`)                                                     |
| `\b`, `\B`         | backspace (`\x08`)                                                        |
| `\t`, `\T`         | (horizontal) tab (`\x09`)                                                 |
| `\n`, `\N`         | newline / line feed (`\x0A`)                                              |
| `\v`, `\V`         | vertical tab (`\x0B`)                                                     |
| `\f`, `\F`         | form feed (`\x0C`)                                                        |
| `\r`, `\R`         | carriage return (`\x0D`)                                                  |
| `\x##`, `\X##`     | any utf-8 character (with sequences support!), `#` is a hex digit         |
| `\u####`, `\U####` | any utf-16 character (with surrogate pair support!), `#` is a hex digit   |

Values end on a newline. Strings that follow values (and vice-versa) get concatenated with the
whitespace between them. Values appear as always trimmed (stripped of whitespace on both sides),
but there are cases when it is not the case. Lists and levels that may appear in a value get
interpreted as plain characters.

```lsd
10                                         # "10"
Hello world!                               # "Hello world!"
"# Test\n\nTesting strings with newlines"  # "# Test\n\nTesting strings with newlines"
10 "px"                                    # "10 px"
a  b                                       # "a  b"
  a  b                                     # "a  b"
```

```grammar
Value ::= ValuePart (IWS ValuePart)*

ValueWordChar ::= _ - ' ' - '\t' - '\r' - '\n' - '"' - '\'' - '#'
ValueWord ::= ValueWordChar+
ValuePart ::= ValueWord | String
String ::= '"' (Escape | StringChar | '\'')* '"'
         | '\'' (Escape | StringChar | '\"')* '\''
StringChar ::= _ - '\'' - '\"' - '\\' - '\r' - '\n'
Escape ::= '\\\"' | '\\\'' | '\\\\'
         | '\\0'
         | '\\a' | '\\A'
         | '\\b' | '\\B'
         | '\\t' | '\\T'
         | '\\n' | '\\N'
         | '\\v' | '\\V'
         | '\\f' | '\\F'
         | '\\r' | '\\R'
         # utf-8 logic omitted
         | '\\x' Hex Hex | '\\X' Hex Hex
         # utf-16 logic omitted
         | '\\u' Hex Hex Hex Hex | '\\U' Hex Hex Hex Hex
Hex ::= 'a' . 'f' | 'A' . 'F' | '0' . '9'
```

### Levels

Level (map, dictionaries) is an ordered set of key-LSD pairs, where key is a unique word or string.
The syntax for levels uses curly braces `{...}`, and inside contains keys/paths followed by some
whitespace, LSDs, and then newlines. An LSD file is immediately put into a level, if it is not
a list (`[...]`) and not a level (`{...}`), and thus, empty file is considered an empty level.
Levels with matching paths are merged. Levels may be omitted altogether and replaced by a dot `.` in
a key/path (level itself does not have to exist beforehand).

```lsd
key value
level {
    a b
}
# {"key": "value", "level": {"a": "b"}}

"empty level" {}
# {"empty level": {}}

outer{ # no space before `{` and `[` or after a string - also accepted
    "example level" {
        value 10
    }
}
outer."example level".value2 20
a.b.c 30
# {"outer": {"example level": {"value": "10", "value2": "20"}}, "a": {"b": {"c": "30"}}}
```

```grammar
Level ::= '{' NWS LevelInner '}'

LevelInner ::= (KeyPath NWS LevelLSD NWS)*

KeyPath ::= KeyPart ('.' KeyPart)*
KeyPart ::= (KeyWord | String)+
KeyWord ::= (ValueWordChar - '{' - '}' - '[' - ']' - '.')+

LevelLSD ::= LevelValue | List | Level
LevelValue ::= LevelValuePart (IWS LevelValuePart)*
LevelValueWordChar ::= ValueWordChar - '}'
LevelValueWord ::= LevelValueWordChar+
LevelValuePart ::= LevelValueWord | String
```

### Lists

List (vector) is an ordered collection of non-necessarily-unique LSDs. Lists use square brackets
`[...]` syntax.

```lsd
[
    test
    of things
    "and such"
    {
        a b
    }
]
# ["test", "of things", "and such", {"a": "b"}]

[{} as {}]  # [{}, "as", {}]
```

```grammar
List ::= '[' NWS (ListLSD NWS)* ']'

ListLSD ::= ListValue | List | Level
ListPart ::= (ListWord | String)+
ListWord ::= (ValueWordChar - '{' - '}' - '[' - ']')+
ListValue ::= ListPart (IWS ListPart)*
```

### Example

Here is an example file that describes how an LSD file may look (taken from
[buildpp](https://github.com/kirillsemyonkin/buildpp/blob/master/example.buildpp.lsd)):

```lsd
name project-name
version 0.1.0

dependency {
    msmpi {
        is local pair
        include C:\Program Files (x86)\Microsoft SDKs\MPI\Include
        library C:\Program Files (x86)\Microsoft SDKs\MPI\Lib\x64
    }
}

profile {
    default {
        is msvc
        standard c++20 
    }
}
```
