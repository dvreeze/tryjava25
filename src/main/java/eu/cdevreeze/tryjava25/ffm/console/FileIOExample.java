/*
 * Copyright 2025-2026 Chris de Vreeze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cdevreeze.tryjava25.ffm.console;

import module java.base;
import com.google.common.base.Preconditions;

import static eu.cdevreeze.tryjava25.generated.stdio_string.*;

/**
 * Simple program performing native file I/O, using the {@link java.lang.foreign} API and output of the "jextract" tool.
 * It counts the number of lines, using native calls. Of course, this is just a toy application using FFM.
 * <p>
 * See for example <a href="https://www.baeldung.com/java-foreign-memory-access">Java Foreign Memory Access</a>
 * and <a href="https://dev.java/learn/jvm/tools/complementary/jextract/">jextract</a>.
 * <p>
 * To run this program, enable native access with "--enable-native-access=ALL-UNNAMED".
 *
 * @author Chris de Vreeze
 */
public class FileIOExample {

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        Path path = Path.of(args[0]);

        try (Arena arena = Arena.ofConfined()) {
            try {
                int lineCount = getLineCount(path, arena);
                System.out.printf("Line count: %d%n", lineCount);
            } catch (RuntimeException e) {
                System.err.printf("Could not compute line count. Exception message: %s%n", e.getMessage());
            }
        }
    }

    /**
     * Returns the line count in the given file, or throws an exception if the line count could not be obtained.
     * Note how the Arena dynamically allocates memory in a safe way. It is still native "C memory",
     * yet safely managed through the FFM API.
     * <p>
     * Note how we are both in the "C world" and the "Java world". The "C world" is visible in the C function
     * contracts and types, and the use of native memory and therefore of FFM MemorySegment objects everywhere,
     * even for "native null pointers". The "Java world" is visible in the use of Java as a language, and
     * the use of Java language features to safely manage native memory.
     * <p>
     * To avoid making mistakes against the proper use of native memory in the native function calls,
     * we need to have a good look at the native function contracts in the C header files that declare those functions.
     * <p>
     * Maybe it is a bit strange to "write C in Java", but as a small experiment with Java/C/FFM it is educational.
     */
    public static int getLineCount(Path path, Arena arena) {
        MemorySegment file = null;
        try {
            // Returns a pointer to FILE
            // See https://en.cppreference.com/c/io/fopen
            file = fopen(arena.allocateFrom(path.toString()), arena.allocateFrom("r"));

            // Note the use of "native memory NPE" checks, through a comparison of the MemorySegment against MemorySegment.NULL
            // If we forget to do such checks where needed we invite core dumps!
            Preconditions.checkState(file != null && !file.equals(MemorySegment.NULL));

            int numberOfLines = 0;
            int bufferLength = 10_000;
            // Pointer to (1 byte) char
            MemorySegment line = arena.allocate(bufferLength);

            // The fgets call returns a pointer to (1 byte) char
            // See https://en.cppreference.com/c/io/fgets
            while (!fgets(line, bufferLength, file).equals(MemorySegment.NULL)) {
                int newline = '\n'; // think: (int) '\n'
                // See https://en.cppreference.com/c/string/byte/strchr
                if (!strchr(line, newline).equals(MemorySegment.NULL)) {
                    numberOfLines++;
                }
            }

            return numberOfLines;
        } finally {
            // Again the "native memory NPE check", or else we get a core dump if the actual file does not exist
            if (file != null && !file.equals(MemorySegment.NULL)) {
                // See https://en.cppreference.com/c/io/fclose
                fclose(file);
            }
        }
    }
}
