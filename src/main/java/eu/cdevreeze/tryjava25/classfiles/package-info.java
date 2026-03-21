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
 * Playing around with the {@link java.lang.classfile} API, for static analysis. Note that unlike the reflection
 * API this (beautifully designed) API is powerful enough for finding the (potential) use of classes, methods and fields
 * throughout a code base.
 * <p>
 * One way to approach this is to parse all "relevant" class files into this API, and per {@link java.lang.classfile.ClassModel}
 * collect all bytecode instructions accessing methods and fields of other classes. After having built this "index"
 * we can reason back from method/field declarations to their use sites. Note that due to instructions like
 * "invoke-interface" and "invoke-virtual" we can not always be sure whether a method is called, but at least we can
 * be sure which concrete methods can potentially be called at runtime.
 * <p>
 * Note that with this approach the analysis program does not need the code to inspect to occur on its class path.
 * And, again, the legacy Java reflection API lacks the power of the "classfile" API in finding all (potential)
 * callers of specific methods.
 *
 * @author Chris de Vreeze
 */
@NullMarked
package eu.cdevreeze.tryjava25.classfiles;

import org.jspecify.annotations.NullMarked;
