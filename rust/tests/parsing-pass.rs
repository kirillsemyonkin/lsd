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
                format!("a"),
                LSD::Value(format!("10"))
            ),
            (
                format!("b"),
                LSD::Value(format!("20"))
            ),
            (
                format!("c"),
                LSD::Value(format!(
                    "a  test string\nand spaces  b"
                ))
            ),
            (
                format!("d"),
                LSD::Value(format!(r#"also"string"#))
            ),
            (
                format!("glued key"),
                LSD::Value(format!("test"))
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
                format!("a"),
                LSD::Level(Level::from([(
                    format!("a"),
                    LSD::Value(format!("10")),
                )]))
            ),
            (
                format!("b"),
                LSD::Level(Level::default())
            ),
            (
                format!("c"),
                LSD::Level(Level::from([(
                    format!("c"),
                    LSD::Value(format!("30")),
                )]))
            ),
            (
                format!("d"),
                LSD::Level(Level::from([
                    (
                        format!("d"),
                        LSD::Value(format!("40")),
                    ),
                    (
                        format!("2"),
                        LSD::Value(format!("50")),
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
        "#,
    );

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd,
        LSD::Level(Level::from([
            (
                format!("a"),
                LSD::List(List::from([LSD::Value(
                    format!("a 10")
                )]))
            ),
            (
                format!("b"),
                LSD::List(List::default())
            ),
            (
                format!("c"),
                LSD::List(List::from([
                    LSD::Value(format!("1 2")),
                    LSD::Level(Level::default()),
                    LSD::Value(format!("3 4"))
                ]))
            ),
            (
                format!("d"),
                LSD::List(List::from([
                    LSD::Value(format!("1 2")),
                    LSD::Value(format!("3 4"))
                ]))
            ),
        ]))
    );
}
