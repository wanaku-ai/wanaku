//! Wanaku build task runner.
//!
//! Invoke via Cargo aliases (`.cargo/config.toml`):
//!
//! ```
//! cargo build-image                          # build for native arch, push <tag>-<arch>
//! cargo build-image --variant headless       # headless variant
//! cargo build-image --target minikube        # load into Minikube daemon (plain tag, no push)
//! cargo build-image --target openshift       # build locally, push to OCP registry
//! cargo build-image-headless                 # shorthand for --variant headless
//! cargo build-image-manifest                 # assemble multi-arch manifest list
//! ```

use std::collections::HashMap;
use std::process::{Command, Stdio};

use anyhow::{Context, anyhow, bail};
use clap::{Parser, Subcommand, ValueEnum};

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    match cli.command {
        Commands::BuildImage(args) => build_image(&args),
        Commands::BuildImageManifest(args) => build_image_manifest(&args),
    }
}

// ---------------------------------------------------------------------------
// CLI definition
// ---------------------------------------------------------------------------

#[derive(Debug, Parser)]
#[command(name = "xtask", about = "Wanaku build task runner")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Debug, Subcommand)]
enum Commands {
    /// Build a container image for the current native architecture.
    ///
    /// The tag is always suffixed with the host architecture, e.g.
    /// `quay.io/wanaku/wanaku-server:latest` becomes
    /// `quay.io/wanaku/wanaku-server:latest-x86_64` on an x86_64 machine.
    ///
    /// Exception: the minikube target loads the image into the cluster daemon
    /// with the plain --tag value (no suffix, no push).
    BuildImage(BuildImageArgs),

    /// Assemble a multi-arch manifest list from existing arch-specific images.
    ///
    /// Requires both `<tag>-aarch64` and `<tag>-x86_64` to already be in the
    /// registry. Assembles them into a single manifest list at `<tag>`.
    BuildImageManifest(BuildImageManifestArgs),
}

#[derive(Debug, Parser)]
pub struct BuildImageArgs {
    /// Image variant to build.
    #[arg(long, value_enum, default_value_t = Variant::Full)]
    variant: Variant,

    /// Base image tag. The native architecture suffix is appended automatically.
    /// For openshift, the tag suffix also determines the registry image name.
    /// minikube: the plain tag is used without a suffix.
    #[arg(long, default_value = "quay.io/wanaku/wanaku-server:latest")]
    tag: String,

    /// Container build target environment.
    #[arg(long, value_enum, default_value_t = Target::Docker)]
    target: Target,
}

#[derive(Debug, Parser)]
pub struct BuildImageManifestArgs {
    /// Base image tag whose arch-specific variants will be assembled.
    /// Must match the --tag value used with `cargo build-image`.
    #[arg(long, default_value = "quay.io/wanaku/wanaku-server:latest")]
    tag: String,

    /// Image variant (used to identify the correct image name for openshift).
    #[arg(long, value_enum, default_value_t = Variant::Full)]
    variant: Variant,

    /// Container target environment for the manifest push.
    /// minikube is not supported for manifest assembly.
    #[arg(long, value_enum, default_value_t = ManifestTarget::Docker)]
    target: ManifestTarget,
}

#[derive(Debug, Clone, ValueEnum)]
pub enum Variant {
    Full,
    Headless,
}

impl Variant {
    const fn as_str(&self) -> &'static str {
        match self {
            Self::Full => "full",
            Self::Headless => "headless",
        }
    }
}

#[derive(Debug, Clone, ValueEnum)]
pub enum Target {
    /// Use local Docker (falls back to Podman if docker is not on PATH).
    Docker,
    /// Use Minikube's internal Docker daemon. Loads with plain tag; no push.
    Minikube,
    /// Build locally with Podman, then push to the OpenShift internal registry.
    Openshift,
}

#[derive(Debug, Clone, ValueEnum)]
pub enum ManifestTarget {
    /// Assemble the manifest using podman and push to a plain registry.
    Docker,
    /// Assemble the manifest using podman and push to the OpenShift internal registry.
    Openshift,
}

// ---------------------------------------------------------------------------
// Dispatch
// ---------------------------------------------------------------------------

fn build_image(args: &BuildImageArgs) -> anyhow::Result<()> {
    match args.target {
        Target::Docker => build_docker(&args.variant, &args.tag),
        Target::Minikube => build_minikube(&args.variant, &args.tag),
        Target::Openshift => build_openshift(&args.variant, &args.tag),
    }
}

