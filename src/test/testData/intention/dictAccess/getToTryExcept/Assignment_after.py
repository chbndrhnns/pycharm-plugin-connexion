d = {"2": "a"}
try:
    x = d["2"]
except KeyError:
    x = "1"