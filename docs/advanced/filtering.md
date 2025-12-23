# Console & Usage Filtering

Tools to reduce noise and improve focus during development and debugging.

## Console Filter

The **Py Message Console Filter** automatically processes output in the Python console and execution tool windows. It can highlight or hide specific patterns, making it easier to spot relevant information in verbose logs.

## Usage Filtering Rules

The plugin introduces custom rules for the **Find Usages** (++alt+f7++) functionality.

### Type Annotation Usage Filtering
Allows you to filter out usages that only occur within type annotations. This is extremely useful when you want to find where a class is actually *instantiated* or *called*, rather than just where its name is used as a type hint.
