use std::io::{self, Write};
use std::path::PathBuf;

use clap::Parser;

#[derive(Debug, Parser)]
#[command(
    name = "wanaku-openapi",
    about = "Export the Wanaku OpenAPI spec or check a snapshot"
)]
struct Args {
    /// Compare PATH with the generated spec without changing the file.
    #[arg(long, requires = "path")]
    check: bool,
    /// Write the spec to PATH. Omit to write to standard output.
    path: Option<PathBuf>,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();
    let spec = wanaku_server::openapi::openapi_json();
    export(&args, &spec)
}

fn export(args: &Args, spec: &[u8]) -> Result<(), Box<dyn std::error::Error>> {
    match &args.path {
        Some(path) if args.check => {
            if std::fs::read(path)? != spec {
                return Err(format!("OpenAPI snapshot differs: {}", path.display()).into());
            }
        }
        Some(path) => std::fs::write(path, spec)?,
        None => io::stdout().lock().write_all(spec)?,
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn writes_and_checks_snapshot_without_modifying_drift() -> Result<(), Box<dyn std::error::Error>>
    {
        let directory = tempfile::tempdir()?;
        let path = directory.path().join("openapi.json");
        let mut args = Args {
            check: false,
            path: Some(path.clone()),
        };
        export(&args, b"generated")?;
        assert_eq!(std::fs::read(&path)?, b"generated");
        args.check = true;
        export(&args, b"generated")?;
        assert!(export(&args, b"changed").is_err());
        assert_eq!(std::fs::read(&path)?, b"generated");
        std::fs::remove_file(&path)?;
        assert!(export(&args, b"generated").is_err());
        assert!(!path.exists());
        Ok(())
    }

    #[test]
    fn rejects_invalid_arguments() {
        for arguments in [
            vec!["wanaku-openapi", "--check"],
            vec!["wanaku-openapi", "--unknown"],
            vec!["wanaku-openapi", "first.json", "second.json"],
        ] {
            assert!(Args::try_parse_from(arguments).is_err());
        }
    }

    #[test]
    fn reports_write_failures() -> Result<(), Box<dyn std::error::Error>> {
        let directory = tempfile::tempdir()?;
        let args = Args {
            check: false,
            path: Some(directory.path().to_path_buf()),
        };
        assert!(export(&args, b"generated").is_err());
        Ok(())
    }
}
