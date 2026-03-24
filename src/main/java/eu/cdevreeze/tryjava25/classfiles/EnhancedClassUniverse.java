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

package eu.cdevreeze.tryjava25.classfiles;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Like a {@link ClassUniverse}, but enhanced with data about classes containing methods calling into
 * the classes making up the {@link ClassUniverse}.
 *
 * @author Chris de Vreeze
 */
public record EnhancedClassUniverse(
        ClassUniverse classUniverse,
        ImmutableMap<ClassDesc, ImmutableList<ClassDesc>> classUsageMap
) {

    public static EnhancedClassUniverse create(ClassUniverse classUniverse) {
        Map<ClassDesc, List<ClassDesc>> classUsageMapBuilder = new HashMap<>();

        for (ClassModel classModel : classUniverse.getUniverse().values()) {
            classModel.methods().stream()
                    .filter(m -> m.code().isPresent())
                    .map(MethodAndContainingClass::of)
                    .flatMap(m ->
                            m.methodModel().code().orElseThrow().elementStream().flatMap(codeElem ->
                                    codeElem instanceof InvokeInstruction invokeInstruction ?
                                            Stream.of(new InvokeInstructionAndContainingMethod(invokeInstruction, m)) :
                                            Stream.empty()
                            )
                    )
                    .forEach(ivk -> {
                        ClassDesc classOwningMethod = ivk.invokeInstruction().owner().asSymbol();

                        if (!classUsageMapBuilder.containsKey(classOwningMethod)) {
                            classUsageMapBuilder.put(classOwningMethod, new ArrayList<>());
                        }
                        classUsageMapBuilder.get(classOwningMethod).add(ivk.methodAndContainingClass().classDesc());
                    });
        }

        ImmutableMap<ClassDesc, ImmutableList<ClassDesc>> classUsageMap = classUsageMapBuilder
                .entrySet()
                .stream()
                .collect(
                        ImmutableMap.toImmutableMap(
                                Map.Entry::getKey,
                                kv -> kv.getValue().stream().distinct().collect(ImmutableList.toImmutableList())
                        )
                );

        return new EnhancedClassUniverse(classUniverse, classUsageMap);
    }
}
