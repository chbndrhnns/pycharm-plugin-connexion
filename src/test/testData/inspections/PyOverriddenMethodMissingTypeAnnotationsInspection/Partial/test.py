class Base:
    def method(self, value: int, flag: bool) -> str:
        pass
class Child(Base):
    def <weak_warning descr="Override is missing type annotations from parent method">method</weak_warning>(self, value, flag: bool) -> str:
        pass
