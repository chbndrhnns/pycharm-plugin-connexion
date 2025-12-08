d = {}
try:
    x = d["k"]<caret>
except (KeyError, IndexError):
    x = 1
