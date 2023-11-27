//! LSD (Less Syntax Data) configuration/data transfer format.
//!
//! It is made out three variants - values (strings/words), lists (`[]`) and
//! levels (`{}`). File may not contain only a value (it will be considered a
//! level).
//!
//! ```rust
//! use lsdata::LSD;
//! use lsdata::LSDGetExt;
//! use lsdata::key;
//! use std::io::Cursor;
//!
//! #[derive(Debug)]
//! enum CustomError {
//!     LanguageNameIsNotAValue,
//!     CouldNotFindLanguageName,
//! }
//!
//! use CustomError::*;
//!
//! let file = Cursor::new("languages.rust.name Rust");
//! let lsd = LSD::parse(file).unwrap();
//! let lang_key = "rust";
//! let lang_name = lsd
//!     .value(
//!         || LanguageNameIsNotAValue,
//!         key!["languages" lang_key "name"],
//!     )
//!     .unwrap()
//!     .ok_or_else(|| CouldNotFindLanguageName)
//!     .unwrap();
//! assert_eq!(lang_name, "Rust");
//! ```
//!
//! Visit [LSD repository](https://github.com/kirillsemyonkin/lsd) for
//! a full LSD description.

use std::borrow::Borrow;
use std::fmt::Display;
use std::io;
use std::io::BufReader;
use std::io::Read;
use std::iter::Peekable;
use std::ops::Not;
use std::rc::Rc;
use std::str::FromStr;

use implicit_clone::unsync::IString;
use implicit_clone::ImplicitClone;
use indexmap::IndexMap;
use utf8_chars::BufReadCharsExt;

pub type Value = String;
pub type List = Vec<LSD>;
pub type Level = IndexMap<Value, LSD>;

/// Main LSD enum.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LSD {
    /// Value of the LSD (strings/words).
    Value(Value),

    /// List of the LSD (`[]`).
    List(List),

    /// Level of the LSD (`{}`).
    Level(Level),
}

impl Default for LSD {
    fn default() -> Self { Self::Level(Default::default()) }
}

impl PartialEq<&LSD> for LSD {
    fn eq(&self, other: &&LSD) -> bool { &self == other }
}

impl PartialEq<LSD> for &LSD {
    fn eq(&self, other: &LSD) -> bool { self == &other }
}

//
// Parse
//

/// All errors thrown by the [LSD] parser.
#[derive(Debug)]
pub enum ParseError {
    /// [io::Error]s thrown during reading.
    ReadFailure(io::Error),

    /// Any kind of characters have occured after a list of a level as the file root.
    UnexpectedCharAtFileEnd,

    /// File ended after a string start (`"` or `'`).
    UnexpectedStringEnd,

    /// File ended after a char escape (`\` and such).
    UnexpectedCharEscapeEnd,

    /// Invalid UTF-8 escape sequence (invalid syntax or byte sequence).
    UnexpectedCharInByteEscape,

    /// Invalid UTF-16 escape sequence (invalid syntax or byte sequence).
    UnexpectedCharInUnicodeEscape,

    /// During a level parse, expected key or end of level (`}`).
    ExpectedKeyOrEnd,

    /// During a level parse, expected a key part after a key part separator (`.`).
    ExpectedKeyPartAfterKeySeparator,

    /// During a level parse, expected an LSD after a key.
    ExpectedLSDAfterKey,

    /// During a level parse, expected a list of LSDs or end of list (`]`).
    ExpectedListLSDOrEnd,

    /// Were not able to merge a level into a non-level.
    KeyCollisionShouldBeLevelButIsNot,

    /// Key repeated twice in the same level or after a merge.
    KeyCollisionKeyAlreadyExists(String),
}

impl From<io::Error> for ParseError {
    fn from(value: io::Error) -> Self { Self::ReadFailure(value) }
}

