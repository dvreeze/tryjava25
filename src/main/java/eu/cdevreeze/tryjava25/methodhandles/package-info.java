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

/**
 * Playing around with {@link java.lang.invoke.MethodHandle} etc. This is by far not a new Java 25 API.
 * Getting a feel for this API helps in understanding the "invoke-dynamic" JVM instruction. Also, this API
 * can be used as an alternative to Java reflection in many cases.
 * <p>
 * For more background, see <a href="https://www.baeldung.com/java-method-handles">Java Method Handles</a>.
 *
 * @author Chris de Vreeze
 */
@NullMarked
package eu.cdevreeze.tryjava25.methodhandles;

import org.jspecify.annotations.NullMarked;