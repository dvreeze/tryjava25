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

/**
 * Stream operations, such as "flatMap", "map" and "filter". These are far from production-ready implementations.
 * The idea is simply to show how these common Stream operations can be understood in terms of Gatherers.
 *
 * @author Chris de Vreeze
 */
public class StreamOperations {

    private StreamOperations() {
    }

    public static <T, R> Stream<R> map(Stream<T> stream, Function<? super T, ? extends R> mapper) {
        Gatherer<T, ?, R> gatherer = Gatherer.ofSequential(
                (ignoredState, element, downStream) ->
                        downStream.push(mapper.apply(element))
        );
        return stream.gather(gatherer);
    }

    public static <T> Stream<T> filter(Stream<T> stream, Predicate<? super T> predicate) {
        Gatherer<T, ?, T> gatherer = Gatherer.ofSequential(
                (ignoredState, element, downStream) -> {
                    if (predicate.test(element)) {
                        return downStream.push(element);
                    } else {
                        return true;
                    }
                }
        );
        return stream.gather(gatherer);
    }

    // Many subtle bugs in the flatMap implementation below.
    // See https://dev.java/learn/api/streams/gatherers/ for more on this, and on Gatherers in general.

    public static <T, R> Stream<R> flatMap(Stream<T> stream, Function<? super T, ? extends Stream<? extends R>> mapper) {
        Gatherer<T, ?, R> gatherer = Gatherer.ofSequential(
                (ignoredState, element, downStream) -> {
                    List<Boolean> canContinueFlags = mapper.apply(element).map(downStream::push).toList();
                    return canContinueFlags.stream().allMatch(b -> b);
                }
        );
        return stream.gather(gatherer);
    }
}
