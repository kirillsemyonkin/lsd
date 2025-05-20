use std::io::Cursor;

use lsdata::Level;
use lsdata::List;
use lsdata::LSD;

#[test]
fn nothing() {
    let text = Cursor::new(r#""#);

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(lsd, LSD::default());
}

#[test]
fn comment() {
    let text = Cursor::new(
        r#"
            # test1
            # test2
        "#,
    );

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(lsd, LSD::default());
}

#[test]
fn list() {
    let text = Cursor::new("[]");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd,
        LSD::List(List::default())
    );
}

#[test]
fn level() {
    let text = Cursor::new("{}");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd,
        LSD::Level(Level::default())
    );
}

#[test]
fn basic() {
    let text = Cursor::new(
        r#"
            a 10 # comment
            b 20
            c a  "test string\nand spaces"  b
            d 'also"string'
            glued" key" test
        "#,
    );

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd,
        LSD::Level(Level::from([
            (
                "a".to_string(),
                LSD::Value("10".to_string())
            ),
            (
                "b".to_string(),
                LSD::Value("20".to_string())
            ),
            (
                "c".to_string(),
                LSD::Value("a  test string\nand spaces  b".to_string())
            ),
            (
                "d".to_string(),
                LSD::Value(r#"also"string"#.to_string())
            ),
            (
                "glued key".to_string(),
                LSD::Value("test".to_string())
            ),
        ]))
    );
}

#[test]
fn nested_level() {
    let text = Cursor::new(
        r#"
            a {
                a 10
            }
            b{}
            c{ c 30 }
            d{ d 40
               2 50 }
        "#,
    );

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd,
        LSD::Level(Level::from([
            (
                "a".to_string(),
                LSD::Level(Level::from([(
                    "a".to_string(),
                    LSD::Value("10".to_string()),
                )]))
            ),
            (
                "b".to_string(),
                LSD::Level(Level::default())
            ),
            (
                "c".to_string(),
                LSD::Level(Level::from([(
                    "c".to_string(),
                    LSD::Value("30".to_string()),
                )]))
            ),
            (
                "d".to_string(),
                LSD::Level(Level::from([
                    (
                        "d".to_string(),
                        LSD::Value("40".to_string()),
                    ),
                    (
                        "2".to_string(),
                        LSD::Value("50".to_string()),
                    )
                ]))
            ),
        ]))
    );
}

#[test]
fn nested_list() {
    let text = Cursor::new(
        r#"
            a [
                a 10
            ]
            b[]
            c[ 1 2 {} 3 4 ]
            d[ 1 2
               3 4 ]
            e[ 1.2 ]
        "#,
    );

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd,
        LSD::Level(Level::from([
            (
                "a".to_string(),
                LSD::List(List::from([LSD::Value(
                    "a 10".to_string()
                )]))
            ),
            (
                "b".to_string(),
                LSD::List(List::default())
            ),
            (
                "c".to_string(),
                LSD::List(List::from([
                    LSD::Value("1 2".to_string()),
                    LSD::Level(Level::default()),
                    LSD::Value("3 4".to_string())
                ]))
            ),
            (
                "d".to_string(),
                LSD::List(List::from([
                    LSD::Value("1 2".to_string()),
                    LSD::Value("3 4".to_string())
                ]))
            ),
            (
                "e".to_string(),
                LSD::List(List::from([LSD::Value(
                    "1.2".to_string()
                )]))
            ),
        ]))
    );
}
