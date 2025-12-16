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

import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

/**
 * Fibonacci sequence implementations, with and without the use of Gatherers.
 *
 * @author Chris de Vreeze
 */
public class Fibonacci {

    private Fibonacci() {
    }

    private record Pair(BigInteger v1, BigInteger v2) {

        public BigInteger nextNumber() {
            return v1.add(v2);
        }

        public Pair nextPair() {
            return new Pair(v2(), nextNumber());
        }

        public static Pair firstPair() {
            return new Pair(BigInteger.ZERO, BigInteger.ONE);
        }

        // See https://en.wikipedia.org/wiki/Fibonacci_sequence, for negative integer indices
        public static Pair predecessorOfFirstPair() {
            return new Pair(BigInteger.ONE, BigInteger.ZERO);
        }
    }

    public static class UsingStreamIterate {

        private UsingStreamIterate() {
        }

        public static List<BigInteger> fibonacci(int size) {
            Preconditions.checkArgument(size >= 0);

            return Stream.iterate(Pair.firstPair(), Pair::nextPair)
                    .limit(size)
                    .map(Pair::v1)
                    .toList();
        }
    }

    public static class UsingScanningGatherer {

        private UsingScanningGatherer() {
        }

        public static List<BigInteger> fibonacci(int size) {
            Preconditions.checkArgument(size >= 0);

            Gatherer<Object, ?, Pair> fibonacciGatherer = Gatherers.scan(
                    Pair::predecessorOfFirstPair,
                    (acc, ignoredElement) -> acc.nextPair()
            );

            // Somewhat of a hack to need a "dummy" collection of the right size

            return Stream.generate(Object::new)
                    .limit(size)
                    .gather(fibonacciGatherer)
                    .map(Pair::v1)
                    .toList();
        }
    }

    public static class UsingLowLevelGatherer {

        private UsingLowLevelGatherer() {
        }

        public static List<BigInteger> fibonacci(int size) {
            Preconditions.checkArgument(size >= 0);

            // Using AtomicReference for its mutability only
            Gatherer<Object, AtomicReference<Pair>, BigInteger> fibonacciGatherer = Gatherer.ofSequential(
                    () -> new AtomicReference<>(Pair.predecessorOfFirstPair()),
                    (acc, ignoredElement, downStream) -> {
                        Pair curr = acc.updateAndGet(Pair::nextPair);
                        return downStream.push(curr.v1());
                    }
            );

            // Somewhat of a hack to need a "dummy" collection of the right size

            return Stream.generate(Object::new)
                    .limit(size)
                    .gather(fibonacciGatherer)
                    .toList();
        }
    }

    static void main(String[] args) {
        Objects.checkIndex(0, args.length);
        int size = Integer.parseInt(args[0]);

        System.out.printf(
                "Fibonacci sequence of size %d (using stream-iterate, limited to first 20): %s%n",
                size,
                UsingStreamIterate.fibonacci(size).stream().limit(20).toList()
        );

        System.out.printf(
                "Fibonacci sequence of size %d (using scanning gatherer, limited to first 20): %s%n",
                size,
                UsingScanningGatherer.fibonacci(size).stream().limit(20).toList()
        );

        System.out.printf(
                "Fibonacci sequence of size %d (using low level gatherer, limited to first 20): %s%n",
                size,
                UsingLowLevelGatherer.fibonacci(size).stream().limit(20).toList()
        );
    }
}
