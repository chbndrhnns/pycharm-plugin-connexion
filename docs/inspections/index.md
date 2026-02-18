# Inspections

The plugin provides several inspections to ensure code quality and adherence to DDD principles.

## DDD Related Inspections

### Missing Dataclass Decorator
Warns when a class appears to be used as a Value Object (e.g., it's a subclass of another Value Object or used in wrapping intentions) but is missing the `@dataclass` decorator.

## Python Language Inspections

### Missing Symbol in `__all__`
Warns when a public symbol (class, function, or variable) is defined in a module but not included in its `__all__` list. Includes a quick-fix to add the symbol to `__all__`.

### Private Module Import
Warns when you import symbols from a "private" module (one whose name starts with an underscore). DDD encourages importing through public APIs.

### Shadowing Stdlib Module
Warns when a project module has the same name as a standard library module, which can lead to confusing import errors.

### Constant is not Final
Warns when a variable that looks like a constant (uppercase naming) is not annotated with `Final`.

## Abstract Method Inspections

### Abstract Method Not Implemented
Ensures all abstract methods are implemented in concrete subclasses.

