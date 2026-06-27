---
name: html-document-generation
description: Generate or update project-local HTML document pages for human review. Use when Codex should transfer an existing document such as `.md`, `.txt`, `.doc`, or `.docx` into HTML page(s), or create a previously non-existent document directly in HTML format. Prefer stronger structure for readability, including tabs or multiple linked sub-pages when useful, and always include text-selection comments plus export flows for reviewer feedback.
---

# HTML Document Generation

Create HTML artifacts for this repo under `docs/`.

Use this skill for:

- Converting an existing `.md`, `.txt`, `.doc`, or `.docx` document into HTML page(s)
- Creating a new document directly in HTML when no source document exists yet
- Upgrading an existing HTML doc for better structure or readability
- Breaking long material into tabs or linked sub-pages
- Adding required comment capture on selected text
- Exporting comments as JSON or prompt-ready text

## Workflow

1. Read the source doc when one exists, and read `docs/README.md`.
2. If there is no source doc yet, derive the structure from the user request and create the document directly in HTML.
3. Choose the structure that best improves human readability:
   - one standalone page for short docs
   - tabs for long but closely related sections
   - multiple linked sub-pages when one page would become crowded or hard to scan
4. Always include review interactions:
   - text selection limited to one paragraph or bullet at a time
   - a fixed or sticky review sidebar
   - local persistence with `localStorage`
   - export to JSON and prompt-ready plain text
5. Write the HTML under `docs/` with stable names such as `prd.html`, `technical-design.html`, or a small linked set of review pages.
6. Update `docs/README.md` when adding a new HTML artifact.

## Project Conventions

- Keep the page standalone: HTML, CSS, and JS in one file unless the user asks otherwise.
- Preserve the meaning of the source doc; improve structure, not product intent.
- Optimize for human reviewers first.
- Prefer tabs when the document is long enough to benefit from segmentation.
- Prefer multiple linked sub-pages when tabs alone would still feel dense.
- Keep copy minimal. Avoid decorative hero text unless the user asks for it.
- Always include commenting and export behavior on generated review pages.

## Review Interaction Pattern

Generated HTML review pages should always support comments:

- Treat each paragraph or bullet as a commentable block.
- Store the selected text, block id, section/tab id, freeform comment, and timestamp.
- Keep the last valid selection available even after focus moves to the comment box.
- Allow jump-to-comment behavior so the reviewer can revisit the source block.
- Highlight commented ranges when practical, but do not break text selection.

## Export Pattern

Support two exports when comments exist:

- Structured JSON for machines or future tooling
- Prompt-ready text for the next editing pass

Prompt export should include:

- section name
- selected text
- reviewer comment
- a short instruction telling the next agent to update the source doc or HTML accordingly

## Reference Implementation

Use `docs/prd.html` as the current in-repo example of this pattern.

Use `assets/review-page-template.html` as a lightweight starting point when creating a new page from scratch.
