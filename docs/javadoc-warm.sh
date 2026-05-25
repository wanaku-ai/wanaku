#!/bin/bash
# Warms up javadoc.io caches for all publishable modules in this project.
# Usage: ./docs/javadoc-warm.sh <version>

set -euo pipefail

VERSION="${1:?Usage: $0 <version>}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

find "$PROJECT_ROOT" -name "pom.xml" \
    -not -path "*/target/*" \
    -not -path "*/archetype-resources/*" \
    -not -path "*/tests/*" \
    | sort | while read -r pom; do

    read -r gid aid pkg < <(python3 - "$pom" <<'PYEOF'
import xml.etree.ElementTree as ET, sys
ns = '{http://maven.apache.org/POM/4.0.0}'
tree = ET.parse(sys.argv[1])
root = tree.getroot()

aid_el = root.find(ns + 'artifactId')
aid = aid_el.text if aid_el is not None else ''

pkg_el = root.find(ns + 'packaging')
pkg = pkg_el.text if pkg_el is not None else 'jar'

gid_el = root.find(ns + 'groupId')
if gid_el is None:
    parent = root.find(ns + 'parent')
    if parent is not None:
        gid_el = parent.find(ns + 'groupId')
gid = gid_el.text if gid_el is not None else ''

print(f'{gid} {aid} {pkg}')
PYEOF
    )

    if [[ "$pkg" == "pom" || "$pkg" == "maven-archetype" ]]; then
        continue
    fi

    url="https://javadoc.io/doc/${gid}/${aid}/${VERSION}"
    echo "Warming: ${gid}:${aid}:${VERSION} -> ${url}"
    curl -sf -o /dev/null "$url" || echo "  WARNING: failed for ${aid}"
done

echo "Done."
