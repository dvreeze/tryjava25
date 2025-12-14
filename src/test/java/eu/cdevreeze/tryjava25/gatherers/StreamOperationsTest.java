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

import net.jqwik.api.*;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Stream operations test, showing how common Stream operations can be understood in terms of Gatherers.
 * <p>
 * See <a href="https://blog.johanneslink.net/2018/03/26/from-examples-to-properties/">from examples to properties</a>
 * and <a href="https://blog.johanneslink.net/2018/03/29/jqwik-on-junit5/">jqwik on JUnit 5</a>.
 *
 * @author Chris de Vreeze
 */
class StreamOperationsTest {

    @Property
    @Report(Reporting.FALSIFIED)
    boolean mapFunctionIsCorrect(@ForAll List<Integer> list, @ForAll("mapParameterFunctions") Function<Integer, Long> f) {
        return StreamOperations.map(list.stream(), f).toList().equals(list.stream().map(f).toList());
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean filterFunctionIsCorrect(@ForAll List<Integer> list, @ForAll("filterParameterPredicates") Predicate<Integer> p) {
        return StreamOperations.filter(list.stream(), p).toList().equals(list.stream().filter(p).toList());
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean flatMapFunctionIsCorrect(@ForAll List<Integer> list, @ForAll("flatMapParameterFunctions") Function<Integer, Stream<Long>> f) {
        return StreamOperations.flatMap(list.stream(), f).toList().equals(list.stream().flatMap(f).toList());
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean filterFunctionIsSpecialCaseOfFlatMap(@ForAll List<Integer> list, @ForAll("filterParameterPredicates") Predicate<Integer> p) {
        Function<Integer, Stream<Integer>> f = n -> p.test(n) ? Stream.of(n) : Stream.empty();
        return StreamOperations.filter(list.stream(), p).toList().equals(list.stream().flatMap(f).toList());
    }

    @Property
    @Report(Reporting.FALSIFIED)
    boolean mapFunctionIsSpecialCaseOfFlatMap(@ForAll List<Integer> list, @ForAll("mapParameterFunctions") Function<Integer, Long> f) {
        Function<Integer, Stream<Long>> f2 = n -> Stream.of(f.apply(n));
        return StreamOperations.map(list.stream(), f).toList().equals(list.stream().flatMap(f2).toList());
    }

    @Provide
    private Arbitrary<Function<Integer, Long>> mapParameterFunctions() {
        return Arbitraries.of(
                n -> Long.valueOf(n) * 2,
                n -> Long.valueOf(n) + 1,
                n -> (long) -n
        );
    }

    @Provide
    private Arbitrary<Predicate<Integer>> filterParameterPredicates() {
        return Arbitraries.of(
                n -> n % 2 == 0,
                n -> n % 2 != 0,
                n -> n % 10 == 0
        );
    }

    @Provide
    private Arbitrary<Function<Integer, Stream<Long>>> flatMapParameterFunctions() {
        return Arbitraries.of(
                n -> LongStream.range(0L, Math.min(Math.abs(n), 5000)).boxed(),
                n -> LongStream.range(0L, Math.min(Math.abs(n), 5000)).filter(i -> i % 100 == 0).boxed(),
                n -> LongStream.of(Math.abs(n), 2L * Math.abs(n)).boxed()
        );
    }
}
