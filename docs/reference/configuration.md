---
title: "Configure the proxy: files, variables and precedence"
description: How Wanaku reads its configuration from environment variables, pipeline.yaml, and wanaku.yaml, which source wins, and which file to change.
---

# Configure the proxy: files, variables and precedence

Wanaku uses environment variables and two optional YAML files. It does not use a properties file. Set environment variables in the container orchestrator or systemd unit. Specify configuration files only when you need custom settings.

## Configuration Sources (Precedence Order)

1. **Environment variables** — highest priority, always win
2. **Runtime YAML files** — loaded from CLI args (e.g., `wanaku-server --pipeline-config pipeline.yaml --wanaku-config wanaku.yaml`)
3. **Embedded defaults** — compiled into the binary

## Which source to change

- [Wanaku environment variables](environment-variables.md): listen addresses, persistence, the Admin UI override, header forwarding, and feature limits.
- [Pipeline configuration file (pipeline.yaml)](pipeline-yaml.md): listeners, TLS, filter chains, and filter options.
- [Wanaku configuration file (wanaku.yaml)](wanaku-yaml.md): forwards, credential bindings, connections, evaluators, action policies, and governance.

## Development (Local Machine)

```bash
# No persistence, embedded UI, inference backend for LLMs
export WANAKU_PERSIST_BACKEND=none
export WANAKU_INFERENCE_UPSTREAM=http://localhost:11434
wanaku-server
```

## Next steps

- [Ports and endpoints](ports-and-endpoints.md): find the default addresses of each listener.
- [Persist registry and revision state](../operate/persistence.md): keep the registry across restarts.
- [Deploy Wanaku on Kubernetes and OpenShift](../operate/kubernetes.md): use the configuration in a cluster.
