---
title: Install the Wanaku server and CLI
description: Install the full or headless Wanaku server with the installer script, an archive, or a container image, and install the Wanaku CLI from Barn.
---

# Install the Wanaku server and CLI

Wanaku ships as a single `wanaku-server` binary and as container images. The Wanaku CLI is a separate download. This page lists every install method. The [quickstart](../get-started/quickstart.md) uses the installer script.

## Choose a variant

Wanaku has two variants:

| Variant | Archive name | Description |
|---|---|---|
| **Full** | `wanaku-server-{version}-{platform}` | Includes the embedded admin UI at `http://localhost:8080/admin` |
| **Headless** | `wanaku-server-headless-{version}-{platform}` | Does not include the admin UI. Use the management API or CLI to manage Wanaku. You can also include Wanaku in other tools. |

Both variants provide the same MCP endpoint, management API, and filter pipeline. Only the full variant includes the admin UI.

> [!IMPORTANT]
> Wanaku 0.3.0 is the first stable release of this server. Earlier releases contain the Java-based Wanaku, which is deprecated. The `early-access` builds are development builds of the next release. They change often.

## Install the server

Select a variant for your use case. You can install it in one of three ways.

### Use the server installer (Linux and macOS)

The installer downloads the archive of the latest stable release for your platform, verifies the checksum, and installs `wanaku-server` in `$HOME/bin`:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | bash
```

Set `WANAKU_INSTALL_DIR` to install in a different directory. To run Wanaku as a systemd service, install in `/usr/local/bin`, because the unit template uses that path:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | sudo WANAKU_INSTALL_DIR=/usr/local/bin bash
```

To install a different release, set `WANAKU_RELEASE` to its tag. For example, set `WANAKU_RELEASE=early-access` to install the latest development build:

```bash
curl -fsSL https://raw.githubusercontent.com/wanaku-ai/wanaku/main/get-wanaku.sh | WANAKU_RELEASE=early-access bash
```

> [!NOTE]
> The wanaku-barn repository has a different `get-wanaku.sh` script. That script installs the Wanaku CLI, not the server.

### Download the archive manually

Download it from the [latest release page](https://github.com/wanaku-ai/wanaku/releases/latest). The archive contains a top-level directory with the same name as the archive. Extract the archive and move the binary to a directory in your `PATH`. Replace `<archive>` with the name of the archive that you downloaded, without the `.tar.gz` extension:

```bash
tar -xzf <archive>.tar.gz
sudo install -m 0755 <archive>/wanaku-server /usr/local/bin/wanaku-server
```

### Use a container image

Pull the image for the selected variant:

```bash
# Full (with admin UI)
podman pull quay.io/wanaku/wanaku-server:0.3.0

# Headless (no admin UI)
podman pull quay.io/wanaku/wanaku-server-headless:0.3.0
```

You can use `docker` instead of `podman` in all commands. Read [Run Wanaku in a container](container.md) to start the image.

## Install the CLI

The Wanaku CLI is a separate package. Download the latest `wanaku` binary from the [wanaku-barn release page](https://github.com/wanaku-ai/wanaku-barn/releases/).

The full variant of the server also has a **CLI Downloads** page in the admin UI. After you start the server, go to `http://localhost:8080` and select **CLI Downloads** in the side navigation. The headless variant does not include the admin UI.

```bash
chmod +x wanaku
sudo mv wanaku /usr/local/bin/
```

Verify the CLI installation:

```bash
wanaku --version
```

> [!NOTE]
> The Wanaku CLI supports the Java-based Wanaku router and the Rust-based engine. Add the `--no-auth` flag when the router does not require authentication.

## Next steps

- [Quickstart](../get-started/quickstart.md): start the server and proxy your first MCP tool call.
- [Run Wanaku in a container](container.md): start the container image with a configuration file and a volume.
- [Run Wanaku as a systemd service](systemd.md): run the binary as a service on Linux.
