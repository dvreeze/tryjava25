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

package eu.cdevreeze.tryjava25.structuredconcurrency;

import module java.base;

/**
 * Custom {@link Gatherer} instances.
 * Shamelessly copied from
 * <a href="https://softwaremill.com/stream-gatherers-in-practice-part-1/">stream gatherers in practice, part 1</a>.
 *
 * @author Chris de Vreeze
 */
public class MyGatherers {

    private MyGatherers() {
    }

    public static <T, P> DistinctByGatherer<T, P> distinctBy(Function<T, P> extractor) {
        return new DistinctByGatherer<>(extractor);
    }

    public static final class DistinctByGatherer<T, P> implements Gatherer<T, Set<P>, T> {

        private final Function<T, P> selector;

        public DistinctByGatherer(Function<T, P> selector) {
            this.selector = selector;
        }

        @Override
        public Supplier<Set<P>> initializer() {
            return HashSet::new;
        }

        @Override
        public Integrator<Set<P>, T, T> integrator() {
            return Integrator.ofGreedy((state, item, downstream) -> {
                P extracted = selector.apply(item);

                if (!state.contains(extracted)) {
                    state.add(extracted);
                    downstream.push(item);
                }

                return true;
            });
        }
    }
}
