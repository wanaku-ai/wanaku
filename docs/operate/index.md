---
title: Deploy and operate Wanaku
description: Set up authentication with oauth2-proxy and Keycloak for a Wanaku deployment, and find answers to common operation and troubleshooting problems.
---

# Deploy and operate Wanaku

These pages are for operators who deploy and run Wanaku. The install, container, and systemd procedures are in the [quickstart](../get-started/quickstart.md) for now.

## Pages in this section

- [Deploy Wanaku on Kubernetes and OpenShift](kubernetes.md): run Wanaku with plain manifests, or use the Wanaku Operator.
- [Persist registry and revision state](persistence.md): keep the registry and the revision histories across restarts.
- [Authentication](authentication.md): put oauth2-proxy and Keycloak in front of Wanaku.
- [FAQ and Troubleshooting](troubleshooting.md): find the cause of common problems and how to fix them.

## Next steps

- [Configuration](../reference/configuration.md): find the environment variables and the YAML options.
- [Govern agent actions](../govern/index.md): define what agents can do through the proxy.
