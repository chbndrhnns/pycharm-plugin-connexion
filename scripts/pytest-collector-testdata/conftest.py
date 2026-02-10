"""
Sample conftest.py with fixtures of various scopes and dependencies.
Used to generate test data for PytestOutputParser integration tests.
"""
import pytest


@pytest.fixture(autouse=True)
def cleanup():
    """Autouse fixture that runs for every test."""
    yield
    # teardown


@pytest.fixture
def db_engine():
    """Simulates a database engine."""
    return "engine"


@pytest.fixture
def db_session(db_engine):
    """Simulates a database session depending on db_engine."""
    return f"session({db_engine})"


@pytest.fixture(scope="module")
def settings():
    """Module-scoped settings fixture."""
    return {"debug": True}
