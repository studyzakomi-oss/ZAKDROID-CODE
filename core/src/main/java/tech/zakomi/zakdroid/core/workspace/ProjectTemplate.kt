package tech.zakomi.zakdroid.core.workspace

/** Starter projects. C++ CMake and Python venv templates arrive with CMake support in Phase 3. */
enum class ProjectTemplate(val mainFile: String, val files: Map<String, String>) {
    CHello(
        mainFile = "main.c",
        files = mapOf(
            "main.c" to """
                |#include <stdio.h>
                |
                |int main(void) {
                |    printf("Hello from zakdroid-code!\n");
                |    return 0;
                |}
                |""".trimMargin(),
        ),
    ),
    CppHello(
        mainFile = "main.cpp",
        files = mapOf(
            "main.cpp" to """
                |#include <iostream>
                |#include <string>
                |#include <vector>
                |
                |int main() {
                |    std::vector<std::string> words{"Hello", "from", "zakdroid-code!"};
                |    for (const auto& word : words) {
                |        std::cout << word << ' ';
                |    }
                |    std::cout << '\n';
                |    return 0;
                |}
                |""".trimMargin(),
        ),
    ),
    PythonScript(
        mainFile = "main.py",
        files = mapOf(
            "main.py" to """
                |def main() -> None:
                |    name = input("Your name: ")
                |    print(f"Hello, {name}! Welcome to zakdroid-code.")
                |
                |
                |if __name__ == "__main__":
                |    main()
                |""".trimMargin(),
        ),
    ),
}
