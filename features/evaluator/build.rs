use std::path::Path;
use std::process::Command;

fn main() {
    let actions_dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../actions");
    let dist_dir = actions_dir.join("dist");

    std::fs::create_dir_all(&dist_dir).ok();

    let rust_actions = [
        ("safety-block", "safety_block_action.wasm"),
        ("safety-warn", "safety_warn_action.wasm"),
        ("assembly-filter", "assembly_filter_action.wasm"),
    ];

    for (crate_name, output_name) in &rust_actions {
        let crate_dir = actions_dir.join(crate_name);
        let src_file = crate_dir.join("src/lib.rs");
        let wit_file = crate_dir.join("wit/evaluator.wit");
        let output = dist_dir.join(output_name);

        println!("cargo:rerun-if-changed={}", src_file.display());
        println!("cargo:rerun-if-changed={}", wit_file.display());
        println!("cargo:rerun-if-changed={}", output.display());

        if output.exists() {
            continue;
        }

        eprintln!("Compiling WASM action: {crate_name}");

        let status = Command::new("cargo")
            .arg("component")
            .arg("build")
            .arg("--release")
            .current_dir(&crate_dir)
            .env("HOME", std::env::temp_dir())
            .status();

        match status {
            Ok(s) if s.success() => {
                let built = crate_dir
                    .join("target/wasm32-wasip1/release")
                    .join(output_name);
                if built.exists() {
                    std::fs::copy(&built, &output).ok();
                }
            }
            Ok(s) => {
                println!(
                    "cargo:warning=Failed to compile {crate_name}: exit code {}",
                    s.code().unwrap_or(-1)
                );
            }
            Err(e) => {
                println!("cargo:warning=cargo-component not available ({e}), skipping {crate_name}");
            }
        }
    }
}
