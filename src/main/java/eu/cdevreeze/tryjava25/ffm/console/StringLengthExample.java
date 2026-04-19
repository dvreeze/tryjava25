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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Simple program getting the string length of a given string, using the {@link java.lang.foreign} API.
 * The length of the string in this context is the length in bytes of the string in UTF-8 encoding.
 * <p>
 * See for example <a href="https://www.baeldung.com/java-foreign-memory-access">Java Foreign Memory Access</a>.
 * <p>
 * To run this program, enable native access with "--enable-native-access=ALL-UNNAMED".
 *
 * @author Chris de Vreeze
 */
public class StringLengthExample {

    private static final char NULL_CHAR = '\0';

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        String str = args[0];

        long stringLength = getStringLength(str);

        System.out.printf("String length (in bytes, of the UTF-8 encoding of the string): %d%n", stringLength);

        long stringLength2 = str.getBytes(StandardCharsets.UTF_8).length;
        System.out.printf("The same string (or UTF-8 byte array) length, without calling any native function: %d%n", stringLength2);
        System.out.flush();
    }

    public static long getStringLength(String str) {
        Linker linker = Linker.nativeLinker();
        // See https://en.cppreference.com/c/string/byte/strlen
        MemorySegment strLenMemorySegment = linker.defaultLookup().find("strlen").orElseThrow();

        // The string argument is a pointer to (byte) char (the first char in the array), so an ADDRESS
        FunctionDescriptor strLenDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
        MethodHandle strLenMethod = linker.downcallHandle(strLenMemorySegment, strLenDescriptor);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment strSegment = convertStringToNullTerminatedUTF8EncodedByteArray(str, arena);

            return getStringLength(strLenMethod, strSegment);
        }
    }

    private static MemorySegment convertStringToNullTerminatedUTF8EncodedByteArray(String str, Arena arena) {
        byte[] byteArray = str.getBytes(StandardCharsets.UTF_8);
        byte[] byteArrayWithNull = new byte[byteArray.length + 1];
        System.arraycopy(byteArray, 0, byteArrayWithNull, 0, byteArray.length);
        // Null-terminated string as UTF-8 encoded byte array
        byteArrayWithNull[byteArrayWithNull.length - 1] = (byte) NULL_CHAR;

        MemorySegment strSegment = arena.allocate(byteArrayWithNull.length);
        MemorySegment.copy(
                byteArrayWithNull,
                0,
                strSegment,
                ValueLayout.JAVA_BYTE,
                0,
                byteArrayWithNull.length
        );
        return strSegment;
    }

    public static long getStringLength(MethodHandle strLenMethod, MemorySegment strSegment) {
        try {
            return (long) strLenMethod.invokeExact(strSegment);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
