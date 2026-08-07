# Authentication with oauth2-proxy

Wanaku Praxis uses [oauth2-proxy](https://github.com/oauth2-proxy/oauth2-proxy) for authentication. Two oauth2-proxy instances protect the MCP and management ports with shared SSO.

## Prerequisites

### Keycloak Client Setup

The `wanaku-mcp-router` client in Keycloak must be **confidential** (not public):

1. Go to Keycloak Admin → Clients → `wanaku-mcp-router` → Settings
2. Set **Client authentication** to **ON**
3. Save, then go to the **Credentials** tab and copy the client secret
4. Under **Valid redirect URIs**, add `http://localhost:4180/*` and `http://localhost:4181/*`
5. Under **Web origins**, add `*`

## Quick Start (Docker Compose)

1. Generate a cookie secret (must be exactly 16, 24, or 32 bytes):
   ```bash
   openssl rand -hex 16
   ```

2. Update `oauth2-proxy-shared.env`:
   - Set `OAUTH2_PROXY_COOKIE_SECRET` to the generated secret
   - Set `OAUTH2_PROXY_CLIENT_SECRET` to the Keycloak client secret from above

3. Place your Keycloak realm export as `wanaku-realm.json` in this directory.

4. Start the stack:
   ```bash
   docker compose -f docker-compose-auth.yml up
   ```

5. Access:
   - Admin UI: http://localhost:4181/admin/
   - MCP endpoint: http://localhost:4180/mcp
   - Public MCP (no auth): http://localhost:4180/public/mcp

## Architecture

```
Browser/CLI ──► oauth2-proxy-mcp (:4180) ──► Praxis MCP (:8081)
            └─► oauth2-proxy-mgmt (:4181) ──► Praxis Mgmt (:9090)
```

Both instances share the same cookie secret, so logging in on one port authenticates you on the other (SSO).

The MCP proxy accepts bearer tokens from multiple Keycloak clients (`mcp-client`, `wanaku-mcp-client`) via `--oidc-extra-audience`, so MCP Inspector and other MCP clients can authenticate with their own Keycloak client credentials.

## Role-Based Access

To restrict the management UI to administrators:

1. Create an `admin` role in Keycloak
2. Uncomment `OAUTH2_PROXY_ALLOWED_ROLES=admin` in `oauth2-proxy-mgmt.env`
3. Assign the `admin` role to administrator users

MCP users who don't have the `admin` role can use tools but cannot access the management UI.

## CLI Usage

```bash
# Get a token from Keycloak (client must have direct access grants enabled)
TOKEN=$(curl -s -X POST http://localhost:8543/realms/wanaku/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=wanaku-mcp-router \
  -d client_secret=<your-secret> \
  -d username=test \
  -d password=test | jq -r .access_token)

# Use with the management proxy
wanaku tools list --host http://localhost:4181 --token $TOKEN

# Use with MCP
curl -H "Authorization: Bearer $TOKEN" http://localhost:4180/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

## Local Development (without Docker)

Install oauth2-proxy (`brew install oauth2-proxy` on macOS) and run directly.

Generate a shared cookie secret (must be exactly 16, 24, or 32 bytes):
```bash
export COOKIE_SECRET=$(openssl rand -hex 16)
```

Start Praxis with the auth issuer configured:
```bash
WANAKU_AUTH_ISSUER=http://localhost:8543/realms/wanaku cargo run
```

Start the MCP proxy:
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
  --api-route="^/mcp.*" \
  --upstream-timeout=3600s
```

Start the management proxy (in another terminal, same `$COOKIE_SECRET` for SSO):
```bash
oauth2-proxy \
  --http-address=127.0.0.1:4181 \
  --upstream=http://127.0.0.1:9090 \
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

## MCP Inspector

Point the MCP Inspector at `http://localhost:4180/mcp`. The Inspector's OAuth flow uses the `mcp-client` Keycloak client, which is accepted via the `--oidc-extra-audience` flag.
