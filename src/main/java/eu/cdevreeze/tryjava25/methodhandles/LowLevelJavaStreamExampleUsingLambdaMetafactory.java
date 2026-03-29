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
 * Low level Java {@link Stream} example, creating lambdas "the hard way".
 * <p>
 * See <a href="https://www.baeldung.com/java-method-handles">Java Method Handles</a>.
 * <p>
 * Here we use a LambdaMetafactory to wrap a method as a lambda.
 *
 * @author Chris de Vreeze
 */
public class LowLevelJavaStreamExampleUsingLambdaMetafactory {

    // A MethodHandle is immutable, but a CallSite can change its targeted MethodHandle.
    // A CallSite can never change the target's MethodType, however. Type-safety is retained, yet only at runtime.

    private static final CallSite isEvenCallSite;

    private static final CallSite incrementCallSite;

    static {
        MethodHandle isEvenMethodHandle = isEvenMethodHandle(MethodHandles.lookup());
        try {
            // Also see https://medium.com/@danilarassokhin/speed-up-your-java-reflection-with-lambdametafactory-and-aide-f52e6642eabe
            isEvenCallSite = LambdaMetafactory.metafactory(
                    MethodHandles.lookup(),
                    "test",
                    MethodType.methodType(IntPredicate.class),
                    MethodType.methodType(boolean.class, int.class),
                    isEvenMethodHandle,
                    isEvenMethodHandle.type()
            );
        } catch (LambdaConversionException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        MethodHandle incrementMethodHandle = incrementMethodHandle(MethodHandles.lookup());
        try {
            // Also see https://medium.com/@danilarassokhin/speed-up-your-java-reflection-with-lambdametafactory-and-aide-f52e6642eabe
            incrementCallSite = LambdaMetafactory.metafactory(
                    MethodHandles.lookup(),
                    "applyAsInt",
                    MethodType.methodType(IntUnaryOperator.class),
                    MethodType.methodType(int.class, int.class),
                    incrementMethodHandle,
                    incrementMethodHandle.type()
            );
        } catch (LambdaConversionException e) {
            throw new RuntimeException(e);
        }
    }

    static void main(String... args) {
        // "Faking" dynamically created lambda, based on a MethodHandle
        final IntPredicate isOddPredicate;
        try {
            isOddPredicate = (IntPredicate) isEvenCallSite.dynamicInvoker().invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        // "Faking" dynamically created lambda, based on a MethodHandle
        IntUnaryOperator incrementUnaryOperator;
        try {
            incrementUnaryOperator = (IntUnaryOperator) incrementCallSite.dynamicInvoker().invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

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
            return privateLookup.findStatic(LowLevelJavaStreamExampleUsingLambdaMetafactory.class, "isEven", methodType);
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
            return privateLookup.findStatic(LowLevelJavaStreamExampleUsingLambdaMetafactory.class, "increment", methodType);
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
