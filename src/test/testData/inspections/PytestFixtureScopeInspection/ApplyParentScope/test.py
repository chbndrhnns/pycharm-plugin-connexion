import pytest


@pytest.fixture(scope="session")
def abcdefg(dep):
    return 1


@pytest.fixture
def dep():
    return 2


def test(abcdefg):
    assert abcdefg
