d = {}
try:
    x = d["a"]["b"]<caret>
except KeyError:
    x = 1
