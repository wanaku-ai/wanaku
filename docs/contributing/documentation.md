---
title: Write Wanaku documentation
description: Page conventions for the Wanaku docs, with files and slugs, frontmatter, page skeleton, cross-links, headings, and the checks that CI runs on docs.
---

# Write Wanaku documentation

This page describes the shape of a Wanaku documentation page and the checks that CI runs on the `docs/` tree. The wording rules (ASD-STE100 for technical content, plain technical English for framing text) are in [CLAUDE.md](https://github.com/wanaku-ai/wanaku/blob/main/CLAUDE.md#documentation).

## Sections

The `docs/` tree has one folder for each reader task:

| Folder | Reader task |
|---|---|
| `get-started/` | Install Wanaku and learn how it works. |
| `operate/` | Deploy, secure, and run Wanaku. |
| `connect/` | Connect agents and backends. |
| `govern/` | Control what agents can do. |
| `extend/` | Add features, evaluator actions, and Admin UI plugins. |
| `reference/` | Look up configuration, ports, and API routes. |
| `contributing/` | Develop and test Wanaku. |

Each folder has an `index.md` hub. The hub lists every page in its folder. Keep a hub at 70 lines or fewer.

## Files and slugs

- Use lowercase kebab-case file names with four words or fewer. The folder supplies the group name: use `operate/kubernetes.md`, not `operate/operate-kubernetes.md`.
- Use a verb-object slug for a tutorial or a how-to, for example `govern-an-mcp-server.md`. Use a noun for a reference or a concept page, for example `environment-variables.md`.
- Do not put version numbers or a `-guide` suffix in new slugs.
- A slug is a published URL. When you move or rename a page, add a line to `docs/redirects.txt` in the same pull request. Each line has the old slug and the new slug.

## Frontmatter

Each page starts with a `title` and a `description`:

```markdown
---
title: Deploy Wanaku on Kubernetes and OpenShift
description: Deploy Wanaku on Kubernetes or OpenShift with a ConfigMap, a Secret, a Deployment, and a Service, or use the Wanaku Operator for production.
---
```

- `title` has 60 characters or fewer and is unique. Do not add a " | Wanaku" suffix. The site adds it.
- `description` has 120 to 155 characters and is unique. Write it in short, active sentences.
- If a value contains a colon followed by a space, put the value in double quotes. Otherwise, the frontmatter is not valid YAML.

## Page skeleton

```markdown
# H1 with the same meaning as the title

One to three sentences: what the reader can do after this page.

## Prerequisites (omit when empty)

## Body sections (specific H2s, never "Overview")

## Next steps (two to four links, nothing after this section)
```

Use one page type for each page:

| Type | H1 | Body |
|---|---|---|
| Tutorial | "Govern an MCP server" | Numbered steps with one visible result. |
| How-to | Verb first: "Deploy Wanaku on Kubernetes and OpenShift" | Goal, steps, and verification. |
| Reference | Noun: "Environment variables" | Tables and schemas, in alphabetical or schema order. |
| Explanation | Noun: "Architecture" | The model, a diagram, and the limits. |
| Troubleshooting | "FAQ and troubleshooting" | The symptom or the exact error as the heading, then the cause and the fix. |

Keep tutorials and how-tos at 350 lines or fewer, explanations at 500, and references at 800. Split a longer page by task.

## Links

- Use relative links with the `.md` extension, for example `../govern/action-policies.md`.
- Do not leave `docs/` with a relative link. The site publishes only the `docs/` tree. Link code, configuration, and example files with `https://github.com/wanaku-ai/wanaku/blob/main/<path>`.
- Link fragments only to H2 and H3 headings.
- Use the name of the destination page as the link text. Do not use "here".
- End each page with one "Next steps" section. Do not use "Related Docs".
- Add each page to `docs/nav.json` exactly once. The website builds the sidebar from this file.

## Headings and callouts

- Use one H1. Do not skip heading levels. Stop at H3.
- Use sentence case for new and renamed headings. Do not rename headings in a move-only change, because that breaks anchors.
- Do not number headings. Renumbering breaks anchors.
- Expand acronyms on first use in an H1 or a description, for example A2A (Agent2Agent) and OPA (Open Policy Agent).
- Use GitHub alert syntax for callouts: `> [!NOTE]`, `> [!IMPORTANT]`, and `> [!WARNING]`.
- Do not write a "Contents" list. GitHub and the site show an outline.

## Images

- Put images in `docs/imgs/` with the name `<page-slug>-<what>.png`.
- Add alt text to each image. Use PNG or WebP, 200 KB or less, and 1280 px wide or less.
- Use code blocks, not screenshots, for text.

## Check your changes

CI runs these checks on each pull request that changes Markdown or the docs navigation. Run them locally before you push:

```bash
python3 .github/check-docs-nav.py
npx markdownlint-cli2
lychee --offline --include-fragments --exclude-path .claude --exclude-path .specify --exclude-path .oss-ai-helper-rules --exclude-path CONTRIBUTING.md '**/*.md'
```

`.github/check-docs-nav.py` checks the rules on this page:

- Each page is in `docs/nav.json` exactly once, and each `nav.json` link has a page.
- Each page is linked from its folder hub.
- Each page has a `title` and a `description` of the correct length, and no other page has the same values.
- No relative link leaves `docs/`.
- No page has a "Related Docs" heading.
- Each `docs/redirects.txt` target has a page.

## Next steps

- [Contributing to Wanaku](index.md): the development workflow and the checks to run.
- [Testing](testing.md): run the test suites.
