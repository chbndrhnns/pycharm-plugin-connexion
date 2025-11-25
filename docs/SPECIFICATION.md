# Plugin Specification

## Overview

The Python DDD Toolkit plugin provides a set of IntelliJ Intentions to facilitate Domain-Driven Design (DDD) in Python
projects. It focuses on "Strong Typing" by making it easy to introduce and use custom domain types (Value Objects)
instead of primitive types.

## Features

### 1. Wrap with Expected Type

Automatically wraps an expression that causes a type mismatch with the expected type constructor.

* **Trigger**: Triggered when there is a type mismatch between an expression and the expected type (determined by type
  hints).
* **Action**: Wraps the expression with the constructor of the expected type.
* **Supported Scenarios**:
    * **Single Types**: Wraps `value` into `ExpectedType(value)`.
    * **Union Types**: Displays a popup allowing the user to choose which type from the union to use for wrapping.
    * **Container Elements**: Detects when an item inside a list, set, or tuple literal mismatches the expected item
      type and offers to wrap that specific item.
* **Prerequisites**:
    * The expected type must be a class with a constructor accepting the expression.
    * Type hints must be present to infer the expected type.
* **Exclusions**:
    * Does not trigger if `Unwrap` would resolve the mismatch.
    * Does not trigger for `typing.Literal` types.
    * Does not trigger if the caret is on `*args` or `**kwargs`.
    * Does not trigger on variable names in definition/assignment (LHS).

### 2. Wrap Items with Expected Type

Batch wrapping for container literals.

* **Trigger**: Triggered when a container literal (list, set, tuple) is used where a container of specific custom types
  is expected.
* **Action**: Wraps *all* items in the container with the expected item type constructor.
* **Supported Scenarios**:
    * **Lists, Sets, Tuples**: `[1, 2]` -> `[MyInt(1), MyInt(2)]` when `List[MyInt]` is expected.
    * **Generic Mappings**: Maps generic container expectations (`Sequence`, `Iterable`, `Collection`) to `list`.
* **Limitations**:
    * **Dictionaries**: Not supported. Explicitly excluded due to ambiguity between wrapping keys vs. values.

### 3. Unwrap to Expected Type

Removes unnecessary wrappers when the inner value matches the expected type.

* **Trigger**: Triggered on a wrapper call (e.g., `MyId(1)`) when the inner value (e.g., `1`) effectively matches the
  expected type at that position.
* **Action**: Replaces the wrapper call with the inner expression.
* **Supported Scenarios**:
    * Assignments, function arguments, return statements where the underlying type is expected (e.g., passing a `UserId`
      where an `int` is required, if valid).
* **Exclusions**:
    * Does not unwrap container constructors (e.g., `list(...)`, `set(...)`).

### 4. Unwrap Items to Expected Type

Batch unwrapping for container literals.

* **Trigger**: Triggered on a container literal where items are wrapped, but the expected container item type matches
  the inner values.
* **Action**: Unwraps all items in the container.
* **Supported Scenarios**:
    * Lists, Sets, Tuples.
* **Limitations**:
    * **Dictionaries**: Not supported.

### 5. Introduce Custom Type from Stdlib

Refactors primitive type usages into new custom domain types (Value Objects).

* **Trigger**: specific builtin types (e.g., `int`, `str`) in annotations, default values, or variable assignments.
* **Action**:
    1. Generates a new class inheriting from the primitive type (e.g., `class CustomName(int): pass`).
    2. Updates the reference to use the new class.
    3. Wraps the value if necessary.
* **Naming Strategy**: Derives the new class name from the context:
    * Variable name (e.g., `user_id: int` -> `CustomUserId`).
    * Parameter name.
    * Keyword argument name.
* **Supported Scenarios**:
    * **Annotations**: Updates type hints, including nested `Union` types.
    * **Default Values**: Wraps default values in parameters.
    * **Assignments**: Wraps assigned values.
* **Prerequisites**:
    * Must be within project content (ignores library code).
    * No blocking inspections (e.g., syntax errors) at the caret.

## General Assumptions & Prerequisites

* **Project Scope**: The plugin primarily operates on source code within the project. Library code is excluded from
  modification.
* **Type System**: Relies heavily on the IntelliJ/PyCharm Python type inference engine (`TypeEvalContext`). Correct
  behavior depends on accurate type analysis.
* **Language**: Supports Python. Tests assume Python 3 syntax (type hints).

## Not Supported / Future Work

* **Dictionaries**: Wrapping/Unwrapping items in dictionaries (keys or values) is not currently supported.
* **Typing.Literal**: The plugin explicitly ignores `Literal` types to avoid invalid wrapping suggestions.
* **Star Arguments**: `*args` and `**kwargs` are excluded from processing.
