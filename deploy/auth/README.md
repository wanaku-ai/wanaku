# Authenticated local deployment

This guide starts Keycloak, two oauth2-proxy instances, and Wanaku with Docker Compose. It uses the `wanaku` and `wanaku-keycloak-admin` CLIs from [wanaku-barn](https://github.com/wanaku-ai/wanaku-barn). The same access token works with the MCP and management APIs.

This configuration is for local development. It uses HTTP, development administrator credentials, and a password grant. Do not use it in production.

## Prerequisites

- Docker with Compose v2, or Podman with a Compose v2 provider.
- OpenSSL.
- Available ports: `8543`, `8943`, `4180`, and `4181`.
- The `wanaku` and `wanaku-keycloak-admin` CLIs on your `PATH`.

The wanaku-barn [releases](https://github.com/wanaku-ai/wanaku-barn/releases) page publishes both CLIs. Download these archives:

- `wanaku-cli-<version>-linux-x86_64.zip` or `wanaku-cli-<version>-osx-aarch_64.zip` (native).
- `wanaku-keycloak-admin-<version>-linux-x86_64.zip` or `wanaku-keycloak-admin-<version>-osx-aarch_64.zip` (native).

Extract each archive and add its `bin` directory to `PATH`. On other platforms, use the JVM archives (`wanaku-cli-<version>.zip` and `wanaku-keycloak-admin-<version>.zip`). They need Java 21 or later. Make sure that `wanaku --version` and `wanaku-keycloak-admin --help` run before you continue.

Run the commands from the Wanaku repository root. Use the same shell for all steps. For Podman, replace `docker compose` with `podman compose`.

## Client roles

| Client | Purpose | Credentials |
|---|---|---|
| `admin-cli` | Keycloak administration through `wanaku-keycloak-admin` | Administrator in the `master` realm |
| `mcp-client` | User login through `wanaku`; access to both Wanaku APIs | User in the `wanaku` realm; no client secret |
| `wanaku-mcp-router` | Browser login through oauth2-proxy | Confidential client secret |

The bundled realm enables direct access grants for `mcp-client`. Its default `wanaku-mcp-client` scope adds the audience that both proxies accept. `admin-cli` does not grant access to Wanaku. Always specify `--client-id mcp-client` when you log in for this guide.

`mcp-client` is a public client, so its login does not need `--client-secret`. The confidential `wanaku-mcp-router` client secret is for the oauth2-proxy instances only.

## 1. Set the browser client secret and start Keycloak

Set a secret for the confidential `wanaku-mcp-router` client before Keycloak imports the realm:

```bash
WANAKU_MCPROUTER_SECRET=$(openssl rand -hex 32)
export WANAKU_MCPROUTER_SECRET
```

Compose passes this value to Keycloak, and the realm import assigns it to `wanaku-mcp-router`. If you omit it, Compose uses `changeme`. That fallback is for local development only. Set a private value for each environment.

```bash
docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml up -d --wait keycloak
```

Compose waits for Keycloak to become healthy. Keycloak imports the bundled `wanaku-realm.json` on the first start. You do not need to supply another realm export.

The realm file must be readable by the Keycloak container user. The bind mount uses `ro,z`. `ro` prevents changes from the container. On SELinux hosts, `z` applies a shared container label to permit access from multiple Compose projects. The `keycloak-data` volume stores the Keycloak database across container restarts and replacements.

## 2. Capture the proxy secrets

Read the secret that the realm import assigned to `wanaku-mcp-router`:

```bash
OAUTH2_PROXY_CLIENT_SECRET=$(wanaku-keycloak-admin credentials show \
  --keycloak-url http://localhost:8543 --realm wanaku \
  --admin-username admin --admin-password admin \
  --client-id wanaku-mcp-router --show-secret --plain)
export OAUTH2_PROXY_CLIENT_SECRET
```

The `--plain --show-secret` options write only the secret to standard output. Errors use standard error. If the command cannot return a secret, it fails and standard output stays empty.

Do not regenerate this client secret after the realm import. Regeneration changes the Keycloak value and no longer matches `WANAKU_MCPROUTER_SECRET`.

Generate the shared cookie secret:

```bash
OAUTH2_PROXY_COOKIE_SECRET=$(openssl rand -hex 16)
export OAUTH2_PROXY_COOKIE_SECRET
```

Compose passes these shell variables to both proxies. Do not put their values in tracked files. Empty secrets cause the proxies to reject their configuration.

## 3. Create a user

```bash
wanaku-keycloak-admin users add \
  --keycloak-url http://localhost:8543 --realm wanaku \
  --admin-username admin --admin-password admin \
  --username alice --password \
  --email alice@example.com --first-name Alice --last-name Smith
```

Enter a local test password at the prompt. The command creates a user with a verified email address. On subsequent runs, use the existing user. To list users, run:

```bash
wanaku-keycloak-admin users list \
  --keycloak-url http://localhost:8543 --realm wanaku \
  --admin-username admin --admin-password admin
```

## 4. Start Wanaku and the proxies

```bash
docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml up -d --build --wait
docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml ps
```

`--build` builds the Wanaku server image from this checkout, so it includes the local server changes. Without `--build`, Compose uses `quay.io/wanaku/wanaku-server:latest` instead. Wait for Wanaku to start before the next step. To inspect startup errors, run:

```bash
docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml logs --tail=50 wanaku-server oauth2-proxy-mcp oauth2-proxy-mgmt
```

Only the proxies expose Wanaku to the host. The management API and admin UI use port `4180`. MCP uses port `4181`.

## 5. Log in and capture a token

```bash
wanaku auth login \
  --auth-server http://localhost:8543 --realm wanaku \
  --client-id mcp-client --username alice --password
```

Enter the password from step 3. Capture the access token:

```bash
TOKEN=$(wanaku auth token --get --plain --unmask)
```

The token command writes only the token to standard output. Diagnostics use standard error. If no usable token is available, the command fails and standard output is empty. Do not continue after a failed token command.

## 6. Call both APIs

```bash
wanaku mcp tool list --verbose --uri http://localhost:4181/default/mcp --token "$TOKEN"
wanaku tools list --verbose --host http://localhost:4180 --token "$TOKEN"
```

Both commands must succeed. The MCP command names the `default` namespace explicitly in its endpoint. Replace `default` with the namespace you want to access. A new registry has no tools, so an empty list is expected. The MCP command also shows `No tools found` when the tools are in a different namespace. `wanaku tools list` shows the tools of all namespaces with their namespace names.

The shell variable does not update when a token expires. Run the token command again to refresh an expired stored token. Run `wanaku auth login` again if the refresh token has expired.

## Browser access and logout

Open `http://localhost:4180/admin/` and log in as `alice`. Both proxies share a session cookie. Every MCP endpoint, such as `http://localhost:4181/default/mcp`, requires a bearer token.

Select **Logout** in the admin UI header to end the browser session. The management proxy deletes its session cookie and ends the Keycloak session. Then the browser goes to the login page. `wanaku auth logout` clears local CLI credentials. It does not end a browser session or revoke an access token. Use `unset TOKEN` to clear the shell variable.

## Stop and restart

```bash
docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml down
```

This command preserves the Keycloak and Wanaku data volumes. Keycloak retains users and client credentials.

To start the deployment again, start Keycloak first. The secret capture needs a running Keycloak.

1. Start Keycloak:

   ```bash
   docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml up -d --wait keycloak
   ```

2. In a new shell, repeat the secret capture and cookie generation commands from [step 2](#2-capture-the-proxy-secrets). A new cookie secret invalidates existing browser cookies.

3. Start Wanaku and the proxies:

   ```bash
   docker compose -p wanaku-auth -f deploy/auth/docker-compose-auth.yml up -d --wait
   ```

Keycloak does not update an existing realm from the import file. To test a changed realm from a clean state, use a new Compose project name and stop the previous project first. The new project creates separate data volumes.

## Troubleshooting

| Symptom | Check |
|---|---|
| `Permission denied` when Keycloak imports `wanaku-realm.json` | Check that the Keycloak container user can read the host file. On SELinux hosts, use the `ro,z` bind mount from this Compose file. |
| `unauthorized_client` during login | Use `--client-id mcp-client`. An older imported realm can have direct access grants disabled. |
| HTTP 401 from either API | Capture a fresh token. Use a `wanaku` realm user and `mcp-client`. Do not use an `admin-cli` token. |
| Logs appear in `TOKEN` | Upgrade the wanaku-barn CLI. Do not remove log lines with a shell filter. |
| `No tools found` from `wanaku mcp tool list` | Make sure that the endpoint uses the namespace that contains the tools, for example `http://localhost:4181/employee/mcp`. |
| `did not answer the 2026-07-28 protocol detection request` warning | The CLI waits 30 seconds, then uses an older MCP protocol version. The command still completes. |
| Invalid MCP endpoint path | Use the exact MCP endpoint for the namespace, such as `http://localhost:4181/default/mcp`. |
| Proxy exits at startup | Export both secrets in the shell that runs Compose. Inspect the proxy logs. |
| Browser redirects to `keycloak:8080` | Rebuild the server. The public issuer must be `http://localhost:8543/realms/wanaku`; the upstream issuer uses the container address. |

For architecture, standalone proxy setup, and optional role restrictions, see [Authentication](../../docs/operate/authentication.md).
