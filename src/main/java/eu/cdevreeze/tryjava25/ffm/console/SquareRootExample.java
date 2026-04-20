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

/**
 * Simple program computing the square root of a number in C, using the {@link java.lang.foreign} API.
 * It is not a very useful program, but does show the FFM API in practice, be it in a trivial example.
 * <p>
 * To run this program, enable native access with "--enable-native-access=ALL-UNNAMED".
 *
 * @author Chris de Vreeze
 */
public class SquareRootExample {

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        double n = Double.parseDouble(args[0]);

        double squareRoot = getSquareRoot(n);

        System.out.printf("Square root of %f: %f%n", n, squareRoot);

        double squareRoot2 = Math.sqrt(n);
        System.out.printf("Square root of %f, without calling any native function: %f%n", n, squareRoot2);
        System.out.flush();
    }

    public static double getSquareRoot(double n) {
        Linker nativeLinker = Linker.nativeLinker();

        SymbolLookup symbolLookup = nativeLinker.defaultLookup();
        // See https://en.cppreference.com/c/numeric/math/sqrt
        MemorySegment sqrtSymbol = symbolLookup.find("sqrt").orElseThrow();

        FunctionDescriptor sqrtDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

        // Finally, we have the native function to call as a MethodHandle.
        MethodHandle sqrtMethod = nativeLinker.downcallHandle(sqrtSymbol, sqrtDescriptor);

        // No need to create a MemorySegment in this case.
        return getSquareRoot(sqrtMethod, n);
    }

    public static double getSquareRoot(MethodHandle squareRootMethod, double n) {
        try {
            return (double) squareRootMethod.invokeExact(n);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
