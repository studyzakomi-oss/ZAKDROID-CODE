package tech.zakomi.zakdroid.editor.outline

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.zakomi.zakdroid.editor.EditorLanguage

class SymbolOutlineTest {

    private fun names(language: EditorLanguage, text: String) =
        SymbolOutline.of(language, text.trimIndent()).map { Triple(it.name, it.kind, it.line) }

    @Test
    fun python() {
        val symbols = SymbolOutline.of(
            EditorLanguage.Python,
            """
            import os
            class Shape:
                def area(self):
                    return 0
                async def load(self): pass
            def main():
                def helper(): pass
                print("def not_a_function()")
            """.trimIndent(),
        )
        assertEquals(
            listOf("Shape" to 0, "area" to 1, "load" to 1, "main" to 0, "helper" to 1),
            symbols.map { it.name to it.depth },
        )
        assertEquals(SymbolKind.Class, symbols[0].kind)
        assertEquals(5, symbols[3].line)
    }

    @Test
    fun cFunctionsTypesAndMacros() {
        val found = names(
            EditorLanguage.C,
            """
            #include <stdio.h>
            #define MAX_SIZE 10
            struct point {
                int x;
            };
            int add(int a, int b);            /* prototype: not listed */
            static unsigned long *make_buffer(size_t n)
            {
                return malloc(n);
            }
            // int commented_out(void) {
            int main(void) {
                if (add(1, 2) > 0) {
                    printf("void fake(void) {");
                }
                return add(1,
                           2);
            }
            enum color { RED, GREEN };
            """,
        )
        assertEquals(
            listOf(
                Triple("MAX_SIZE", SymbolKind.Macro, 1),
                Triple("point", SymbolKind.Struct, 2),
                Triple("make_buffer", SymbolKind.Function, 6),
                Triple("main", SymbolKind.Function, 11),
                Triple("color", SymbolKind.Enum, 18),
            ),
            found,
        )
    }

    @Test
    fun cppClassesMethodsAndNamespaces() {
        val found = names(
            EditorLanguage.Cpp,
            """
            namespace geo {
            class Circle : public Shape {
            public:
                explicit Circle(double r);
                double area() const override {
                    return 3.14 * r_ * r_;
                }
            };
            enum class Mode { A, B };
            }
            geo::Circle::Circle(double r) : r_(r) {}
            double geo::Circle::perimeter() const
            {
                return 2 * 3.14 * r_;
            }
            template <typename T>
            std::vector<T> make(int n,
                                T value) {
                return std::vector<T>(n, value);
            }
            """,
        ).map { it.first to it.second }
        assertEquals(
            listOf(
                "geo" to SymbolKind.Namespace,
                "Circle" to SymbolKind.Class,
                "area" to SymbolKind.Function,
                "Mode" to SymbolKind.Enum,
                "geo::Circle::Circle" to SymbolKind.Function,
                "geo::Circle::perimeter" to SymbolKind.Function,
                "make" to SymbolKind.Function,
            ),
            found,
        )
    }

    @Test
    fun plainTextHasNoSymbols() {
        assertEquals(emptyList<DocumentSymbol>(), SymbolOutline.of(EditorLanguage.PlainText, "def x(): pass"))
    }
}