fn build_image_manifest(args: &BuildImageManifestArgs) -> anyhow::Result<()> {
    match args.target {
        ManifestTarget::Docker => manifest_docker(&args.tag),
        ManifestTarget::Openshift => manifest_openshift(&args.variant, &args.tag),
    }
}

// ---------------------------------------------------------------------------
// Strategy: Docker / Podman
// ---------------------------------------------------------------------------
//
// Always builds for the current native architecture and pushes with an
// arch suffix, e.g. <tag>-x86_64 or <tag>-aarch64.

fn build_docker(variant: &Variant, tag: &str) -> anyhow::Result<()> {
    let tool = resolve_docker_tool()?;
    let arch = native_arch()?;
    let arch_tag = format!("{tag}-{arch}");

    println!("Building image with {tool} (variant={}, arch={arch}, tag={arch_tag})", variant.as_str());

    let status = Command::new(&tool)
        .args([
            "build",
            "-f", "Containerfile",
            "--build-arg", &format!("VARIANT={}", variant.as_str()),
            "-t", &arch_tag,
            ".",
        ])
        .status()
        .with_context(|| format!("failed to spawn `{tool} build`"))?;
    if !status.success() {
        bail!("{tool} build failed with exit code {}", status.code().unwrap_or(-1));
    }

    println!("\nPushing {arch_tag}...");
    let status = Command::new(&tool)
        .args(["push", &arch_tag])
        .status()
        .with_context(|| format!("failed to spawn `{tool} push`"))?;
    if !status.success() {
        bail!("{tool} push failed with exit code {}", status.code().unwrap_or(-1));
    }

    print_manifest_next_steps(tag, &arch);
    Ok(())
}

// ---------------------------------------------------------------------------
// Strategy: Minikube
// ---------------------------------------------------------------------------
//
// Loads the image directly into Minikube's cluster daemon with the plain tag.
// No arch suffix is applied; no push occurs.

