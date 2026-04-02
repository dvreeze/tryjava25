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

package eu.cdevreeze.tryjava25.archunitplus.data;

import module java.base;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import eu.cdevreeze.tryjava25.archunitplus.desc.DescriptorModel;

/**
 * A method as {@link MethodModel} and its containing class as {@link ClassDesc}.
 * <p>
 * This is not a {@link Record} with properly defined value equality, because of the lazily evaluated {@link MethodModel}.
 *
 * @author Chris de Vreeze
 */
public final class MethodAndContainingClass {

    private final MethodModel methodModel;

    public MethodAndContainingClass(MethodModel methodModel) {
        Preconditions.checkArgument(methodModel.parent().isPresent());

        this.methodModel = methodModel;
    }

    public MethodModel getMethodModel() {
        return methodModel;
    }

    public ClassDesc getClassDesc() {
        return methodModel.parent().orElseThrow().thisClass().asSymbol();
    }

    public ClassModel getParent() {
        return methodModel.parent().orElseThrow();
    }

    public DescriptorModel.Method toDescriptorModel() {
        return new DescriptorModel.Method(
                methodModel.methodName().stringValue(),
                methodModel.methodTypeSymbol(),
                getClassDesc(),
                methodModel.flags().flags().stream().collect(ImmutableSet.toImmutableSet())
        );
    }

    public static MethodAndContainingClass of(MethodModel methodModel) {
        return new MethodAndContainingClass(methodModel);
    }
}
