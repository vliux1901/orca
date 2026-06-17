# Project Context

Project structure:

- The project follows the typical Android app structure created by Android Studio.
- The `docs/` folder contains the authoritative project documentation. To find a
specific document, read `docs/README.md`.

Working rules:

- Before implementing or changing behavior, check relevant files in `docs/`.
- If code and docs disagree, surface the discrepancy instead of guessing.
- Keep docs updated when product behavior or requirements change.
- Do the following verifications every time product code change is changed:
    - Run build.
    - Updated or added unit tests for the changes.
    - Run all the related tests.
