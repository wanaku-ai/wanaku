---
title: Authenticate MCP clients with oauth2-proxy and Keycloak
description: Authenticate MCP clients and Admin UI users for Wanaku with oauth2-proxy and Keycloak, set the issuer addresses, and log out of the CLI.
---

# Authenticate MCP clients with oauth2-proxy and Keycloak

Wanaku uses Keycloak to issue tokens. oauth2-proxy validates browser sessions and bearer tokens before it forwards requests to Wanaku.

```text
Browser/CLI ──► oauth2-proxy-mgmt (:4180) ──► Wanaku Management API (:8080)
            └─► oauth2-proxy-mcp (:4181) ──► Wanaku MCP (:8081)
```

Both proxies share a cookie secret. A browser login through either proxy then works on both ports. CLI requests use a Keycloak access token.

## Local Docker Compose setup

Follow the [authenticated local deployment guide](https://github.com/wanaku-ai/wanaku/blob/main/deploy/auth/README.md). It starts Keycloak, Wanaku, and both proxies. It also creates a test user and demonstrates the `wanaku` and `wanaku-keycloak-admin` commands.

The bundled realm has three separate clients:

- `admin-cli` lets `wanaku-keycloak-admin` manage Keycloak. Its token does not authorize Wanaku API requests.
- `mcp-client` lets `wanaku` log in as a user. Its default scope adds the audience accepted by the proxies.
- `wanaku-mcp-router` lets oauth2-proxy authenticate browser sessions. It is a confidential client with a generated secret.

The CLI logs in with the public `mcp-client`, which does not need a client secret. The `wanaku-mcp-router` secret belongs to the oauth2-proxy instances.

The local realm enables the password grant for `mcp-client`. Use this grant only for local development. Production deployments should use an interactive OAuth flow and production Keycloak clients.

## Log in with the CLI

After setup, log in with the local CLI client and use one token with both APIs:

```bash
wanaku auth login --auth-server http://localhost:8543 --realm wanaku \
  --client-id mcp-client --username alice --password
TOKEN=$(wanaku auth token --get --plain --unmask)
wanaku mcp tool list --verbose --uri http://localhost:4181/default/mcp --token "$TOKEN"
wanaku tools list --verbose --host http://localhost:4180 --token "$TOKEN"
```

Set the namespace in the MCP endpoint path. Replace `default` with the namespace that contains the tools you want to access. The login command prompts for the user's password.

## Issuer addresses

Set `WANAKU_AUTH_ISSUER` to the issuer URL that clients can reach. Set `WANAKU_AUTH_UPSTREAM_ISSUER` when the server must use a different URL to reach Keycloak. The upstream variable defaults to `WANAKU_AUTH_ISSUER`.

| Variable | Default | Purpose |
|---|---|---|
| `WANAKU_AUTH_ISSUER` | _(unset = disabled)_ | Public OIDC issuer URL (for example, `http://localhost:8543/realms/wanaku`) |
| `WANAKU_AUTH_UPSTREAM_ISSUER` | `WANAKU_AUTH_ISSUER` | Internal OIDC issuer URL for token requests (for example, `http://keycloak:8080/realms/wanaku`) |

The public issuer URL must be accessible to clients and browsers. Wanaku uses this URL in discovery metadata, authorization redirects, registration redirects, and the JWKS URL. Set `WANAKU_AUTH_UPSTREAM_ISSUER` when Wanaku must use a different address to reach the same issuer. Only the `/token` proxy uses this internal address. An unset or empty upstream issuer uses the public issuer URL.

For the Compose setup, clients use `http://localhost:8543/realms/wanaku`; the server container uses `http://keycloak:8080/realms/wanaku` for token requests. This keeps public discovery metadata and browser redirects usable from the host.

Wanaku always serves OAuth protected-resource metadata for MCP endpoints at `/.well-known/oauth-protected-resource/{namespace}/mcp`. When `WANAKU_AUTH_ISSUER` is set, the `authorization_servers` list contains the Wanaku host. When it is unset, the route returns HTTP 200 with an empty `authorization_servers` list. The OpenID discovery, `/authorize`, `/token`, and `/register` routes are active only when `WANAKU_AUTH_ISSUER` is set.

## Proxy behavior

The MCP proxy accepts bearer tokens with the configured Wanaku MCP audience. MCP endpoints use `/{namespace}/mcp`, such as `/default/mcp`. Pass the full endpoint to the CLI. The CLI does not infer or select a namespace from a bare server origin.

The management proxy accepts the same bearer tokens for `/api/` requests. It protects the admin UI with a browser session. Set the proxy's allowed roles only when the realm maps the selected administrator role into access tokens.

Public MCP requests use `/public/mcp`. The Compose setup allows this endpoint without a token. Keep public tools safe for anonymous use.

## Logging out

`wanaku auth logout` clears the CLI's saved credentials. It does not end a browser session or revoke a token that was already issued. Browser logout behavior is tracked in [issue #2026](https://github.com/wanaku-ai/wanaku/issues/2026).
