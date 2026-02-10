"""
Sample test file with class-based tests, standalone functions, and parametrized tests.
Used to generate test data for PytestOutputParser integration tests.
"""
import pytest


class TestLogin:
    def test_success(self, db_session):
        assert db_session is not None

    def test_failure(self, db_session):
        assert db_session is not None


def test_standalone(settings):
    assert settings["debug"] is True


@pytest.mark.parametrize("x,y,expected", [(1, 2, 3), (4, 5, 9)])
def test_add(x, y, expected):
    assert x + y == expected