impl LSD {
    /// Parse an [LSD] from a [Read] stream.
    ///
    /// # Examples
    ///
    /// ```rust
    /// use lsdata::LSD;
    /// use lsdata::Level;
    /// use std::io::Cursor;
    ///
    /// let file = Cursor::new("a 10");
    /// let lsd = LSD::parse(file).unwrap();
    /// assert_eq!(
    ///     lsd,
    ///     LSD::Level(Level::from([
    ///         (
    ///             format!("a"),
    ///             LSD::Value(format!("10")),
    ///         ),
    ///     ])),
    /// );
    /// ```
    pub fn parse(stream: impl Read) -> Result<LSD, ParseError> {
        use ParseError::*;

        let mut reader = BufReader::new(stream);
        let stream = &mut reader
            .chars()
            .peekable();

        read_nws(stream)?;

        if let Some(level) = read_level(stream)? {
            read_nws(stream)?;

            if let Some(_) = peek(stream)? {
                return Err(UnexpectedCharAtFileEnd);
            }

            return Ok(LSD::Level(level));
        };

        if let Some(list) = read_list(stream)? {
            read_nws(stream)?;

            if let Some(_) = peek(stream)? {
                return Err(UnexpectedCharAtFileEnd);
            }

            return Ok(LSD::List(list));
        };

        Ok(LSD::Level(read_level_inner(
            stream, false,
        )?))
    }
}

