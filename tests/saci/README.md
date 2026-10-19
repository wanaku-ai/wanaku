# Saci test image

[Midnight Saci](https://github.com/orpiske/midnight-saci) runs Markdown test
plans in a worker container. The [`Containerfile`](Containerfile) in this
directory extends the Saci worker image with the Wanaku binaries, so a plan
can start a Wanaku server and test it with the CLI.

## Contents

The image is `quay.io/orpiske/saci-worker` with these additions:

| Program | Path | Source |
| --- | --- | --- |
| `wanaku-server` | `/usr/local/bin/wanaku-server` | `/usr/local/bin/wanaku-server` of `WANAKU_IMAGE` |
| `wanaku` | `/usr/local/bin/wanaku` | The `wanaku-cli` archive of a [wanaku-barn release](https://github.com/wanaku-ai/wanaku-barn/releases/tag/early-access) |

On Linux x86_64, the image contains the native CLI. On other architectures,
the image contains the JVM CLI and a Java runtime.

The base image also supplies `bash`, `curl`, `jq`, `git` and the other
[worker utilities](https://github.com/orpiske/midnight-saci/blob/main/docs/worker.md#the-image).
The image runs as UID 10001 in group 0, and `HOME` is `/workspace`.

## Build the image

Run this command from the repository root:

```sh
make saci-image
```

The target builds `localhost/wanaku-server:saci` from this checkout. Then it
builds `localhost/wanaku-saci-worker:latest` from that image. The target uses
`podman`. If `podman` is not installed, it uses `docker`.

| Variable | Default | Description |
| --- | --- | --- |
| `RUNTIME` | `podman`, else `docker` | The container tool. |
| `SACI_BASE` | `quay.io/orpiske/saci-worker:latest` | The Saci worker image to extend. Set it to `localhost/saci-worker:latest` to use a local `make image` build of Midnight Saci. |
| `SACI_TAG` | `localhost/wanaku-saci-worker:latest` | The tag of the result. |

To use a published server image and not build the server, run the build
directly:

```sh
docker build -f tests/saci/Containerfile \
  --build-arg WANAKU_IMAGE=quay.io/wanaku/wanaku-server:early-access \
  -t localhost/wanaku-saci-worker:latest tests/saci
```

The Containerfile also accepts `WANAKU_CLI_RELEASE` (default `early-access`)
and `WANAKU_CLI_VERSION` (default `0.3.0-SNAPSHOT`). The build verifies the
SHA-256 checksum of the CLI archive.

## Use the image

Set `worker.image` in the Saci configuration:

```yaml
worker:
  image: localhost/wanaku-saci-worker:latest
```

The shell session of a run keeps background processes between steps. A
setup step can start the server, and later steps can use it:

````markdown
## Phase 1: Setup

### Step 1.1: Start the Wanaku server

Start the server in the background and wait for the management API:

```bash
wanaku-server >wanaku-server.log 2>&1 &
for _ in $(seq 1 50); do
  curl -fsS -o /dev/null http://127.0.0.1:8080/healthz && break
  sleep 0.2
done
curl -sS -w '\nstatus %{http_code}\n' http://127.0.0.1:8080/healthz
```

**Expected result:**

- The output ends with `status 200`.

## Phase 2: CLI

### Test 2.1: List the tools

```bash
wanaku tools list --no-auth
```

**Expected result:**

- The command exits with status 0.
````

The server stores its registry in `$HOME/.wanaku/server`, in the run workspace.
Each run starts with an empty registry.
