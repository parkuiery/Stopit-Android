```markdown
# Stopit-Android Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches the development patterns and conventions used in the Stopit-Android repository, a Python codebase with no detected framework. You'll learn about file naming, import/export styles, commit patterns, and how to write and organize tests. This guide is ideal for contributors aiming for consistency and maintainability in this project.

## Coding Conventions

### File Naming
- Use **kebab-case** for all file names.
  - Example: `user-profile.py`, `data-manager.py`

### Import Style
- Use **relative imports** within the package.
  - Example:
    ```python
    from .utils import helper_function
    ```

### Export Style
- Use **named exports** (i.e., explicitly define what is exported).
  - Example:
    ```python
    __all__ = ['MyClass', 'my_function']
    ```

### Commit Patterns
- Follow **conventional commits**.
- Use prefixes like `ci` for commit messages.
- Keep commit messages concise (average 36 characters).
  - Example: `ci: update deployment workflow`

## Workflows

### Commit Changes
**Trigger:** When you make code changes and are ready to commit.
**Command:** `/commit-changes`

1. Stage your changes:
    ```bash
    git add .
    ```
2. Write a conventional commit message, e.g.:
    ```bash
    git commit -m "ci: fix bug in data-manager"
    ```
3. Push your changes:
    ```bash
    git push
    ```

### Add a New Module
**Trigger:** When you need to create a new Python module.
**Command:** `/add-module`

1. Create a new file using kebab-case, e.g., `new-feature.py`.
2. Use relative imports for any internal dependencies.
    ```python
    from .existing-module import ExistingClass
    ```
3. Define `__all__` to specify exports.
    ```python
    __all__ = ['NewFeatureClass']
    ```
4. Write code following the project's style.

### Write and Run Tests
**Trigger:** When adding new features or fixing bugs.
**Command:** `/run-tests`

1. Create a test file using the pattern `*.test.*`, e.g., `user-profile.test.py`.
2. Write your tests (framework is unknown—follow existing patterns).
3. Run tests using the project's test runner (consult project docs or use typical Python test runners like `pytest` if unsure).

## Testing Patterns

- Test files follow the pattern: `*.test.*` (e.g., `data-manager.test.py`).
- The testing framework is **unknown**; review existing test files for conventions.
- Place tests alongside or near the modules they test.
- Example test file structure:
    ```python
    # data-manager.test.py

    from .data-manager import DataManager

    def test_data_manager_behavior():
        dm = DataManager()
        assert dm.do_something() == expected_result
    ```

## Commands
| Command         | Purpose                                 |
|-----------------|-----------------------------------------|
| /commit-changes | Commit code changes with conventions    |
| /add-module     | Add a new module following conventions  |
| /run-tests      | Run the test suite                      |
```