/// Peek a character from the stream.
fn peek(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<
    Option<(
        char,
        impl FnOnce() -> char + '_,
    )>,
    ParseError,
> {
    Ok(match stream.peek() {
        Some(Err(_)) =>
            return Err(stream
                .next()
                .unwrap()
                .unwrap_err())?,
        Some(Ok(ch)) => Some((*ch, || {
            stream
                .next()
                .unwrap()
                .unwrap()
        })),
        None => None,
    })
}

/// Read a character from the stream.
fn read(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<char>, ParseError> {
    Ok(stream
        .next()
        .transpose()?)
}

/// Read a sequence of whitespaces (' ' and '\t') from the stream.
fn read_iws(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<String, ParseError> {
    let mut result = String::new();
    while let Some((' ' | '\t', accept)) = peek(stream)? {
        result.push(accept());
    }
    Ok(result)
}

/// Read a sequence of whitespaces with newlines ('\r' and '\n') from the stream.
fn read_nws(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<bool, ParseError> {
    read_iws(stream)?;

    let mut has_newline = false;

    let mut in_comment = false;
    loop {
        match peek(stream)? {
            Some(('\r' | '\n', accept)) => {
                accept();
                in_comment = false;
                has_newline = true;
            },
            Some((_, accept)) if in_comment => {
                accept();
                continue;
            },
            Some(('#', accept)) => {
                accept();
                in_comment = true;
            },
            _ => return Ok(has_newline),
        };

        read_iws(stream)?;
    }
}

/// Read an LSD from the stream.
fn read_lsd(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
    value_ignore_char: Option<char>,
) -> Result<Option<LSD>, ParseError> {
    if let Some(list) = read_list(stream)? {
        return Ok(Some(LSD::List(list)));
    }

    if let Some(level) = read_level(stream)? {
        return Ok(Some(LSD::Level(level)));
    }

    if let Some(value) = read_value(stream, value_ignore_char)? {
        return Ok(Some(LSD::Value(value)));
    }

    Ok(None)
}

/// Read a value from the stream.
fn read_value(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
    ignore_char: Option<char>,
) -> Result<Option<Value>, ParseError> {
    let Some(mut result) = read_value_part(stream, ignore_char)? else {
        return Ok(None);
    };

    Ok(Some(loop {
        let iws = read_iws(stream)?;
        match read_value_part(stream, ignore_char)? {
            Some(part) => {
                // Rust, why no push_string?
                result.push_str(&iws);
                result.push_str(&part);
            },
            None => break result,
        }
    }))
}

/// Read a value part (word or string) from the stream.
fn read_value_part(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
    ignore_char: Option<char>,
) -> Result<Option<String>, ParseError> {
    if let Some(word) = read_word(stream, ignore_char)? {
        return Ok(Some(word));
    }

    if let Some(string) = read_string(stream)? {
        return Ok(Some(string));
    }

    Ok(None)
}

/// Read a word (non-whitespace, non-comment, non-string) from the stream.
fn read_word(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
    ignore_char: Option<char>,
) -> Result<Option<String>, ParseError> {
    let mut result = String::new();
    loop {
        match peek(stream)? {
            None | Some((' ' | '\t' | '\r' | '\n' | '\'' | '"' | '#', _)) => break,
            Some((ch, _)) if Some(ch) == ignore_char => break,
            Some((_, accept)) => result.push(accept()),
        }
    }
    Ok(result
        .is_empty()
        .not()
        .then_some(result))
}

/// Read a string (starting and ending with `'` or `"`) from the stream.
fn read_string(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<String>, ParseError> {
    use ParseError::*;

    let closing_char = match peek(stream)? {
        Some(('"', accept)) | Some(('\'', accept)) => accept(),
        _ => return Ok(None),
    };

    fn u4_from_hex_char(ch: char) -> Result<u8, ()> {
        match ch {
            'a'..='f' => Ok(ch as u8 - 'a' as u8 + 10),
            'A'..='F' => Ok(ch as u8 - 'A' as u8 + 10),
            '0'..='9' => Ok(ch as u8 - '0' as u8),
            _ => Err(()),
        }
    }

    fn u8_from_2_hex_chars(ch1: char, ch2: char) -> Result<u8, ()> {
        Ok(u4_from_hex_char(ch1)? << 4 | u4_from_hex_char(ch2)?)
    }

    fn u16_from_4_hex_chars(ch1: char, ch2: char, ch3: char, ch4: char) -> Result<u16, ()> {
        Ok((u8_from_2_hex_chars(ch1, ch2)? as u16) << 8 | u8_from_2_hex_chars(ch3, ch4)? as u16)
    }

    let mut result = String::new();
    loop {
        match read(stream)?.ok_or(UnexpectedStringEnd)? {
            '\\' => match read(stream)?.ok_or(UnexpectedCharEscapeEnd)? {
                '"' => result.push('"'),
                '\\' => result.push('\\'),
                '\'' => result.push('\''),
                '0' => result.push('\0'),
                'a' | 'A' => result.push('\x07'),
                'b' | 'B' => result.push('\x08'),
                't' | 'T' => result.push('\t'),
                'n' | 'N' => result.push('\n'),
                'v' | 'V' => result.push('\x0b'),
                'f' | 'F' => result.push('\x0c'),
                'r' | 'R' => result.push('\r'),
                'x' | 'X' => {
                    let first_byte = u8_from_2_hex_chars(
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                    )
                    .map_err(|()| UnexpectedCharInByteEscape)?;

                    let mut bytes = vec![first_byte];

                    // does not guarantee only 4, can do 8, does not check for 10...
                    for _ in 0..first_byte.leading_ones() {
                        match (
                            read(stream)?.ok_or(UnexpectedStringEnd)?,
                            read(stream)?.ok_or(UnexpectedStringEnd)?,
                        ) {
                            ('\\', 'x' | 'X') => {},
                            _ => return Err(UnexpectedCharInByteEscape)?,
                        }

                        bytes.push(
                            u8_from_2_hex_chars(
                                read(stream)?.ok_or(UnexpectedStringEnd)?,
                                read(stream)?.ok_or(UnexpectedStringEnd)?,
                            )
                            .map_err(|()| UnexpectedCharInByteEscape)?,
                        )
                    }

                    result.push_str(
                        &String::from_utf8(bytes).map_err(|_| UnexpectedCharInByteEscape)?,
                    )
                },
                'u' | 'U' => {
                    // read first possibly-surrogate HHHH escape
                    let first_surrogate = u16_from_4_hex_chars(
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                    )
                    .map_err(|()| UnexpectedCharInUnicodeEscape)?;

                    // try checking if first surrogate is enough
                    let unicode_attempt = char::decode_utf16([first_surrogate])
                        .next()
                        .unwrap();
                    if let Ok(ch) = unicode_attempt {
                        result.push(ch);
                        continue;
                    }
                    // not enough - read second \uHHHH escape and try to parse as pair

                    match (
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                    ) {
                        ('\\', 'u' | 'U') => {},
                        _ => return Err(UnexpectedCharInUnicodeEscape)?,
                    }

                    let second_surrogate = u16_from_4_hex_chars(
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                        read(stream)?.ok_or(UnexpectedStringEnd)?,
                    )
                    .map_err(|()| UnexpectedCharInUnicodeEscape)?;

                    result.push(
                        char::decode_utf16([first_surrogate, second_surrogate])
                            .next()
                            .unwrap()
                            .map_err(|_| UnexpectedCharInUnicodeEscape)?,
                    );
                },
                _ => return Err(UnexpectedCharEscapeEnd)?,
            },
            ch if ch == closing_char => return Ok(Some(result)),
            ch => result.push(ch),
        }
    }
}

/// Read a level (`{}`) from the stream.
fn read_level(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<Level>, ParseError> {
    match peek(stream)? {
        Some(('{', accept)) => accept(),
        _ => return Ok(None),
    };

    read_nws(stream)?;

    Ok(Some(read_level_inner(
        stream, true,
    )?))
}

/// Read a sequence of key-LSD pairs from the stream.
fn read_level_inner(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
    level_ends_with_close: bool,
) -> Result<Level, ParseError> {
    use ParseError::*;

    let mut results = Level::default();
    Ok(loop {
        if level_ends_with_close {
            if let Some(('}', accept)) = peek(stream)? {
                accept();
                break results;
            }
        }

        let key = match read_key_path(stream)? {
            Some(key) => key,
            None if level_ends_with_close => return Err(ExpectedKeyOrEnd),
            None => return Ok(results),
        };

        read_nws(stream)?;

        let lsd = read_lsd(stream, Some('}'))?.ok_or(ExpectedLSDAfterKey)?;

        read_nws(stream)?;

        fn merge_level(insert_into: &mut Level, level: Level) -> Result<(), ParseError> {
            for (key, value) in level.into_iter() {
                match value {
                    LSD::Value(value) => insert_into
                        .insert(key.clone(), LSD::Value(value))
                        .is_none()
                        .then_some(())
                        .ok_or_else(|| KeyCollisionKeyAlreadyExists(key))?,
                    LSD::List(list) => insert_into
                        .insert(key.clone(), LSD::List(list))
                        .is_none()
                        .then_some(())
                        .ok_or_else(|| KeyCollisionKeyAlreadyExists(key))?,
                    LSD::Level(lvl) => match insert_into
                        .entry(key)
                        .or_insert_with(|| LSD::Level(Level::default()))
                    {
                        LSD::Value(_) => return Err(KeyCollisionShouldBeLevelButIsNot)?,
                        LSD::List(_) => return Err(KeyCollisionShouldBeLevelButIsNot)?,
                        LSD::Level(ref mut insert_into) => merge_level(insert_into, lvl)?,
                    },
                }
            }
            return Ok(());
        }

        // wrap key-lsd pair in key parts
        let mut result = Level::new();
        let mut insert_into = &mut result;

        for (i, part) in key
            .iter()
            .enumerate()
        {
            let part = part
                .as_str()
                .into();

            if key.len() - 1 == i {
                insert_into.insert(part, lsd);
                break;
            }

            insert_into = match insert_into
                .entry(part)
                .or_insert_with(|| LSD::Level(Level::default()))
            {
                LSD::Level(ref mut lvl) => lvl,
                _ => unreachable!(),
            }
        }

        merge_level(&mut results, result)?;
    })
}

/// Read a key word (word, but also not level or list) from the stream.
fn read_key_word(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<String>, ParseError> {
    let mut result = String::new();
    Ok(loop {
        match peek(stream)? {
            None
            | Some((
                ' ' | '\t' | '\r' | '\n' | '\'' | '"' | '#' | '{' | '}' | '[' | ']' | '.',
                _,
            )) =>
                break result
                    .is_empty()
                    .not()
                    .then_some(result),
            Some((_, accept)) => result.push(accept()),
        }
    })
}

/// Read a key part from the stream.
fn read_key_part(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<String>, ParseError> {
    let mut result = String::new();
    loop {
        if let Some(word) = read_key_word(stream)? {
            result.push_str(&word);
            continue;
        }

        if let Some(string) = read_string(stream)? {
            result.push_str(&string);
            continue;
        }

        break Ok(result
            .is_empty()
            .not()
            .then_some(result));
    }
}

/// Read a key path (separated by `.`) from the stream.
fn read_key_path(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<Vec<String>>, ParseError> {
    use ParseError::*;

    let mut result = vec![match read_key_part(stream)? {
        Some(key_part) => key_part,
        None => return Ok(None),
    }];

    loop {
        let Some(('.', accept)) = peek(stream)? else {
            break;
        };
        accept();

        result.push(read_key_part(stream)?.ok_or(ExpectedKeyPartAfterKeySeparator)?);
    }

    Ok(Some(result))
}

/// Read a list item from the stream.
fn read_list_lsd(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<LSD>, ParseError> {
    if let Some(list) = read_list(stream)? {
        return Ok(Some(LSD::List(list)));
    }

    if let Some(level) = read_level(stream)? {
        return Ok(Some(LSD::Level(level)));
    }

    if let Some(value) = read_list_value(stream)? {
        return Ok(Some(LSD::Value(value)));
    }

    Ok(None)
}

/// Read a list value (same as regular value, but may not contain level or list) from the stream.
fn read_list_value(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<Value>, ParseError> {
    let Some(mut result) = read_key_part(stream)? else {
        return Ok(None);
    };

    Ok(Some(loop {
        let iws = read_iws(stream)?;
        match read_key_part(stream)? {
            Some(part) => {
                // Rust, why no push_string?
                result.push_str(&iws);
                result.push_str(&part);
            },
            None => break result,
        }
    }))
}

/// Read a list (`[]`) from the stream.
fn read_list(
    stream: &mut Peekable<impl Iterator<Item = io::Result<char>>>,
) -> Result<Option<List>, ParseError> {
    use ParseError::*;

    match peek(stream)? {
        Some(('[', accept)) => accept(),
        _ => return Ok(None),
    };

    read_nws(stream)?;

    let mut results = List::default();
    Ok(Some(loop {
        match peek(stream)? {
            Some((']', accept)) => {
                accept();
                break results;
            },
            _ => {},
        }

        results.push(read_list_lsd(stream)?.ok_or(ExpectedListLSDOrEnd)?);

        read_nws(stream)?;
    }))
}

//
// KeyPath
//

/// Key path part - key (string) or index (number).
///
/// Used in [key] macro.
#[derive(Clone, ImplicitClone, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum KeyPathPart {
    /// Key path part - key (string).
    Key(IString),

    /// Key path part - index (number).
    Index(usize),
}

impl Display for KeyPathPart {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            KeyPathPart::Key(part) => write!(f, "{}", part),
            KeyPathPart::Index(part) => write!(f, "{}", part),
        }
    }
}

macro_rules! impl_key_path_part_for_str {
    ($($type:ty),* $(,)?) => {
        $(
            impl From<$type> for KeyPathPart {
                fn from(value: $type) -> Self {
                    value
                        .parse()
                        .map(KeyPathPart::Index)
                        .unwrap_or(KeyPathPart::Key(value.into()))
                }
            }
        )*
    };
}

impl_key_path_part_for_str!(
    &'static str,
    String,
    IString,
    Rc<str>
);

impl From<usize> for KeyPathPart {
    fn from(value: usize) -> Self { Self::Index(value) }
}

pub type KeyPath = [KeyPathPart];

/// Macro for creating key paths.
///
/// You may use `.`, `,` and `;` as separators, as well as spaces.
#[macro_export]
macro_rules! key {
    (@$($collected:expr),*;) => { vec![$($collected),*] };
    (@$($collected:expr),*; . $($rest:tt)*) => {
        $crate::key!(@$($collected),*; $($rest)*)
    };
    (@$($collected:expr),*; , $($rest:tt)*) => {
        $crate::key!(@$($collected),*; $($rest)*)
    };
    (@$($collected:expr),*; ; $($rest:tt)*) => {
        $crate::key!(@$($collected),*; $($rest)*)
    };
    (@$($collected:expr),*; $part:ident $($rest:tt)*) => {
        $crate::key!(
            @$($collected,)* $crate::KeyPathPart::from($part);
            $($rest)*
        )
    };
    (@$($collected:expr),*; $part:literal $($rest:tt)*) => {
        $crate::key!(
            @$($collected,)* $crate::KeyPathPart::from($part);
            $($rest)*
        )
    };
    (@$($collected:expr),*; $($rest:tt)*) => {{
        compile_error!(concat!("unknown key char: ", stringify!($($rest)*)));
    }};

    () => { vec![] };
    ($($rest:tt)*) => { $crate::key!(@; $($rest)*) };
}

//
// Values
//

impl LSD {
    /// Try to interpret this [LSD] as a [Value].
    pub fn as_value(&self) -> Option<&Value> {
        match self {
            LSD::Value(value) => Some(value),
            _ => None,
        }
    }
    /// Mutable version of [LSD::as_value].
    pub fn as_value_mut(&mut self) -> Option<&mut Value> {
        match self {
            LSD::Value(value) => Some(value),
            _ => None,
        }
    }

    /// Try to interpret this [LSD] as a [List].
    pub fn as_list(&self) -> Option<&List> {
        match self {
            LSD::List(list) => Some(list),
            _ => None,
        }
    }

    /// Mutable version of [LSD::as_list].
    pub fn as_list_mut(&mut self) -> Option<&mut List> {
        match self {
            LSD::List(list) => Some(list),
            _ => None,
        }
    }

    /// Try to interpret this [LSD] as a [Level].
    pub fn as_level(&self) -> Option<&Level> {
        match self {
            LSD::Level(level) => Some(level),
            _ => None,
        }
    }

    /// Mutable version of [LSD::as_level].
    pub fn as_level_mut(&mut self) -> Option<&mut Level> {
        match self {
            LSD::Level(level) => Some(level),
            _ => None,
        }
    }
}

/// Extensions for LSD types that can pull deeply nested values.
pub trait LSDGetExt {
    /// Try to get an inner LSD given by the path.
    fn inner(&self, parts: impl Borrow<KeyPath>) -> Option<&LSD>;

    /// Mutable version of [LSDGetExt::inner].
    fn inner_mut(&mut self, parts: impl Borrow<KeyPath>) -> Option<&mut LSD>;

    /// Try to find an inner [LSD::Value] given by the path.
    fn value<E>(
        &self,
        invalid: impl FnOnce() -> E,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<&Value>, E> {
        self.inner(parts)
            .map(LSD::as_value)
            .map(|v| v.ok_or_else(invalid))
            .transpose()
    }

    /// Mutable version of [LSDGetExt::value].
    fn value_mut<E>(
        &mut self,
        invalid: impl FnOnce() -> E,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<&mut Value>, E> {
        self.inner_mut(parts)
            .map(LSD::as_value_mut)
            .map(|v| v.ok_or_else(invalid))
            .transpose()
    }

    /// Try to find a value given by the path, and parse it as the given type.
    fn parsed<T: FromStr, E: Clone>(
        &self,
        invalid: impl FnOnce() -> E + Clone,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<T>, E> {
        self.value(invalid.clone(), parts)?
            .map(Value::as_str)
            .map(str::parse)
            .map(|v| v.map_err(|_| invalid()))
            .transpose()
    }

    /// Try to find an inner [LSD::List] given by the path.
    fn list<E>(
        &self,
        invalid: impl FnOnce() -> E,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<&List>, E> {
        self.inner(parts)
            .map(LSD::as_list)
            .map(|v| v.ok_or_else(invalid))
            .transpose()
    }

    /// Mutable version of [LSDGetExt::list].
    fn list_mut<E>(
        &mut self,
        invalid: impl FnOnce() -> E,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<&mut List>, E> {
        self.inner_mut(parts)
            .map(LSD::as_list_mut)
            .map(|v| v.ok_or_else(invalid))
            .transpose()
    }

    /// Try to find an inner [LSD::Level] given by the path.
    fn level<E>(
        &self,
        invalid: impl FnOnce() -> E,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<&Level>, E> {
        self.inner(parts)
            .map(LSD::as_level)
            .map(|v| v.ok_or_else(invalid))
            .transpose()
    }

    /// Mutable version of [LSDGetExt::level].
    fn level_mut<E>(
        &mut self,
        invalid: impl FnOnce() -> E,
        parts: impl Borrow<KeyPath>,
    ) -> Result<Option<&mut Level>, E> {
        self.inner_mut(parts)
            .map(LSD::as_level_mut)
            .map(|v| v.ok_or_else(invalid))
            .transpose()
    }
}

impl LSDGetExt for LSD {
    fn inner(&self, parts: impl Borrow<KeyPath>) -> Option<&LSD> {
        match parts
            .borrow()
            .len()
        {
            0 => Some(self),
            _ => match self {
                LSD::Level(level) => level.inner(parts),
                LSD::List(list) => list.inner(parts),
                LSD::Value(_) => None,
            },
        }
    }

    fn inner_mut(&mut self, parts: impl Borrow<KeyPath>) -> Option<&mut LSD> {
        match parts
            .borrow()
            .len()
        {
            0 => Some(self),
            _ => match self {
                LSD::Level(level) => level.inner_mut(parts),
                LSD::List(list) => list.inner_mut(parts),
                LSD::Value(_) => None,
            },
        }
    }
}

impl LSDGetExt for Level {
    fn inner(&self, parts: impl Borrow<KeyPath>) -> Option<&LSD> {
        parts
            .borrow()
            .split_first()
            .and_then(|(key, rest)| {
                self.get(&key.to_string())
                    .and_then(|lsd| lsd.inner(rest))
            })
    }

    fn inner_mut(&mut self, parts: impl Borrow<KeyPath>) -> Option<&mut LSD> {
        parts
            .borrow()
            .split_first()
            .and_then(|(key, rest)| {
                self.get_mut(&key.to_string())
                    .and_then(|lsd| lsd.inner_mut(rest))
            })
    }
}

impl LSDGetExt for List {
    fn inner(&self, parts: impl Borrow<KeyPath>) -> Option<&LSD> {
        parts
            .borrow()
            .split_first()
            .and_then(|(key, rest)| match key {
                KeyPathPart::Index(i) => self
                    .get(*i)
                    .and_then(|lsd| lsd.inner(rest)),
                KeyPathPart::Key(_) => None,
            })
    }

    fn inner_mut(&mut self, parts: impl Borrow<KeyPath>) -> Option<&mut LSD> {
        parts
            .borrow()
            .split_first()
            .and_then(|(key, rest)| match key {
                KeyPathPart::Index(i) => self
                    .get_mut(*i)
                    .and_then(|lsd| lsd.inner_mut(rest)),
                KeyPathPart::Key(_) => None,
            })
    }
}
