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
 * Playing around with the combination of ArchUnit with the {@link java.lang.classfile} API, for static analysis.
 * The idea is to have ArchUnit doing the bootstrapping, and to use ArchUnit's DSL for static code analysis,
 * while using the Class File API for more complex rules that require the power of the Class File API.
 * <p>
 * Complex ArchUnit rules could be written with the help of the {@link java.lang.classfile} API.
 * <p>
 * Note that ArchUnit and the Class File API are conceptually mutually consistent. Both APIs "reflect" on
 * compilation output (i.e. class files) and not on source code, and both APIs do not require the inspected
 * classes to be loaded into the JVM by a classloader. Indeed, it seems that combining the 2 APIs makes for
 * a perfect match!
 *
 * @author Chris de Vreeze
 */
@NullMarked
package eu.cdevreeze.tryjava25.archunitplus;

import org.jspecify.annotations.NullMarked;
