---
title: Run Wanaku in a container
description: Start the Wanaku container image with Podman or Docker, mount wanaku.yaml and a data volume, update the image, and fix common container problems.
---

# Run Wanaku in a container

The container image runs the same server as the binary. Use it for evaluation and small installations. For production on Kubernetes or OpenShift, read [Deploy Wanaku on Kubernetes and OpenShift](kubernetes.md).

## Image paths and ports

The image uses these paths and ports:

| Item | Value | Notes |
|---|---|---|
| Configuration file | `/etc/wanaku/wanaku.yaml` | The working directory of the image is `/etc/wanaku`. The server ignores a file that you mount at a different path, for example `/etc/wanaku.yaml`. |
| Persistence directory | `/data/registry` | The image sets `WANAKU_PERSIST_PATH=/data/registry`. Mount a volume on this directory, not on `/data`. |
| Management API and admin UI | `8080` | |
| MCP endpoint | `8081` | |
| Inference proxy | `8083` | |
| A2A listener | `8084` | |
| User | UID `1001`, GID `0` | Mounted files must be readable by this user. |

The server can start without a `wanaku.yaml` file, but it then uses the fail-safe governance defaults. Discovery works, but the server denies every tool call, resource read, and prompt request. Without an action policy and an evaluator, nothing allows these requests. This is sufficient for a first try. For real use, write your own configuration. Start from the [minimal wanaku.yaml](../get-started/quickstart.md#7-allow-your-first-tool-call) and read [Governance Posture](../govern/governance-posture.md).

## Start the container

Podman and Docker do not create a file for a bind mount, so create the file before you start the container:

```bash
mkdir -p ./wanaku
touch ./wanaku/wanaku.yaml
```

Start the container:

```bash
podman run -d --name wanaku \
  -p 8080:8080 -p 8081:8081 -p 8083:8083 -p 8084:8084 \
  -v wanaku-data:/data/registry \
  -v ./wanaku/wanaku.yaml:/etc/wanaku/wanaku.yaml:ro,Z \
  quay.io/wanaku/wanaku-server:early-access
```

Make sure that the server is healthy:

```bash
podman logs wanaku
curl -s http://localhost:8080/healthz
```

## Update the image

The `early-access` tag changes when the project publishes a new build. To update the server, pull the new image, then remove the old container and start a new one with the same `podman run` command. The `wanaku-data` volume keeps the registry data:

```bash
podman pull quay.io/wanaku/wanaku-server:early-access
podman stop wanaku
podman rm wanaku
```

## Troubleshooting

- **Bind mount sources:** Start a host path with `./` or `/`. Podman reads a path such as `etc/wanaku.yaml` as a named volume and fails with `names must match [a-zA-Z0-9][a-zA-Z0-9_.-]*`.
- **SELinux:** On hosts with SELinux (Fedora, RHEL), add `:Z` to each bind mount. Without it, the container cannot read the file.
- **Secrets:** The server reads `env:` references from the container environment. Pass each variable into the container. Read [Pass secrets to the Wanaku server](secrets.md).
- **Services on the host:** In the container, `localhost` is the container itself. Use `host.containers.internal` (Podman) or `host.docker.internal` (Docker) in forward addresses.
- **Connection refused from the host:** A host service that listens only on `127.0.0.1` refuses connections from the container. Configure the service to listen on `0.0.0.0`. Alternatively, start Wanaku with `--network host`. With host networking, remove the `-p` options and use `localhost` in forward addresses.
- **HTTP 403 from an upstream MCP server:** Some MCP servers accept only known `Host` header values, for DNS rebinding protection. Add the host name that Wanaku uses (for example `host.containers.internal:3000`) to the allowed hosts of the upstream server, or use `--network host`.

## Next steps

- [Pass secrets to the Wanaku server](secrets.md): give the container the tokens that `wanaku.yaml` refers to.
- [Persist registry and revision state](persistence.md): learn what the data volume keeps.
- [Secure a Wanaku deployment](secure-a-deployment.md): protect the ports before other users connect.
