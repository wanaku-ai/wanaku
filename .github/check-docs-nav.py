#!/usr/bin/env python3
"""Check the docs/ tree against the page conventions in docs/contributing/documentation.md."""
import json
import re
import sys
from pathlib import Path

DOCS = Path("docs")
errors = []


def slug(page):
    """Return the nav.json link of a page: "operate/kubernetes", or "operate/" for a hub."""
    rel = page.relative_to(DOCS).with_suffix("").as_posix()
    return rel[: -len("index")] if rel.endswith("index") else rel


def frontmatter(page, text):
    if not text.startswith("---\n") or "\n---\n" not in text[4:]:
        return None
    fields = {}
    for line in text[4 : text.index("\n---\n", 4)].splitlines():
        key, _, value = line.partition(": ")
        if value.startswith('"') and value.endswith('"'):
            value = value[1:-1]
        elif ": " in value:
            errors.append(f"{page}: {key} has an unquoted ': ', quote the value")
        fields[key] = value
    return fields


nav_links = []


def walk(items):
    for item in items:
        nav_links.append(item["link"])
        walk(item.get("items", []))


walk(json.loads((DOCS / "nav.json").read_text())["sidebar"])

pages = sorted(DOCS.rglob("*.md"))
slugs = {slug(p) for p in pages}
titles, descriptions = {}, {}

for page in pages:
    text = page.read_text()
    body = re.sub(r"```.*?```", "", text, flags=re.S)
    if page != DOCS / "index.md" and nav_links.count(slug(page)) != 1:
        errors.append(f"{page}: appears {nav_links.count(slug(page))} times in docs/nav.json, expected 1")
    hub = page.parent / "index.md"
    if page.name != "index.md" and page.parent != DOCS and f"]({page.name}" not in hub.read_text():
        errors.append(f"{page}: not linked from {hub}")
    fields = frontmatter(page, text)
    if fields is None:
        errors.append(f"{page}: no frontmatter")
        continue
    title, description = fields.get("title", ""), fields.get("description", "")
    if not 0 < len(title) <= 60:
        errors.append(f"{page}: title has {len(title)} characters, expected 1-60")
    if not 120 <= len(description) <= 155:
        errors.append(f"{page}: description has {len(description)} characters, expected 120-155")
    for value, seen, name in ((title, titles, "title"), (description, descriptions, "description")):
        if value in seen:
            errors.append(f"{page}: same {name} as {seen[value]}")
        seen[value] = page
    if re.search(r"^#+ Related Docs", body, re.M | re.I):
        errors.append(f"{page}: has a 'Related Docs' heading, use 'Next steps'")
    for target in re.findall(r"\]\(([^)#\s]+)", body):
        if re.match(r"[a-z]+:", target):
            continue
        resolved = (page.parent / target).resolve()
        if not resolved.is_relative_to(DOCS.resolve()):
            errors.append(f"{page}: link {target} leaves docs/, use a https://github.com/wanaku-ai/wanaku URL")

for link in nav_links:
    if link not in slugs:
        errors.append(f"docs/nav.json: link {link} has no page")

for line in (DOCS / "redirects.txt").read_text().splitlines():
    if line.strip() and not line.startswith("#") and line.split()[-1] not in slugs:
        errors.append(f"docs/redirects.txt: target {line.split()[-1]} has no page")

print("\n".join(errors) or f"OK: {len(pages)} pages")
sys.exit(1 if errors else 0)
