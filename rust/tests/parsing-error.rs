use std::io::Cursor;

use lsdata::ParseError::*;
use lsdata::LSD;

#[test]
fn unexpected_char_at_file_end() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"[] test"#)),
        Err(UnexpectedCharAtFileEnd)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(r#"{} test"#)),
        Err(UnexpectedCharAtFileEnd)
    ));
}

#[test]
fn unexpected_string_end() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test ""#)),
        Err(UnexpectedStringEnd)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\u"#)),
        Err(UnexpectedStringEnd)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\udfff"#)),
        Err(UnexpectedStringEnd)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\x"#)),
        Err(UnexpectedStringEnd)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\xff"#)),
        Err(UnexpectedStringEnd)
    ));
}

#[test]
fn unexpected_char_escape_end() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\"#)),
        Err(UnexpectedCharEscapeEnd)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\j"#)),
        Err(UnexpectedCharEscapeEnd)
    ));
}

#[test]
fn unexpected_char_in_byte_escape() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"test "\xffNO"#)),
        Err(UnexpectedCharInByteEscape)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(
            r#"test "\xf0\x00\x00\x00\x00""#
        )),
        Err(UnexpectedCharInByteEscape)
    ));
}

#[test]
fn unexpected_char_in_unicode_escape() {
    assert!(matches!(
        LSD::parse(Cursor::new(
            r#"test "\udfffNO""#
        )),
        Err(UnexpectedCharInUnicodeEscape)
    ));
    assert!(matches!(
        LSD::parse(Cursor::new(
            r#"test "\udfff\udfff""#
        )),
        Err(UnexpectedCharInUnicodeEscape)
    ));
}

#[test]
fn expected_key_or_end() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"{"#)),
        Err(ExpectedKeyOrEnd)
    ));
}

#[test]
fn expected_key_part_after_key_separator() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"{a."#)),
        Err(ExpectedKeyPartAfterKeySeparator)
    ));
}

#[test]
fn expected_lsd_after_key() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"{a "#)),
        Err(ExpectedLSDAfterKey)
    ));
}

#[test]
fn expected_list_item_or_end() {
    assert!(matches!(
        LSD::parse(Cursor::new(r#"["#)),
        Err(ExpectedListLSDOrEnd)
    ));
}

#[test]
fn key_collision_should_be_level_but_is_not() {
    assert!(matches!(
        LSD::parse(Cursor::new(
            r#"
                a 10
                a.b 20
            "#
        )),
        Err(KeyCollisionShouldBeLevelButIsNot)
    ));
}

#[test]
fn key_collision_key_already_exists() {
    assert!(matches!(
        LSD::parse(Cursor::new(
            r#"
                a 10
                a 20
            "#
        )),
        Err(KeyCollisionKeyAlreadyExists(
            ..
        ))
    ));
}
