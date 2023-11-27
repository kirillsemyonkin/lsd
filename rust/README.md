# LSD

This is a Rust implementation of LSD (Less Syntax Data) configuration/data transfer format.

This is first implementation ever. If there ever will be any other implementations in this
repository, they will most likely be a copy of this implementation.

## Installation

### Cargo/Crates

Main way of adding LSD to your Rust projects is via official crates.io repository.

#### Command

```sh
cargo add lsdata
```

#### Cargo.toml

Precise version (example; in case this README is not updated **copy from
[crates.io page](https://crates.io/crates/lsdata)**):

```toml
lsdata = "0.1.0"
```

Latest (for quick personal projects, not production):

```toml
lsdata = "*"
```

### From GitHub

You may also let cargo build development LSD directly off of the GitHub branch.

#### Command

```sh
cargo add --git https://github.com/kirillsemyonkin/lsd.git
```

#### Cargo.toml

```toml
lsdata = { git = "https://github.com/kirillsemyonkin/lsd.git" }
```

### From local folder

If you have a local variant you are working on, you may also refer to it instead.

#### Command

```sh
cargo add --path path/to/lsd/rust
```

#### Cargo.toml

```toml
lsdata = { path = "path/to/lsd/rust" }
```

## Usage

Once you got LSD into your Rust project, import (`use`) it in your code:

```rust
use lsdata::LSD; // Just LSD enum itself
use lsdata::LSD::*; // LSD variants (LSD::Level, LSD::List, LSD::Value)
use lsdata::key; // `key!` macro
use lsdata::*; // Import everything directly into your scope (except those variants)
```

There is one `parse` method available for you:

```rust
let file_lsd = LSD::parse(File::open("example.lsd")?)?;
let string_lsd = LSD::parse(Cursor::new("example Hello world!"))?; 
```

To access values, it is useful to have `key!` macro ready:

```rust
let lang_key = "rust";
let lang_name = lsd
    .value(
        key!["languages" lang_key "name"],
        || LanguageNameIsNotAValue,
    )?
    .ok_or_else(|| CouldNotFindLanguageName)?;
```

Check out [documentation](https://docs.rs/lsdata/) to see more of the API.

## Planned

- [ ] Saving to a file
- [ ] `serde` support
