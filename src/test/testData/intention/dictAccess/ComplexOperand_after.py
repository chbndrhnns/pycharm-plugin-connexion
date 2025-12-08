class A:
    def __add__(self, other):
        return {}
a = A()
b = A()
x = (a + b).get("k")
