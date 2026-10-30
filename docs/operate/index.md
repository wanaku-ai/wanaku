---
title: Deploy and operate Wanaku
description: Deploy, secure, and run Wanaku, with Kubernetes manifests, persistence, authentication, a security checklist, the Admin UI, and troubleshooting.
---

# Deploy and operate Wanaku

These pages are for operators who deploy and run Wanaku.

## Pages in this section

- [Install the Wanaku server and CLI](install.md): choose a variant and install it with the installer, an archive, or a container image.
- [Run Wanaku in a container](container.md): start the image with Podman or Docker, and fix common container problems.
- [Run Wanaku as a systemd service](systemd.md): run the binary as a service on Linux.
- [Pass secrets to the Wanaku server](secrets.md): give the server the variables that `wanaku.yaml` refers to.
- [Deploy Wanaku on Kubernetes and OpenShift](kubernetes.md): run Wanaku with plain manifests, or use the Wanaku Operator.
- [Persist registry and revision state](persistence.md): keep the registry and the revision histories across restarts.
- [Authentication](authentication.md): put oauth2-proxy and Keycloak in front of Wanaku.
- [Secure a Wanaku deployment](secure-a-deployment.md): check authentication, network exposure, secrets, and audit before you go live.
- [Use the Wanaku Admin UI](admin-ui.md): find each page of the web console and the feature that it manages.
- [FAQ and Troubleshooting](troubleshooting.md): find the cause of common problems and how to fix them.

## Next steps

- [Configuration](../reference/configuration.md): find the environment variables and the YAML options.
- [Govern agent actions](../govern/index.md): define what agents can do through the proxy.
