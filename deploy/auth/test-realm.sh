#!/usr/bin/env bash

set -euo pipefail

realm_file="$(dirname "$0")/wanaku-realm.json"

jq -e '
  .clients[]
  | select(.clientId == "wanaku-mcp-router")
  | .directAccessGrantsEnabled == true
    and .secret == "${WANAKU_MCPROUTER_SECRET}"
    and any(
      .protocolMappers[]?;
      .protocolMapper == "oidc-audience-mapper"
        and .config["included.custom.audience"] == "wanaku-mcp-router"
        and .config["access.token.claim"] == "true"
    )
' "$realm_file" >/dev/null

# The CLI uses the public client, without a client secret or an explicit scope.
jq -e '
  . as $realm
  | (.clients[] | select(.clientId == "mcp-client")) as $client
  | $client.publicClient == true
    and $client.directAccessGrantsEnabled == true
    and ($client.defaultClientScopes | index("wanaku-mcp-client") != null)
    and any($realm.clientScopes[];
      .name == "wanaku-mcp-client" and any(.protocolMappers[]?;
        .protocolMapper == "oidc-audience-mapper"
        and .config["included.custom.audience"] == "wanaku-mcp-client"
        and .config["access.token.claim"] == "true"))
' "$realm_file" >/dev/null

# Keycloak administration must not grant access to Wanaku by itself.
jq -e '
  .clients[] | select(.clientId == "admin-cli")
  | (.defaultClientScopes | index("wanaku-mcp-client") == null)
    and all(.protocolMappers[]?;
      .config["included.custom.audience"] != "wanaku-mcp-client")
' "$realm_file" >/dev/null
