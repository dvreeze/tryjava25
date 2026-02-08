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

package eu.cdevreeze.tryjava25.gatherers;

import module java.base;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Report;
import net.jqwik.api.Reporting;
import net.jqwik.api.constraints.IntRange;

/**
 * Fibonacci sequence property test.
 * <p>
 * See <a href="https://blog.johanneslink.net/2018/03/26/from-examples-to-properties/">from examples to properties</a>
 * and <a href="https://blog.johanneslink.net/2018/03/29/jqwik-on-junit5/">jqwik on JUnit 5</a>.
 *
 * @author Chris de Vreeze
 */
class FibonacciTest {

    @Property
    @Report(Reporting.FALSIFIED)
    boolean fibonacciUsingStreamIterateIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int size) {
        return fibonacciImplementationIsCorrect(Fibonacci.UsingStreamIterate::fibonacci, size);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean fibonacciUsingScanningGathererIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int size) {
        return fibonacciImplementationIsCorrect(Fibonacci.UsingScanningGatherer::fibonacci, size);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean fibonacciUsingLowLevelGathererIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int size) {
        return fibonacciImplementationIsCorrect(Fibonacci.UsingLowLevelGatherer::fibonacci, size);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean fibonacciUsingScanningGathererIsEquivalentToStreamIterateFibonacci(@ForAll @IntRange(min = 0, max = 1500) int size) {
        return Fibonacci.UsingScanningGatherer.fibonacci(size).equals(Fibonacci.UsingStreamIterate.fibonacci(size));
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean fibonacciUsingLowLevelGathererIsEquivalentToStreamIterateFibonacci(@ForAll @IntRange(min = 0, max = 1500) int size) {
        return Fibonacci.UsingLowLevelGatherer.fibonacci(size).equals(Fibonacci.UsingStreamIterate.fibonacci(size));
    }

    private boolean fibonacciImplementationIsCorrect(Function<Integer, List<BigInteger>> fibonacciFunction, int size) {
        // Note the guarded pattern below, and the fact that it cannot yet be used with primitives
        return switch (Integer.valueOf(size)) {
            case Integer sz when sz <= 0 -> fibonacciFunction.apply(size).equals(List.of());
            case 1 -> fibonacciFunction.apply(size).equals(List.of(BigInteger.ZERO));
            case 2 -> fibonacciFunction.apply(size).equals(List.of(BigInteger.ZERO, BigInteger.ONE));
            default -> {
                List<BigInteger> fib = fibonacciFunction.apply(size);
                yield IntStream.range(2, size).allMatch(i -> fib.get(i).equals(fib.get(i - 1).add(fib.get(i - 2))));
            }
        };
    }
}
