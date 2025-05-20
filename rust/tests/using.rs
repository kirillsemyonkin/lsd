use std::io::Cursor;

use lsdata::key;
use lsdata::LSDGetExt;
use lsdata::LSD;

#[test]
fn parse() {
    let text = Cursor::new("a 10");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd.parsed::<u8, _>(|| (), key!["a"])
            .unwrap()
            .unwrap(),
        10
    );
}

#[test]
fn level() {
    let text = Cursor::new("a { b 10 }");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd.level(|| (), key!["a"])
            .unwrap()
            .unwrap()
            .get("b")
            .unwrap(),
        LSD::Value("10".to_string())
    );
    assert_eq!(
        lsd.value(|| (), key!["a" "b"])
            .unwrap()
            .unwrap(),
        "10"
    );
}

#[test]
fn list() {
    let text = Cursor::new("a [ 10 ]");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd.list(|| (), key!["a"])
            .unwrap()
            .unwrap()[0],
        LSD::Value("10".to_string())
    );
    assert_eq!(
        lsd.value(|| (), key!["a" 0])
            .unwrap()
            .unwrap(),
        "10"
    );
}

#[test]
fn nested() {
    let text = Cursor::new("a [ { a 10 } ]");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd.value(|| (), key!["a" "0" "a"])
            .unwrap()
            .unwrap(),
        "10"
    );
}

#[test]
fn dynamic() {
    let text = Cursor::new(
        r#"
            a [
                10
                20
            ]
        "#,
    );

    let lsd = LSD::parse(text).unwrap();

    let id = 1;
    assert_eq!(
        lsd.value(|| (), key!["a" id])
            .unwrap()
            .unwrap(),
        "20"
    );
}

#[test]
fn syntaxes() {
    let text = Cursor::new("a { b 10 }");

    let lsd = LSD::parse(text).unwrap();

    assert_eq!(
        lsd.value(|| (), key!["a" "b"])
            .unwrap()
            .unwrap(),
        "10"
    );
    assert_eq!(
        lsd.value(|| (), key!["a"."b"])
            .unwrap()
            .unwrap(),
        "10"
    );
    assert_eq!(
        lsd.value(|| (), key!["a", "b"])
            .unwrap()
            .unwrap(),
        "10"
    );
    assert_eq!(
        lsd.value(|| (), key!["a"; "b"])
            .unwrap()
            .unwrap(),
        "10"
    );
}
