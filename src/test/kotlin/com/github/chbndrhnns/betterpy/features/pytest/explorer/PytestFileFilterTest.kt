package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.trigger.PytestFileFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PytestFileFilterTest {

    @Test
    fun `test_prefixed python file matches`() {
        assertTrue(PytestFileFilter.isTestRelatedFile("test_auth.py"))
    }

    @Test
    fun `_test suffixed python file matches`() {
        assertTrue(PytestFileFilter.isTestRelatedFile("auth_test.py"))
    }

    @Test
    fun `conftest matches`() {
        assertTrue(PytestFileFilter.isTestRelatedFile("conftest.py"))
    }

    @Test
    fun `regular python file does not match`() {
        assertFalse(PytestFileFilter.isTestRelatedFile("models.py"))
    }

    @Test
    fun `non-python file does not match`() {
        assertFalse(PytestFileFilter.isTestRelatedFile("test_auth.txt"))
    }

    @Test
    fun `null does not match`() {
        assertFalse(PytestFileFilter.isTestRelatedFile(null))
    }

    @Test
    fun `empty string does not match`() {
        assertFalse(PytestFileFilter.isTestRelatedFile(""))
    }

    @Test
    fun `conftest without py extension does not match`() {
        assertFalse(PytestFileFilter.isTestRelatedFile("conftest.txt"))
    }

    @Test
    fun `test file with path does not match - only filename`() {
        assertFalse(PytestFileFilter.isTestRelatedFile("tests/test_auth.py"))
    }
}