#[expect(clippy::too_many_lines, reason = "sequential cluster checks are inherently linear")]
fn build_minikube(variant: &Variant, tag: &str) -> anyhow::Result<()> {
    require_tool("minikube", "https://minikube.sigs.k8s.io/docs/start/")?;

    // Verify the cluster is running.
    let status = Command::new("minikube")
        .arg("status")
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .context("failed to run `minikube status`")?;
    if !status.success() {
        bail!("Minikube cluster is not running. Start it with: minikube start");
    }

    // Capture docker-env variables without shell syntax.
    let output = Command::new("minikube")
        .args(["docker-env", "--shell", "none"])
        .output()
        .context("failed to run `minikube docker-env --shell none`")?;
    if !output.status.success() {
        bail!(
            "minikube docker-env failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    let docker_env = parse_env_lines(&String::from_utf8_lossy(&output.stdout));
    println!(
        "Building image inside Minikube daemon (variant={}, tag={tag})",
        variant.as_str()
    );

    // Minikube uses a Docker-protocol daemon; podman is not a valid fallback here.
    let tool = which("docker").ok_or_else(|| {
        anyhow!("docker not found on PATH — required to target Minikube. Install docker CLI.")
    })?;

    let status = Command::new(&tool)
        .args([
            "build",
            "-f", "Containerfile",
            "--build-arg", &format!("VARIANT={}", variant.as_str()),
            "-t", tag,
            ".",
        ])
        .envs(&docker_env)
        .status()
        .with_context(|| format!("failed to spawn {tool}"))?;

    if !status.success() {
        bail!("{tool} build (minikube) failed with exit code {}", status.code().unwrap_or(-1));
    }
    Ok(())
}

/// Parse `KEY=VALUE` lines (output of `minikube docker-env --shell none`).
fn parse_env_lines(output: &str) -> HashMap<String, String> {
    output
        .lines()
        .filter_map(|line| {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                return None;
            }
            let (key, value) = line.split_once('=')?;
            Some((key.to_owned(), value.to_owned()))
        })
        .collect()
}

// ---------------------------------------------------------------------------
// Strategy: OpenShift — build locally, push to internal registry
// ---------------------------------------------------------------------------
//
// WHY NOT `oc start-build --from-dir` (S2I binary source strategy)?
//
// Two fundamental problems with that approach for this project:
//
// 1. EPHEMERAL STORAGE: the Containerfile performs a full Rust compilation
//    inside the build pod — it downloads the Rust 1.96 toolchain (~500 MB),
//    fetches all crates, and compiles a large dependency tree. The build pod
//    needs >10 GB of ephemeral storage. OpenShift cluster nodes typically
//    enforce 5–6 GB ephemeral-storage limits, so the build pod is evicted
//    before it finishes (event: "node was low on resource: ephemeral-storage").
//
// 2. BUILD ARGS IGNORED: `oc start-build --from-dir` uses the S2I binary
//    source strategy, which silently drops --build-arg, --build-loglevel, and
//    env vars. The VARIANT build arg is never forwarded to the Containerfile
//    ("WARNING: Specifying build arguments with binary builds is not supported").
//
// CORRECT APPROACH: build the image locally with Podman (full Rust compile
// happens on the developer machine where cargo caches already exist), then push
// only the final ~100 MB runtime image to the OpenShift internal registry.

#[expect(clippy::too_many_lines, reason = "sequential cluster setup steps are inherently linear")]
fn build_openshift(variant: &Variant, tag: &str) -> anyhow::Result<()> {
    require_tool("oc", "https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html")?;

    // OpenShift target always uses podman.
    let tool = require_podman()?;

    let (oc_user, registry_host, namespace, token) = oc_login_info()?;

    let tag_suffix = tag.rsplit_once(':').map_or("latest", |(_, t)| t);
    let image_ref = format!("{registry_host}/{namespace}/wanaku-server:{tag_suffix}");

    let arch = native_arch()?;
    let arch_image_ref = format!("{image_ref}-{arch}");
    let arch_tag = format!("{tag}-{arch}");

    println!("OpenShift registry : {registry_host}");
    println!("Namespace          : {namespace}");
    println!("Registry image ref : {arch_image_ref}");
    println!("Local tag          : {arch_tag}");

    // Log in to the OpenShift internal registry.
    println!("\nLogging {tool} into {registry_host} as {oc_user}...");
    let status = Command::new(&tool)
        .args([
            "login",
            "--tls-verify=false",
            "-u", &oc_user,
            "-p", &token,
            &registry_host,
        ])
        .status()
        .with_context(|| format!("failed to spawn `{tool} login`"))?;
    if !status.success() {
        bail!(
            "`{tool} login` to {registry_host} failed with exit code {}",
            status.code().unwrap_or(-1)
        );
    }

    println!("\nBuilding image locally with {tool} (variant={}, arch={arch})...", variant.as_str());
    let status = Command::new(&tool)
        .args([
            "build",
            "-f", "Containerfile",
            "--build-arg", &format!("VARIANT={}", variant.as_str()),
            "-t", &arch_image_ref,
            "-t", &arch_tag,
            ".",
        ])
        .status()
        .with_context(|| format!("failed to spawn `{tool} build`"))?;
    if !status.success() {
        bail!("{tool} build failed with exit code {}", status.code().unwrap_or(-1));
    }

    println!("\nPushing {arch_image_ref}...");
    let status = Command::new(&tool)
        .args(["push", "--tls-verify=false", &arch_image_ref])
        .status()
        .with_context(|| format!("failed to spawn `{tool} push`"))?;
    if !status.success() {
        bail!("{tool} push failed with exit code {}", status.code().unwrap_or(-1));
    }

    println!("\nImage pushed successfully.");
    println!("  Registry reference : {arch_image_ref}");
    print_manifest_next_steps(&image_ref, &arch);
    Ok(())
}

// ---------------------------------------------------------------------------
// Manifest assembly
// ---------------------------------------------------------------------------

/// Assemble a multi-arch manifest for a plain registry using podman.
fn manifest_docker(tag: &str) -> anyhow::Result<()> {
    let tool = require_podman()?;
    assemble_manifest(tag, &tool)
}

/// Assemble a multi-arch manifest for the OpenShift internal registry.
fn manifest_openshift(variant: &Variant, tag: &str) -> anyhow::Result<()> {
    require_tool("oc", "https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html")?;
    let tool = require_podman()?;

    let (oc_user, registry_host, namespace, token) = oc_login_info()?;

    let tag_suffix = tag.rsplit_once(':').map_or("latest", |(_, t)| t);
    // For OpenShift the manifest target is the registry image ref, not the local tag.
    // The variant is passed so the image name matches what was pushed by build_openshift.
    let image_name = match variant {
        Variant::Full => "wanaku-server",
        Variant::Headless => "wanaku-server-headless",
    };
    let image_ref = format!("{registry_host}/{namespace}/{image_name}:{tag_suffix}");

    println!("OpenShift registry : {registry_host}");
    println!("Namespace          : {namespace}");
    println!("Manifest target    : {image_ref}");

    println!("\nLogging {tool} into {registry_host} as {oc_user}...");
    let status = Command::new(&tool)
        .args([
            "login",
            "--tls-verify=false",
            "-u", &oc_user,
            "-p", &token,
            &registry_host,
        ])
        .status()
        .with_context(|| format!("failed to spawn `{tool} login`"))?;
    if !status.success() {
        bail!(
            "`{tool} login` to {registry_host} failed with exit code {}",
            status.code().unwrap_or(-1)
        );
    }

    assemble_manifest(&image_ref, &tool)
}

// ---------------------------------------------------------------------------
// Multi-arch helpers
// ---------------------------------------------------------------------------

/// Detect the current machine's native CPU architecture.
///
/// Returns the raw `uname -m` value for recognised architectures:
/// `"x86_64"`, `"aarch64"` (Linux ARM64), or `"arm64"` (macOS ARM64).
/// Errors on unrecognised values.
fn native_arch() -> anyhow::Result<String> {
    let out = Command::new("uname")
        .arg("-m")
        .output()
        .context("failed to run `uname -m`")?;
    if !out.status.success() {
        bail!("`uname -m` failed");
    }
    let raw = String::from_utf8_lossy(&out.stdout).trim().to_owned();
    match raw.as_str() {
        "x86_64" | "aarch64" | "arm64" | "ppc64le" => Ok(raw),
        other => bail!(
            "unrecognised architecture `{other}` from `uname -m`. \
             Supported values: x86_64, aarch64, arm64, ppc64le"
        ),
    }
}

/// Assemble a multi-arch manifest list at `tag` from `<tag>-aarch64` and `<tag>-x86_64`.
///
/// Mirrors `build-manifests.sh`: remove any stale local manifest, create the manifest
/// list, then push it.
fn assemble_manifest(tag: &str, tool: &str) -> anyhow::Result<()> {
    let aarch64_tag = format!("{tag}-aarch64");
    let x86_64_tag = format!("{tag}-x86_64");

    println!("\nAssembling multi-arch manifest at {tag}...");
    println!("  aarch64 : {aarch64_tag}");
    println!("  x86_64  : {x86_64_tag}");

    // Remove any stale local manifest (ignore failure — it may not exist).
    let _ = Command::new(tool)
        .args(["rmi", "-f", tag])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();

    for image in [&aarch64_tag, &x86_64_tag] {
        println!("Pulling {image}...");
        let status = Command::new(tool)
            .args(["pull", image])
            .status()
            .with_context(|| format!("failed to spawn `{tool} pull {image}`"))?;
        if !status.success() {
            bail!(
                "`{tool} pull {image}` failed with exit code {}",
                status.code().unwrap_or(-1)
            );
        }
    }

    let status = Command::new(tool)
        .args(["manifest", "create", tag, &aarch64_tag, &x86_64_tag])
        .status()
        .with_context(|| format!("failed to spawn `{tool} manifest create`"))?;
    if !status.success() {
        bail!("`{tool} manifest create` failed with exit code {}", status.code().unwrap_or(-1));
    }

    let status = Command::new(tool)
        .args(["manifest", "push", "--all", tag])
        .status()
        .with_context(|| format!("failed to spawn `{tool} manifest push`"))?;
    if !status.success() {
        bail!("`{tool} manifest push` failed with exit code {}", status.code().unwrap_or(-1));
    }

    println!("\nManifest pushed successfully.");
    println!("  {tag}  →  aarch64 + x86_64");
    Ok(())
}

/// Print the next step after a native-arch build: run `cargo build-image-manifest`.
fn print_manifest_next_steps(tag: &str, current_arch: &str) {
    let other_arch = if current_arch == "x86_64" { "aarch64" } else { "x86_64" };
    println!("\nArch-specific image pushed: {tag}-{current_arch}");
    println!("\nNext steps:");
    println!("  1. Run on an {other_arch} machine:");
    println!("       cargo build-image --tag {tag}");
    println!("  2. Then assemble the manifest list (on either machine):");
    println!("       cargo build-image-manifest --tag {tag}");
}

// ---------------------------------------------------------------------------
// OpenShift helpers
// ---------------------------------------------------------------------------

/// Resolve OpenShift login info: (oc_user, registry_host, namespace, token).
#[expect(clippy::too_many_lines, reason = "sequential oc CLI calls are inherently linear")]
fn oc_login_info() -> anyhow::Result<(String, String, String, String)> {
    // Verify login.
    let whoami = Command::new("oc")
        .arg("whoami")
        .output()
        .context("failed to run `oc whoami`")?;
    if !whoami.status.success() {
        bail!("Not logged in to OpenShift. Run: oc login <cluster-url>");
    }
    let oc_user = String::from_utf8_lossy(&whoami.stdout).trim().to_owned();

    // Resolve the internal registry hostname via the default-route.
    let reg_out = Command::new("oc")
        .args(["get", "route/default-route", "-n", "openshift-image-registry", "-o=jsonpath={.spec.host}"])
        .output()
        .context("failed to run `oc get route/default-route -n openshift-image-registry`")?;
    if !reg_out.status.success() {
        bail!(
            "`oc get route/default-route -n openshift-image-registry` failed: {}\n\
             Ensure the OpenShift internal image registry is exposed:\n\
             oc patch configs.imageregistry.operator.openshift.io/cluster \
             --patch '{{\"spec\":{{\"defaultRoute\":true}}}}' --type=merge",
            String::from_utf8_lossy(&reg_out.stderr).trim()
        );
    }
    let registry_host = String::from_utf8_lossy(&reg_out.stdout).trim().to_owned();

    // Resolve the current namespace.
    let ns_out = Command::new("oc")
        .args(["project", "-q"])
        .output()
        .context("failed to run `oc project -q`")?;
    if !ns_out.status.success() {
        bail!(
            "`oc project -q` failed: {}",
            String::from_utf8_lossy(&ns_out.stderr).trim()
        );
    }
    let namespace = String::from_utf8_lossy(&ns_out.stdout).trim().to_owned();

    // Obtain the oc auth token for registry login.
    let token_out = Command::new("oc")
        .args(["whoami", "-t"])
        .output()
        .context("failed to run `oc whoami -t`")?;
    if !token_out.status.success() {
        bail!(
            "`oc whoami -t` failed: {}",
            String::from_utf8_lossy(&token_out.stderr).trim()
        );
    }
    let token = String::from_utf8_lossy(&token_out.stdout).trim().to_owned();

    Ok((oc_user, registry_host, namespace, token))
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Return the name of `tool` if it is on `PATH`, otherwise `None`.
fn which(tool: &str) -> Option<String> {
    // `command -v` is POSIX. On Windows use `where`.
    #[cfg(windows)]
    let (program, arg) = ("where", tool);
    #[cfg(not(windows))]
    let (program, arg) = ("sh", "-c");
    #[cfg(not(windows))]
    let cmd_str = format!("command -v {tool}");
    #[cfg(windows)]
    let cmd_str = tool.to_owned();

    Command::new(program)
        .args([arg, &cmd_str])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .ok()
        .filter(std::process::ExitStatus::success)
        .map(|_| tool.to_owned())
}

/// Resolve the container tool for docker/minikube targets.
/// Tries `docker` first, then falls back to `podman`.
fn resolve_docker_tool() -> anyhow::Result<String> {
    for tool in ["docker", "podman"] {
        if which(tool).is_some() {
            return Ok(tool.to_owned());
        }
    }
    bail!("Neither docker nor podman found on PATH. Install one to build container images.")
}

/// Require `podman` for the OpenShift target and manifest assembly.
///
/// Podman is required (not optional) for the OpenShift push path because it
/// supports `--tls-verify=false` consistently on both `login` and `push`,
/// which is necessary when the internal registry uses a self-signed certificate.
fn require_podman() -> anyhow::Result<String> {
    if which("podman").is_some() {
        return Ok("podman".to_owned());
    }
    bail!(
        "`podman` not found on PATH. The openshift target and manifest assembly require podman.\n\
         Install it from: https://podman.io/getting-started/installation"
    )
}

/// Assert that `tool` is on `PATH`, returning a helpful error if not.
fn require_tool(tool: &str, install_url: &str) -> anyhow::Result<()> {
    if which(tool).is_none() {
        bail!("`{tool}` not found on PATH. Install it from: {install_url}");
    }
    Ok(())
}
