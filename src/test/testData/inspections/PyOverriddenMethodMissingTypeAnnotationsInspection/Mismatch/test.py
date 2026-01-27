class Base:
    def method(self, value: int, flag: bool) -> str:
        pass


class Child(Base):
    def method(self, value: str, flag: bool) -> int:
        pass
