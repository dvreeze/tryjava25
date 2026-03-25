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
 * Representations of some {@link java.lang.classfile.ClassFileElement} data as immutable thread-safe
 * Java records with well-defined value equality. This data mostly depends on {@link java.lang.constant.ConstantDesc}
 * data, thus leaving out a lot of information that is in the class file.
 *
 * @author Chris de Vreeze
 */
@NullMarked
package eu.cdevreeze.tryjava25.classfiles.desc;

import org.jspecify.annotations.NullMarked;
