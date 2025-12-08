def read_value(d):
    try:
        return d["k"]
    except KeyError:
        return fallback