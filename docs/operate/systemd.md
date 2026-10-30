---
title: Run Wanaku as a systemd service
description: Install the Wanaku server as a systemd service on Linux with the unit template, a system user, an optional wanaku.yaml, and an environment file.
---

# Run Wanaku as a systemd service

The [`deploy/systemd/wanaku.service`](https://github.com/wanaku-ai/wanaku/blob/main/deploy/systemd/wanaku.service) file is a template for convenience. Review it and adapt it to your system before you use it.

## Paths

The template uses these paths:

| Item | Path |
|---|---|
| Binary | `/usr/local/bin/wanaku-server` |
| Configuration file | `/etc/wanaku/wanaku.yaml` (optional) |
| Environment file | `/etc/wanaku/wanaku.env` (optional) |
| Persistence directory | `/var/lib/wanaku` |

## Install the service

1. Install the binary in `/usr/local/bin`. Read [Install the Wanaku server and CLI](install.md#use-the-server-installer-linux-and-macos).
2. Create the `wanaku` system user:

    ```bash
    sudo useradd --system --home-dir /var/lib/wanaku --shell /usr/sbin/nologin wanaku
    ```

3. Install the unit file:

    ```bash
    sudo install -m 0644 deploy/systemd/wanaku.service /etc/systemd/system/wanaku.service
    ```

4. Optional: create `/etc/wanaku/wanaku.yaml` and `/etc/wanaku/wanaku.env`. Put secrets in `wanaku.env` and set its mode to `0600`.
5. Start the service:

    ```bash
    sudo systemctl daemon-reload
    sudo systemctl enable --now wanaku
    ```

6. Examine the logs:

    ```bash
    journalctl -u wanaku -f
    ```

## Next steps

- [Pass secrets to the Wanaku server](secrets.md): put the secrets for `wanaku.yaml` in the environment file.
- [Persist registry and revision state](persistence.md): learn what the persistence directory keeps.
- [Secure a Wanaku deployment](secure-a-deployment.md): protect the ports before other users connect.
