package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectedTestSkippedTest {

    @Test
    fun `test without markers is not skipped`() {
        val test = CollectedTest(
            nodeId = "test_foo.py::test_example",
            modulePath = "test_foo.py",
            className = null,
            functionName = "test_example",
            fixtures = emptyList(),
            markers = emptyList(),
        )
        assertFalse(test.isSkipped)
    }

    @Test
    fun `test with skip marker is skipped`() {
        val test = CollectedTest(
            nodeId = "test_foo.py::test_example",
            modulePath = "test_foo.py",
            className = null,
            functionName = "test_example",
            fixtures = emptyList(),
            markers = listOf("skip"),
        )
        assertTrue(test.isSkipped)
    }

    @Test
    fun `test with skipif marker is skipped`() {
        val test = CollectedTest(
            nodeId = "test_foo.py::test_example",
            modulePath = "test_foo.py",
            className = null,
            functionName = "test_example",
            fixtures = emptyList(),
            markers = listOf("skipif"),
        )
        assertTrue(test.isSkipped)
    }

    @Test
    fun `test with other markers is not skipped`() {
        val test = CollectedTest(
            nodeId = "test_foo.py::test_example",
            modulePath = "test_foo.py",
            className = null,
            functionName = "test_example",
            fixtures = emptyList(),
            markers = listOf("slow", "integration"),
        )
        assertFalse(test.isSkipped)
    }

    @Test
    fun `test with skip among other markers is skipped`() {
        val test = CollectedTest(
            nodeId = "test_foo.py::test_example",
            modulePath = "test_foo.py",
            className = null,
            functionName = "test_example",
            fixtures = emptyList(),
            markers = listOf("slow", "skip", "integration"),
        )
        assertTrue(test.isSkipped)
    }

    @Test
    fun `test in skipped class inherits skip marker`() {
        // When a class is marked with @pytest.mark.skip, pytest propagates
        // the marker to all test methods via iter_markers()
        val test = CollectedTest(
            nodeId = "test_foo.py::TestSkippedClass::test_method",
            modulePath = "test_foo.py",
            className = "TestSkippedClass",
            functionName = "test_method",
            fixtures = emptyList(),
            markers = listOf("skip"),  // inherited from class
        )
        assertTrue(test.isSkipped)
    }

    @Test
    fun `test in nested skipped class inherits skip marker`() {
        // Nested classes also propagate markers
        val test = CollectedTest(
            nodeId = "test_foo.py::TestOuter::TestInner::test_nested",
            modulePath = "test_foo.py",
            className = "TestInner",
            functionName = "test_nested",
            fixtures = emptyList(),
            markers = listOf("skip"),  // inherited from nested class
        )
        assertTrue(test.isSkipped)
    }

    @Test
    fun `test in skipped module inherits skip marker`() {
        // When pytestmark = [pytest.mark.skip] is set at module level,
        // pytest propagates the marker to all tests via iter_markers()
        val test = CollectedTest(
            nodeId = "test_skipped_module.py::test_function",
            modulePath = "test_skipped_module.py",
            className = null,
            functionName = "test_function",
            fixtures = emptyList(),
            markers = listOf("skip"),  // inherited from module-level pytestmark
        )
        assertTrue(test.isSkipped)
    }

    @Test
    fun `test with skipif in class is skipped`() {
        val test = CollectedTest(
            nodeId = "test_foo.py::TestConditionalSkip::test_method",
            modulePath = "test_foo.py",
            className = "TestConditionalSkip",
            functionName = "test_method",
            fixtures = emptyList(),
            markers = listOf("skipif"),  // inherited from class with @pytest.mark.skipif
        )
        assertTrue(test.isSkipped)
    }
}
