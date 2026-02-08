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
 * Factorial property test.
 * <p>
 * See <a href="https://blog.johanneslink.net/2018/03/26/from-examples-to-properties/">from examples to properties</a>
 * and <a href="https://blog.johanneslink.net/2018/03/29/jqwik-on-junit5/">jqwik on JUnit 5</a>.
 *
 * @author Chris de Vreeze
 */
class FactorialTest {

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingIterationIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return factorialImplementationIsCorrect(Factorial.UsingIteration::factorial, n);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingRecursionIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return factorialImplementationIsCorrect(Factorial.UsingRecursion::factorial, n);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingCollectorIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return factorialImplementationIsCorrect(Factorial.UsingCollector::factorial, n);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingFoldingGathererIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return factorialImplementationIsCorrect(Factorial.UsingFoldingGatherer::factorial, n);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingLowLevelGathererIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return factorialImplementationIsCorrect(Factorial.UsingLowLevelGatherer::factorial, n);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingParallelizableGathererIsCorrect(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return factorialImplementationIsCorrect(Factorial.UsingParallelizableGatherer::factorial, n);
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingRecursionIsEquivalentToIteratingFactorial(@ForAll @IntRange(min = 0, max = 1500) int n) {
        // Small range to avoid stack overflow
        return Factorial.UsingRecursion.factorial(n).equals(Factorial.UsingIteration.factorial(n));
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingCollectorIsEquivalentToIteratingFactorial(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return Factorial.UsingCollector.factorial(n).equals(Factorial.UsingIteration.factorial(n));
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingFoldingGathererIsEquivalentToIteratingFactorial(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return Factorial.UsingFoldingGatherer.factorial(n).equals(Factorial.UsingIteration.factorial(n));
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingLowLevelGathererIsEquivalentToIteratingFactorial(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return Factorial.UsingLowLevelGatherer.factorial(n).equals(Factorial.UsingIteration.factorial(n));
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean factorialUsingParallelizableGathererIsEquivalentToIteratingFactorial(@ForAll @IntRange(min = 0, max = 1500) int n) {
        return Factorial.UsingParallelizableGatherer.factorial(n).equals(Factorial.UsingIteration.factorial(n));
    }

    private boolean factorialImplementationIsCorrect(Function<Integer, BigInteger> factorialFunction, int n) {
        if (n == 0) {
            return factorialFunction.apply(n).equals(BigInteger.ONE);
        } else {
            return factorialFunction.apply(n).equals(
                    BigInteger.valueOf(n).multiply(factorialFunction.apply(n - 1))
            );
        }
    }
}
