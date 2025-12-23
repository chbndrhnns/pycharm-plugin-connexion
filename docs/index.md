# Python BetterPy

Python BetterPy is an IntelliJ Platform plugin designed to facilitate Domain-Driven Design (DDD) in Python projects.

## Purpose

The plugin focuses on **"Strong Typing"** by making it easy to introduce and use custom domain types (Value Objects) instead of primitive types. It provides a suite of intentions, inspections, and actions to streamline your Python development workflow, especially when applying DDD principles.

## Key Features

- **Type Mismatch Helpers**: Automatically wrap or unwrap values to match expected type hints.
- **Custom Type Refactorings**: Easily extract primitive types into domain-specific Value Objects.
- **Argument Population**: Fill function call arguments based on signatures for dataclasses, Pydantic models, and more.
- **Refactoring Tools**: Introduce Parameter Object, toggle visibility, and copy code blocks with dependencies.
- **Pytest Integration**: Enhanced navigation and actions for pytest suites.
- **Connexion/OpenAPI Support**: Navigation and inspections for Connexion-based projects.

## Target Audience

Python developers using PyCharm or IntelliJ IDEA who want to:
- Apply Domain-Driven Design principles.
- Improve type safety in their codebases.
- Increase productivity with smart intentions and refactorings.

## Prerequisites

- **IDE**: PyCharm 2025.2+ or IntelliJ IDEA 2025.2+
- **Plugin**: Python plugin must be installed and enabled.
