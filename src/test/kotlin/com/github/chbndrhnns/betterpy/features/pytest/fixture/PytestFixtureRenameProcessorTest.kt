package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import fixtures.TestBase

class PytestFixtureRenameProcessorTest : TestBase() {

    fun testRenameFixtureRenamesClassOverrides() {
        myFixture.configureByText(
            "test_class.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def fi<caret>xture(self):
                    return "base"
            
            class TestDerived(BaseTest):
                @pytest.fixture
                def fixture(self):
                    return "derived"
        """.trimIndent()
        )

        val function = myFixture.elementAtCaret as PyFunction
        myFixture.renameElement(function, "renamed_fixture")

        myFixture.checkResult(
            "test_class.py", """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def renamed_fixture(self):
                    return "base"
            
            class TestDerived(BaseTest):
                @pytest.fixture
                def renamed_fixture(self):
                    return "derived"
        """.trimIndent(), true
        )
    }

    fun testRenameFixtureRenamesConftestHierarchy() {
        myFixture.addFileToProject(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def fixture():
                return "subdir"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "subdir/test_example.py", """
            import pytest
            
            @pytest.fixture
            def fixture():
                return "module"
        """.trimIndent()
        )

        myFixture.configureByText(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def fi<caret>xture():
                return "root"
        """.trimIndent()
        )

        val function = myFixture.elementAtCaret as PyFunction
        myFixture.renameElement(function, "renamed_fixture")

        myFixture.checkResult(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "root"
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "subdir"
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "subdir/test_example.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "module"
        """.trimIndent(), true
        )
    }

    fun testRenameFixtureRenamesAllRelatedElements() {
        myFixture.addFileToProject(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def fixture():
                return "subdir"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def fixture():
                return "root"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "subdir/test_other_example.py", """
            import pytest
            
            @pytest.fixture
            def fixture():
                return "module"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "subdir/test_example.py", """
            import pytest
            
            @pytest.fixture
            def fixt<caret>ure():
                return "module"
        """.trimIndent()
        )

        myFixture.configureByFile("subdir/test_example.py")

        val function = myFixture.elementAtCaret as PyFunction
        myFixture.renameElement(function, "renamed_fixture")

        myFixture.checkResult(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "root"
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "subdir"
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "subdir/test_example.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "module"
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "subdir/test_other_example.py", """
            import pytest
            
            @pytest.fixture
            def renamed_fixture():
                return "module"
        """.trimIndent(), true
        )
    }

    fun testRenameFromTestParameterRenamesLinkedFixturesAndArguments() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest

            @pytest.fixture
            def oldname():
                pass
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "test_b.py", """
            import pytest

            @pytest.fixture
            def oldname(oldname):
                return oldname

            def test_(oldname):
                assert oldname
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_a.py", """
            import pytest

            @pytest.fixture
            def oldname(oldname):
                return oldname

            def test_(old<caret>name):
                assert oldname
        """.trimIndent()
        )

        val parameter = PsiTreeUtil.findChildrenOfType(myFixture.file, PyNamedParameter::class.java)
            .firstOrNull { it.textRange.contains(myFixture.caretOffset) }
            ?: error("Expected caret on a fixture parameter")
        myFixture.renameElement(parameter, "newname")

        myFixture.checkResult(
            "conftest.py", """
            import pytest

            @pytest.fixture
            def newname():
                pass
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "test_a.py", """
            import pytest

            @pytest.fixture
            def newname(newname):
                return newname

            def test_(newname):
                assert newname
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "test_b.py", """
            import pytest

            @pytest.fixture
            def newname(newname):
                return newname

            def test_(newname):
                assert newname
        """.trimIndent(), true
        )
    }

    fun testRenameFromFixtureParameterRenamesLinkedFixturesAndArguments() {
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest

            @pytest.fixture
            def base():
                return "root"
        """.trimIndent()
        )

        myFixture.addFileToProject(
            "test_b.py", """
            import pytest

            @pytest.fixture
            def base(base):
                return base

            def test_(base):
                assert base
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_a.py", """
            import pytest

            @pytest.fixture
            def base(ba<caret>se):
                return base

            def test_(base):
                assert base
        """.trimIndent()
        )

        val parameter = PsiTreeUtil.findChildrenOfType(myFixture.file, PyNamedParameter::class.java)
            .firstOrNull { it.textRange.contains(myFixture.caretOffset) }
            ?: error("Expected caret on a fixture parameter")
        myFixture.renameElement(parameter, "renamed")

        myFixture.checkResult(
            "conftest.py", """
            import pytest

            @pytest.fixture
            def renamed():
                return "root"
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "test_a.py", """
            import pytest

            @pytest.fixture
            def renamed(renamed):
                return renamed

            def test_(renamed):
                assert renamed
        """.trimIndent(), true
        )

        myFixture.checkResult(
            "test_b.py", """
            import pytest

            @pytest.fixture
            def renamed(renamed):
                return renamed

            def test_(renamed):
                assert renamed
        """.trimIndent(), true
        )
    }
}
