from typing import Final
import re

ONE: Final[re.Match[str] | None] = re.match("abc", "abc")
