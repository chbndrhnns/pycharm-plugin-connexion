package com.github.chbndrhnns.betterpy.features.pytest.outcome

import fixtures.TestBase

class PytestStacktraceParserTest : TestBase() {

    fun `test parseFailedLine extracts line number from simple assertion failure`() {
        val stacktrace = """
            def test_failing():
                expected = "hello"
                actual = "world"
            >       assert expected == actual
            E       AssertionError: assert 'hello' == 'world'
            E
            E         - world
            E         + hello

            test_sample.py:4: AssertionError
        """.trimIndent()

        val locationUrl = "python</tmp>://test_sample.test_failing"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(4, result)
    }

    fun `test parseFailedLine extracts line number from test in class`() {
        val stacktrace = """
            self = <test_module.TestMyClass object at 0x7f8b8c0d4d90>

                def test_method(self):
            >           assert False
            E           AssertionError

            test_module.py:10: AssertionError
        """.trimIndent()

        val locationUrl = "python</path/to/tests>://test_module.TestMyClass.test_method"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(10, result)
    }

    fun `test parseFailedLine returns -1 when stacktrace is null`() {
        val result = PytestStacktraceParser.parseFailedLine(null, "python</path>://test_file.test_func")

        assertEquals(-1, result)
    }

    fun `test parseFailedLine returns -1 when stacktrace is blank`() {
        val result = PytestStacktraceParser.parseFailedLine("", "python</path>://test_file.test_func")

        assertEquals(-1, result)
    }

    fun `test parseFailedLine returns -1 when locationUrl is null`() {
        val stacktrace = "test_file.py:4: AssertionError"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, null)

        assertEquals(-1, result)
    }

    fun `test parseFailedLine handles multiple occurrences and returns last one`() {
        val stacktrace = """
            test_nested.py:5: in wrapper
                return func(*args)
            test_nested.py:10: in test_func
            >       assert value == expected
            E       AssertionError

            test_nested.py:10: AssertionError
        """.trimIndent()

        val locationUrl = "python</path>://test_nested.test_func"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(10, result)
    }

    fun `test parseFailedLine handles parametrized test`() {
        val stacktrace = """
            a = 1, b = 2, expected = 4

                @pytest.mark.parametrize("a,b,expected", [(1, 2, 3), (1, 2, 4)])
                def test_add(a, b, expected):
            >       assert a + b == expected
            E       assert (1 + 2) == 4

            test_param.py:7: AssertionError
        """.trimIndent()

        val locationUrl = "python</path>://test_param.test_add"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(7, result)
    }

    fun `test parseFailedLine returns -1 when filename doesn't match`() {
        val stacktrace = """
            other_file.py:4: AssertionError
        """.trimIndent()

        val locationUrl = "python</path>://test_file.test_func"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(-1, result)
    }

    fun `test parseFailedLine handles complex module paths`() {
        val stacktrace = """
            >       assert result == expected
            E       AssertionError

            test_complex_module.py:42: AssertionError
        """.trimIndent()

        val locationUrl = "python</src/tests>://test_complex_module.TestClass.test_method"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(42, result)
    }

    fun `test parseFailedLine with real pytest output format`() {
        val stacktrace = """
            ========================= 1 failed, 1 warning in 0.11s =========================
            FAILED                                                   [100%]
            tests/test_.py:30 (test_)
            1 != 2

            Expected :2
            Actual   :1
            <Click to see difference>

            def test_():
                    do()
            >       assert 1 == 2
            E       assert 1 == 2

            test_.py:33: AssertionError
        """.trimIndent()

        val locationUrl = "python</path/to/tests>://test_.test_"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(33, result)
    }

    fun `test parseFailedLine with package path in locationUrl`() {
        val stacktrace = """
            def test_():
                    do()
            >       assert 1 == 2
            E       assert 1 == 2

            test_.py:33: AssertionError
        """.trimIndent()

        // LocationUrl contains package.module.function pattern
        val locationUrl = "python</Users/jo/PyCharmMiscProject/tests>://tests.test_.test_"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(33, result)
    }

    fun `test parseFailedLine with nested package path`() {
        val stacktrace = """
            >       assert result == expected
            E       AssertionError

            test_feature.py:10: AssertionError
        """.trimIndent()

        val locationUrl = "python</project>://package.subpackage.test_feature.test_function"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(10, result)
    }
    fun `test parseFailedLine with deeply nested classes`() {
        val stacktrace = """
            >       assert 1 == 2
            E       AssertionError: assert 1 == 2

            test_.py:33: AssertionError
        """.trimIndent()

        // As reported in the issue: tests.test_.TestParent.TestChild.TestGrandChild.test_1
        val locationUrl = "python</path/to/project/tests>://tests.test_.TestParent.TestChild.TestGrandChild.test_1"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals("Should extract line 33 from test_.py", 33, result)
    }

    fun `test parseFailedLine with RuntimeError non-assertion failure`() {
        val stacktrace = """
            FAILED                                                   [100%]
            tests/test_.py:1 (test_)
            test_.py:3: in test_
                raise RuntimeError()
            E   RuntimeError
        """.trimIndent()

        val locationUrl = "python</path/to/tests>://tests.test_.test_"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(3, result)
    }

    fun `test parseFailedLine with ValueError non-assertion failure`() {
        val stacktrace = """
            test_errors.py:5: in test_value_error
                raise ValueError("Invalid value")
            E   ValueError: Invalid value
        """.trimIndent()

        val locationUrl = "python</path>://test_errors.test_value_error"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals(5, result)
    }

    fun `test parseFailedLine shows failure at call site not in helper`() {
        val stacktrace = """
            def test_():
            >       helper()

            test_.py:5:
            _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _

                def helper():
            >       1/0
            E       ZeroDivisionError: division by zero

            test_.py:2: ZeroDivisionError
        """.trimIndent()

        val locationUrl = "python</path>://test_.test_"

        val result = PytestStacktraceParser.parseFailedLine(stacktrace, locationUrl)

        assertEquals("Should return line 5 (call site in test) not line 2 (error in helper)", 5, result)
    }
}
