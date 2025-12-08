def foo(d, key):
    try:
        return d[key]<caret>
    except KeyError:
        return None
