use std::path::Path;
use std::process::Command;

fn main() {
    let ui_dir = Path::new("../ui/admin");
    let dist_index = ui_dir.join("dist/index.html");

    // When dist/index.html is missing, cargo always reruns this script.
    println!("cargo:rerun-if-changed=../ui/admin/dist/index.html");
    println!("cargo:rerun-if-changed=../ui/admin/src");
    println!("cargo:rerun-if-changed=../ui/admin/package.json");
    println!("cargo:rerun-if-changed=../ui/admin/yarn.lock");
    println!("cargo:rerun-if-changed=../ui/admin/vite.config.ts");

    if dist_index.exists() {
        return;
    }

    eprintln!("ui/admin/dist not found — building admin UI with yarn");

    let yarn = which_yarn();

    let install = Command::new(&yarn)
        .args(["install", "--frozen-lockfile"])
        .current_dir(ui_dir)
        .status();

    match install {
        Ok(s) if s.success() => {}
        Ok(s) => {
            eprintln!("yarn install failed with {s}");
            std::process::exit(1);
        }
        Err(e) => {
            eprintln!("failed to run yarn install: {e}");
            std::process::exit(1);
        }
    }

    let build = Command::new(&yarn)
        .arg("build")
        .current_dir(ui_dir)
        .status();

    match build {
        Ok(s) if s.success() => {}
        Ok(s) => {
            eprintln!("yarn build failed with {s}");
            std::process::exit(1);
        }
        Err(e) => {
            eprintln!("failed to run yarn build: {e}");
            std::process::exit(1);
        }
    }
}

fn which_yarn() -> String {
    for name in ["yarn", "yarnpkg"] {
        if Command::new(name)
            .arg("--version")
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .is_ok_and(|s| s.success())
        {
            return name.to_owned();
        }
    }

    eprintln!(
        "\n\
         error: yarn not found.\n\
         \n\
         The admin UI needs to be built before the Rust binary can compile.\n\
         Install Node.js and Yarn, then run:\n\
         \n\
         cd ui/admin && yarn install && yarn build\n"
    );
    std::process::exit(1);
}
