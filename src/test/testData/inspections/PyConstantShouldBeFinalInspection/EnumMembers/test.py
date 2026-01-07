from enum import Enum

class Status(Enum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"

class Color(str, Enum):
    RED = "red"
    GREEN = "green"
    BLUE = "blue"

# This should still be flagged as it's a module-level constant
<weak_warning descr="Constant 'CONSTANT_VALUE' should be declared as Final">CONSTANT_VALUE</weak_warning> = 42
