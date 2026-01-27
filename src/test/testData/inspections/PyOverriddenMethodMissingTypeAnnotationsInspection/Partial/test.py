class Base:
    def method(self, value: int, flag: bool) -> str:
        pass


class Child(Base):
    def method(self, <weak_warning descr="Override is missing type annotations from parent method">value</weak_warning>, flag: bool) -> str:
        pass
