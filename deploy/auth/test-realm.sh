#!/usr/bin/env bash

set -euo pipefail

realm_file="$(dirname "$0")/wanaku-realm.json"

jq -e '
  .clients[]
  | select(.clientId == "wanaku-mcp-router")
  | .directAccessGrantsEnabled == true
    and any(
      .protocolMappers[]?;
      .protocolMapper == "oidc-audience-mapper"
        and .config["included.custom.audience"] == "wanaku-mcp-router"
        and .config["access.token.claim"] == "true"
    )
' "$realm_file" >/dev/null
