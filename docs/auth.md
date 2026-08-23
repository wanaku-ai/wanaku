# Authentication

Wanaku uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. Two oauth2-proxy instances run as reverse proxies in front of the MCP and management API ports, providing shared SSO via [Keycloak](https://keycloak.org).

## Architecture

```
Browser/CLI ----> oauth2-proxy-mcp (:4180) ----> Wanaku MCP (:8081)
            \---> oauth2-proxy-mgmt (:4181) ---> Wanaku Mgmt (:8080)
```

Both instances share the same cookie secret, so logging in on one port authenticates you on the other (SSO).

The MCP proxy accepts bearer tokens from multiple Keycloak clients (`mcp-client`, `wanaku-mcp-client`) via `--oidc-extra-audience`, so MCP Inspector and other MCP clients can authenticate with their own Keycloak client credentials.

## Prerequisites

### Install oauth2-proxy

**macOS:**

```bash
brew install oauth2-proxy
```

**Linux:** Download from the [oauth2-proxy releases page](https://github.com/oauth2-proxy/oauth2-proxy/releases).

### Keycloak Client Setup

The `wanaku-mcp-router` client in Keycloak must be **confidential** (not public):

1. Open Keycloak Admin.
2. Select **Clients**.
3. Select `wanaku-mcp-router`.
4. Select **Settings**.
5. Set **Client authentication** to **ON**.
6. Save the client.
7. Open the **Credentials** tab.
8. Copy the client secret.
9. Add `http://localhost:4180/*` and `http://localhost:4181/*` under **Valid redirect URIs**.
10. Add `*` under **Web origins**.

## Setup

### 1. Generate a Shared Cookie Secret

The cookie secret must be exactly 16, 24, or 32 bytes. Both oauth2-proxy instances must use the same secret for SSO to work.

```bash
export COOKIE_SECRET=$(openssl rand -hex 16)
```

### 2. Start Wanaku with Auth Issuer

```bash
WANAKU_AUTH_ISSUER=http://localhost:8543/realms/wanaku wanaku-server
```

When `WANAKU_AUTH_ISSUER` is set, the endpoint `/.well-known/oauth-protected-resource/{namespace}/mcp` returns OAuth server metadata. When unset, the endpoint returns 404.

### 3. Start the MCP Proxy

```bash
oauth2-proxy \
  --http-address=127.0.0.1:4180 \
  --upstream=http://127.0.0.1:8081 \
  --provider=keycloak-oidc \
  --oidc-issuer-url=http://localhost:8543/realms/wanaku \
  --client-id=wanaku-mcp-router \
  --client-secret=<your-secret> \
  --cookie-secret=$COOKIE_SECRET \
  --cookie-secure=false \
  --redirect-url=http://localhost:4180/oauth2/callback \
  --email-domain="*" \
  --code-challenge-method=S256 \
  --skip-jwt-bearer-tokens \
  --pass-authorization-header \
  --oidc-extra-audience=mcp-client \
  --oidc-extra-audience=wanaku-mcp-client \
  --insecure-oidc-allow-unverified-email \
  --skip-auth-route="^/.well-known/.*" \
  --skip-auth-route="^/public/.*" \
  --skip-auth-route="^/authorize$" \
  --skip-auth-route="^/token$" \
  --skip-auth-route="^/register$" \
  --skip-auth-route="OPTIONS=^/.*" \
  --api-route="^/[^/]+/mcp/?$" \
  --upstream-timeout=3600s
```

### 4. Start the Management Proxy

In another terminal, using the same `$COOKIE_SECRET` for SSO:

```bash
oauth2-proxy \
  --http-address=127.0.0.1:4181 \
  --upstream=http://127.0.0.1:8080 \
  --provider=keycloak-oidc \
  --oidc-issuer-url=http://localhost:8543/realms/wanaku \
  --client-id=wanaku-mcp-router \
  --client-secret=<your-secret> \
  --cookie-secret=$COOKIE_SECRET \
  --cookie-secure=false \
  --redirect-url=http://localhost:4181/oauth2/callback \
  --email-domain="*" \
  --code-challenge-method=S256 \
  --skip-jwt-bearer-tokens \
  --pass-authorization-header \
  --oidc-extra-audience=mcp-client \
  --oidc-extra-audience=wanaku-mcp-client \
  --insecure-oidc-allow-unverified-email \
  --skip-auth-route="^/healthz$" \
  --skip-auth-route="^/health$"
```

### 5. Access Wanaku

- **Admin UI:** `http://localhost:4181/admin/`
- **MCP endpoint:** `http://localhost:4180/default/mcp`
- **Public MCP (no auth):** `http://localhost:4180/public/mcp`

## Role-Based Access

To restrict the management UI to administrators:

1. Create an `admin` role in Keycloak
2. Add `--allowed-role=admin` to the management proxy command (step 4)
3. Assign the `admin` role to administrator users

MCP users without the `admin` role can use tools. They cannot access the management UI.

## CLI Usage

```bash
# Get a token from Keycloak (client must have direct access grants enabled)
TOKEN=$(curl -s -X POST http://localhost:8543/realms/wanaku/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=wanaku-mcp-router \
  -d client_secret=<your-secret> \
  -d username=test \
  -d password=test | jq -r .access_token)

# Use with the Wanaku CLI
wanaku tools list --host http://localhost:4181 --token $TOKEN

# Use with MCP endpoint directly
curl -H "Authorization: Bearer $TOKEN" http://localhost:4180/default/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

You can also use the CLI's built-in authentication:

```bash
wanaku auth login --api-token $TOKEN
wanaku tools list --host http://localhost:4181
```

## Running Without Authentication

For local development or testing, run Wanaku without oauth2-proxy:

```bash
wanaku-server
```

Use the `--no-auth` flag with CLI commands:

```bash
wanaku tools list --no-auth
```

## MCP Inspector

Set the MCP Inspector endpoint to `http://localhost:4180/default/mcp`. The Inspector's OAuth flow uses the `mcp-client` Keycloak client. The `--oidc-extra-audience` flag permits tokens for this client.

## Related Docs

- [Getting Started](./getting-started.md) — initial setup
- [Configuration](./configuration.md) — `WANAKU_AUTH_ISSUER` and other env vars
- [FAQ](./faq.md) — troubleshooting authentication issues
