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

package eu.cdevreeze.tryjava25.methodhandles;

import module java.base;
import com.google.common.base.Preconditions;

/**
 * Low level Java {@link Stream} example, creating "lambdas" "the hard way".
 * <p>
 * See <a href="https://www.baeldung.com/java-method-handles">Java Method Handles</a>.
 * <p>
 * In this case we do not generate any lambdas, but work with a plain MethodHandle targeting a regular method.
 *
 * @author Chris de Vreeze
 */
public class LowLevelJavaStreamExample {

    private static final MethodHandle isEvenMethodHandle;

    private static final MethodHandle incrementMethodHandle;

    static {
        isEvenMethodHandle = isEvenMethodHandle(MethodHandles.lookup());
    }

    static {
        incrementMethodHandle = incrementMethodHandle(MethodHandles.lookup());
    }

    static void main(String... args) {
        // "Faking" dynamically created lambda, based on a MethodHandle
        IntPredicate isOddPredicate = n -> {
            try {
                // Quite inefficient, because the MethodHandle invocation does not return a reusable IntPredicate
                return (boolean) isEvenMethodHandle.invokeExact(n);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        // "Faking" dynamically created lambda, based on a MethodHandle
        IntUnaryOperator incrementUnaryOperator = n -> {
            try {
                // Quite inefficient, because the MethodHandle invocation does not return a reusable IntUnaryOperator
                return (int) incrementMethodHandle.invokeExact(n);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        List<Integer> oddNumbers = IntStream.range(0, 20)
                .filter(isOddPredicate)
                .map(incrementUnaryOperator)
                .boxed()
                .toList();

        Preconditions.checkState(oddNumbers.equals(List.of(1, 3, 5, 7, 9, 11, 13, 15, 17, 19)));
    }

    private static MethodHandle isEvenMethodHandle(MethodHandles.Lookup privateLookup) {
        // First create the MethodType. Note we retain type-safety, but this time only at runtime and not earlier.
        MethodType methodType = MethodType.methodType(boolean.class, int.class);
        try {
            // Now create the MethodHandle, while first setting its fixed MethodType.
            // Note that the MethodHandle is immutable. Of course this applies to its MethodType too.
            // Also note that using a MethodHandle we indeed prevent having to create and instantiate a class.
            return privateLookup.findStatic(LowLevelJavaStreamExample.class, "isEven", methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle incrementMethodHandle(MethodHandles.Lookup privateLookup) {
        // First create the MethodType. Note we retain type-safety, but this time only at runtime and not earlier.
        MethodType methodType = MethodType.methodType(int.class, int.class);
        try {
            // Now create the MethodHandle, while first setting its fixed MethodType.
            // Note that the MethodHandle is immutable. Of course this applies to its MethodType too.
            // Also note that using a MethodHandle we indeed prevent having to create and instantiate a class.
            return privateLookup.findStatic(LowLevelJavaStreamExample.class, "increment", methodType);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isEven(int n) {
        return n % 2 == 0;
    }

    private static int increment(int n) {
        return n + 1;
    }
}
