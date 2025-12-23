# Abstract Methods

Tools to manage abstract classes and methods.

## Implement Abstract Method in Child Classes

When invoked on an abstract method (decorated with `@abstractmethod`), this intention automatically creates implementation stubs for that method in all concrete subclasses.

## Make Member Abstract in Abstract Class

Converts a regular method in an abstract class into an abstract method by adding the `@abstractmethod` decorator and replacing the body with `...` or `raise NotImplementedError()`.

## Inspections

### Abstract Method Not Implemented
Warns you when a concrete subclass has not implemented one or more abstract methods from its base classes.
