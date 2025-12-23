# Documentation Development

This project uses [MkDocs](https://www.mkdocs.org/) with the [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) theme to manage its documentation.

## Local Setup

To serve and preview the documentation locally, you'll need [uv](https://docs.astral.sh/uv/) installed.

1.  **Serve the documentation:**
    ```bash
    uv run mkdocs serve
    ```
    This will automatically create a virtual environment, install the required dependencies (as defined in `pyproject.toml`), and start a local server, usually at `http://127.0.0.1:8000/`. The page will automatically reload when you make changes to the files in the `docs/` directory or `mkdocs.yml`.

2.  **Add a new dependency:**
    ```bash
    uv add <package-name>
    ```

## Project Structure

- `mkdocs.yml`: Configuration file for the documentation (theme, navigation, extensions).
- `docs/`: Directory containing the Markdown source files for the documentation.
- `docs/index.md`: The home page of the documentation.
