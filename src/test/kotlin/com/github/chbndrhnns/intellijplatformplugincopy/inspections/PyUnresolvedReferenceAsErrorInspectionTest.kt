package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import fixtures.TestBase

class PyUnresolvedReferenceAsErrorInspectionTest : TestBase() {

    fun testUnresolvedVariable() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                x = 1
                return <error descr="Unresolved reference 'y'">y</error>
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedVariable() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                x = 1
                return x
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedFunction() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                return <error descr="Unresolved reference 'bar'">bar</error>()
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedFunction() {
        myFixture.configureByText(
            "test.py",
            """
            def bar():
                pass
            
            def foo():
                return bar()
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedImport() {
        myFixture.configureByText(
            "test.py",
            """
            from <error descr="Unresolved reference 'module'">module</error> import <error descr="Unresolved reference 'something'">something</error>
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedClass() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                return <error descr="Unresolved reference 'MyClass'">MyClass</error>()
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedClass() {
        myFixture.configureByText(
            "test.py",
            """
            class MyClass:
                pass
            
            def foo():
                return MyClass()
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testBuiltinFunction() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                return len([1, 2, 3])
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testMultipleUnresolvedReferences() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                a = <error descr="Unresolved reference 'x'">x</error>
                b = <error descr="Unresolved reference 'y'">y</error>
                return a + b
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedInGlobalScope() {
        myFixture.configureByText(
            "test.py",
            """
            result = <error descr="Unresolved reference 'undefined_var'">undefined_var</error> + 1
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedInClassMethod() {
        myFixture.configureByText(
            "test.py",
            """
            class MyClass:
                def method(self):
                    return <error descr="Unresolved reference 'undefined'">undefined</error>
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedSelfAttribute() {
        myFixture.configureByText(
            "test.py",
            """
            class MyClass:
                def __init__(self):
                    self.value = 1
                
                def method(self):
                    return self.value
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedAttributeOnDataclass() {
        myFixture.configureByText(
            "test.py",
            """
            from dataclasses import dataclass
            
            @dataclass
            class Person:
                name: str
                age: int
            
            def foo():
                p = Person(name="John", age=30)
                return p.<error descr="Unresolved attribute 'invalid_field'">invalid_field</error>
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedAttributeOnDataclass() {
        myFixture.configureByText(
            "test.py",
            """
            from dataclasses import dataclass
            
            @dataclass
            class Person:
                name: str
                age: int
            
            def foo():
                p = Person(name="John", age=30)
                return p.name
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }


    fun testUnresolvedAttributeOnRegularClass() {
        myFixture.configureByText(
            "test.py",
            """
            class Person:
                def __init__(self, name: str, age: int):
                    self.name = name
                    self.age = age
            
            def foo():
                p = Person(name="John", age=30)
                # Regular classes are not in the allowlist, so no error
                return p.invalid_field
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedAttributeOnDataclassQualifiedImport() {
        myFixture.configureByText(
            "test.py",
            """
            import dataclasses
            
            @dataclasses.dataclass
            class Person:
                name: str
                age: int
            
            def foo():
                p = Person(name="John", age=30)
                return p.<error descr="Unresolved attribute 'invalid_field'">invalid_field</error>
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnresolvedAttributeOnDataclassWithModuleAlias() {
        myFixture.configureByText(
            "test.py",
            """
            import dataclasses as dcs
            
            @dcs.dataclass
            class Person:
                name: str
                age: int
            
            def foo():
                p = Person(name="John", age=30)
                return p.<error descr="Unresolved attribute 'invalid_field'">invalid_field</error>
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testResolvedAttributeOnDataclassWithDecoratorAlias() {
        myFixture.configureByText(
            "test.py",
            """
            from dataclasses import dataclass as dc
            
            @dc
            class Person:
                name: str
                age: int
            
            def foo():
                p = Person(name="John", age=30)
                return p.name
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testDunderNameNotFlagged() {
        myFixture.configureByText(
            "test.py",
            """
            def foo():
                if __name__ == "__main__":
                    print("Running as main")
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testDunderFileNotFlagged() {
        myFixture.configureByText(
            "test.py",
            """
            def get_file_path():
                return __file__
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testDunderAllNotFlagged() {
        myFixture.configureByText(
            "test.py",
            """
            def get_exports():
                return __all__
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testDunderDocNotFlagged() {
        myFixture.configureByText(
            "test.py",
            """
            def get_doc():
                return __doc__
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testDunderPackageNotFlagged() {
        myFixture.configureByText(
            "test.py",
            """
            def get_package():
                return __package__
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testMultipleDunderSymbols() {
        myFixture.configureByText(
            "test.py",
            """
            def module_info():
                name = __name__
                file = __file__
                doc = __doc__
                pkg = __package__
                return name, file, doc, pkg
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }

    fun testDunderSymbolsWithOtherUnresolvedReferences() {
        myFixture.configureByText(
            "test.py",
            """
            def mixed():
                name = __name__  # Should not be flagged
                invalid = <error descr="Unresolved reference 'undefined_var'">undefined_var</error>  # Should be flagged
                file = __file__  # Should not be flagged
                return name, invalid, file
            """.trimIndent()
        )
        myFixture.enableInspections(PyUnresolvedReferenceAsErrorInspection::class.java)
        myFixture.checkHighlighting(true, false, false)
    }
}
