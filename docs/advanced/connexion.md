# Connexion / OpenAPI Support

The plugin includes specialized support for the [Connexion](https://connexion.readthedocs.io/) framework, which is often used in DDD-oriented Python microservices.

## Navigation

- **Go to Handler**: From an `operationId` in a YAML/JSON OpenAPI specification, use ++ctrl+click++ or ++cmd+click++ (or ++ctrl+b++) to jump directly to the Python function handling that request.
- **Go to Spec**: From a Python function used as a handler, you can navigate back to the corresponding operation definition in the OpenAPI spec.

## Inspections

- **Invalid Operation ID**: Warns if the `operationId` in the specification does not point to a valid, reachable Python function.
- **Missing Parameters**: (Planned/Experimental) Basic validation that the Python handler signature matches the parameters defined in the OpenAPI spec.

## Completion

- Provides completion for `operationId` strings in the OpenAPI specification files, suggesting available Python functions.
