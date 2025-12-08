d = {}
def heavy(): return 1
try:
    x = d["k"]<caret>
except KeyError:
    x = heavy()
