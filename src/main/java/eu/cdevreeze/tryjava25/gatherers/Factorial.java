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
import com.google.common.base.Preconditions;

/**
 * Factorial implementations, with and without the use of Gatherers.
 *
 * @author Chris de Vreeze
 */
public class Factorial {

    private Factorial() {
    }

    public static class UsingIteration {

        private UsingIteration() {
        }

        public static BigInteger factorial(int n) {
            Preconditions.checkArgument(n >= 0);

            if (n == 0) {
                return BigInteger.ONE;
            }

            BigInteger acc = BigInteger.ONE;
            for (int i = 1; i <= n; i++) {
                acc = acc.multiply(BigInteger.valueOf(i));
            }
            return acc;
        }
    }

    public static class UsingRecursion {

        private UsingRecursion() {
        }

        // Invites stack overflow

        public static BigInteger factorial(int n) {
            Preconditions.checkArgument(n >= 0);
            return factorial(BigInteger.valueOf(n));
        }

        private static BigInteger factorial(BigInteger n) {
            // Recursion
            return (n.equals(BigInteger.ZERO) || n.equals(BigInteger.ONE)) ? BigInteger.ONE : n.multiply(factorial(n.subtract(BigInteger.ONE)));
        }
    }

    public static class UsingCollector {

        private UsingCollector() {
        }

        public static BigInteger factorial(int n) {
            Preconditions.checkArgument(n >= 0);

            return (n == 0) ?
                    BigInteger.ONE :
                    IntStream.rangeClosed(1, n)
                            .mapToObj(BigInteger::valueOf)
                            .reduce(BigInteger.ONE, BigInteger::multiply);
        }
    }

    public static class UsingFoldingGatherer {

        private UsingFoldingGatherer() {
        }

        // "Collecting" using an intermediate operation, namely a folding Gatherer

        public static BigInteger factorial(int n) {
            Preconditions.checkArgument(n >= 0);

            Gatherer<Integer, ?, BigInteger> factorialGatherer = Gatherers.fold(
                    () -> BigInteger.ONE,
                    (acc, element) -> acc.multiply(BigInteger.valueOf(element))
            );

            return (n == 0) ?
                    BigInteger.ONE :
                    IntStream.rangeClosed(1, n)
                            .boxed()
                            .gather(factorialGatherer)
                            .findFirst()
                            .orElseThrow();
        }
    }

    public static class UsingLowLevelGatherer {

        private UsingLowLevelGatherer() {
        }

        public static BigInteger factorial(int n) {
            Preconditions.checkArgument(n >= 0);

            // Using AtomicReference for its mutability only
            Gatherer<Integer, AtomicReference<BigInteger>, BigInteger> factorialGatherer = Gatherer.ofSequential(
                    () -> new AtomicReference<>(BigInteger.ONE),
                    (acc, element, downStream) -> {
                        acc.getAndUpdate(i -> i.multiply(BigInteger.valueOf(element)));
                        // Nothing is pushed to the downstream
                        return true;
                    },
                    (acc, downStream) -> downStream.push(acc.get())
            );

            return (n == 0) ?
                    BigInteger.ONE :
                    IntStream.rangeClosed(1, n)
                            .boxed()
                            .gather(factorialGatherer)
                            .findFirst()
                            .orElseThrow();
        }
    }

    public static class UsingParallelizableGatherer {

        private UsingParallelizableGatherer() {
        }

        public static BigInteger factorial(int n) {
            Preconditions.checkArgument(n >= 0);

            Gatherer<Integer, AtomicReference<BigInteger>, BigInteger> factorialGatherer = Gatherer.of(
                    () -> new AtomicReference<>(BigInteger.ONE),
                    (acc, element, downStream) -> {
                        acc.getAndUpdate(i -> i.multiply(BigInteger.valueOf(element)));
                        // Nothing is pushed to the downstream
                        return true;
                    },
                    (acc1, acc2) -> new AtomicReference<>(acc1.updateAndGet(i -> i.multiply(acc2.get()))),
                    (acc, downStream) -> downStream.push(acc.get())
            );

            return (n == 0) ?
                    BigInteger.ONE :
                    IntStream.rangeClosed(1, n)
                            .boxed()
                            .gather(factorialGatherer)
                            .findFirst()
                            .orElseThrow();
        }
    }

    static void main(String[] args) {
        Objects.checkIndex(0, args.length);
        int n = Integer.parseInt(args[0]);

        System.out.printf("Factorial of %d (using iteration): %d%n", n, UsingIteration.factorial(n));
        System.out.printf("Factorial of %d (using recursion): %d%n", n, UsingRecursion.factorial(n));
        System.out.printf("Factorial of %d (using collector): %d%n", n, UsingCollector.factorial(n));
        System.out.printf("Factorial of %d (using folding gatherer): %d%n", n, UsingFoldingGatherer.factorial(n));
        System.out.printf("Factorial of %d (using low level gatherer): %d%n", n, UsingLowLevelGatherer.factorial(n));
        System.out.printf("Factorial of %d (using parallelizable gatherer): %d%n", n, UsingParallelizableGatherer.factorial(n));
    }
}
