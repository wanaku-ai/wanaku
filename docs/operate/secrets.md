---
title: Pass secrets to the Wanaku server
description: Pass the env references in wanaku.yaml to a Wanaku container or systemd service, and find out what happens when a secret variable is missing.
---

# Pass secrets to the Wanaku server

The `wanaku.yaml` file refers to secrets with `env:NAME` references, for example in `llm_connections` and in credential `bindings`. The server reads each value from its own process environment when it starts.

## Pass the variables

Pass the variables to the server:

- **Container:** Add `-e NAME` to `podman run` to copy the variable from your shell. Export the variable before you run the command. You can also use `--env-file` or `podman secret create` with `--secret NAME,type=env`.
- **systemd:** Put the variables in `/etc/wanaku/wanaku.env`. Set the file mode to `0600`.

## Missing variables

A missing variable has different results:

| Configuration | Result |
|---|---|
| `llm_connections` or `typesafe_system_one_connections` | The server rejects all connections of that type and stops with `evaluator startup failed: connection_configuration_invalid`. |
| Credential `bindings` | The server logs `secret_not_found` and skips discovery for that forward. The server continues to run. |

## Check credential bindings

For credential bindings, also check these items:

- Each binding must have an `origin` with the scheme, host, and port of the forward address.
- The forward field is `credential_bindings`. An unknown key in a forward or binding entry stops startup. The error names the key, for example `forwards.0.credentials_bindings`.

Read [Credential Brokerage](../govern/credential-brokerage.md) for the complete binding reference.

## Next steps

- [Keep API keys away from AI agents with credential brokerage](../govern/credential-brokerage.md): write the bindings that use these secrets.
- [Run Wanaku in a container](container.md): start the container with `-e` options.
- [Run Wanaku as a systemd service](systemd.md): use the environment file of the unit.
