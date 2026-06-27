# Common: OIDC Login Verification

Reusable steps for verifying OIDC authentication via the Wanaku router or directly against Keycloak.

The `wanaku auth login` command authenticates via the router's OIDC proxy endpoint (`/q/oidc/...`) by default.
When the `--realm` option is provided, it uses Keycloak's native discovery path (`/realms/<realm>`) instead,
allowing direct authentication against Keycloak without going through the router proxy.
The default test path below uses the router OIDC proxy; for direct Keycloak authentication, add `--realm <realm>`.

## Prerequisites

- Keycloak deployed and configured (see [keycloak-setup.md](keycloak-setup.md))
- WanakuRouter CR created and Ready
- `WANAKU_ROUTER_URL` environment variable set (e.g. `http://<router-route-host>`)
- `WANAKU_TEST_USER` and `WANAKU_TEST_PASS` environment variables set

## Steps

### 1. Verify the OIDC login works

Log in as the test user via the router's OIDC proxy.

Note: `--password` is a boolean flag that prompts for input. Pipe the password via stdin.

```bash
echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
  --auth-server "${WANAKU_ROUTER_URL}" \
  --username "${WANAKU_TEST_USER}" \
  --password \
  --plain 2>&1

LOGIN_EXIT=$?

if [ "${LOGIN_EXIT}" -ne 0 ]; then
  echo "FAIL: OIDC login failed (exit code ${LOGIN_EXIT})"
  LOGIN_FAILED=true
else
  echo "PASS: OIDC login works for user '${WANAKU_TEST_USER}'"
  LOGIN_FAILED=false
fi
```

### 2. Workaround: regenerate the client secret if login fails

When importing the realm via the CLI (not at Keycloak bootstrap), the variable `${WANAKU_SERVICE_SECRET:mypasswd}` may be stored literally instead of being resolved. If step 1 failed, regenerate the secret:

```bash
if [ "${LOGIN_FAILED}" = "true" ]; then
  echo "WARN: OIDC secret may be stored literally — regenerating via CLI"

  ${WANAKU_CLI:-wanaku} admin credentials regenerate \
    --keycloak-url "${KEYCLOAK_URL}" \
    --admin-username "${KEYCLOAK_ADMIN_USER}" \
    --admin-password "${KEYCLOAK_ADMIN_PASS}" \
    --client-id wanaku-service \
    --show-secret \
    --plain 2>&1

  # Re-retrieve the new secret
  CREDENTIALS_OUTPUT=$(${WANAKU_CLI:-wanaku} admin credentials show \
    --keycloak-url "${KEYCLOAK_URL}" \
    --admin-username "${KEYCLOAK_ADMIN_USER}" \
    --admin-password "${KEYCLOAK_ADMIN_PASS}" \
    --client-id wanaku-service \
    --show-secret \
    --plain 2>&1)

  export WANAKU_OIDC_SECRET=$(echo "${CREDENTIALS_OUTPUT}" | grep "Client Secret:" | sed 's/.*Client Secret: //')

  # Re-verify login via the router
  echo "${WANAKU_TEST_PASS}" | ${WANAKU_CLI:-wanaku} auth login \
    --auth-server "${WANAKU_ROUTER_URL}" \
    --username "${WANAKU_TEST_USER}" \
    --password \
    --plain 2>&1

  if [ $? -ne 0 ]; then
    echo "FAIL: OIDC login still failing after secret regeneration"
    exit 1
  fi
  echo "PASS: OIDC login works after secret regeneration"
fi
```
