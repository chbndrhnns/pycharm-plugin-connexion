class PublicClass:
    pass


def <warning descr="Symbol 'public_function' is not exported in __all__">public_function</warning>():
    pass


__all__ = [
    "PublicClass",
]
