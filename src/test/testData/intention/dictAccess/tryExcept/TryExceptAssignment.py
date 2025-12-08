d = {}
key = "k"
try:
    val = d[key]<caret>
except KeyError:
    val = "default"
