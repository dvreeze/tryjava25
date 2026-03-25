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

package eu.cdevreeze.tryjava25.classfiles.parse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import eu.cdevreeze.tryjava25.classfiles.data.InvokeInstructionAndContainingMethod;
import eu.cdevreeze.tryjava25.classfiles.data.MethodAndContainingClass;

import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Like a {@link ClassUniverse}, but enhanced with data about classes containing methods calling into
 * the classes making up the {@link ClassUniverse}. The latter may be incomplete in that not necessarily
 * all classes in the underlying {@link ClassUniverse} occur in the "class usage map".
 * <p>
 * This is not a Java {@link Record} with value equality, due to lazily loaded {@link ClassModel} data in
 * the "class universe".
 *
 * @author Chris de Vreeze
 */
public final class EnhancedClassUniverse {

    private final ClassUniverse classUniverse;
    private final ImmutableMap<ClassDesc, ImmutableList<ClassDesc>> classUsageMap;

    public EnhancedClassUniverse(ClassUniverse classUniverse, ImmutableMap<ClassDesc, ImmutableList<ClassDesc>> classUsageMap) {
        this.classUniverse = classUniverse;
        this.classUsageMap = classUsageMap;
    }

    public ClassUniverse getClassUniverse() {
        return classUniverse;
    }

    public ImmutableMap<ClassDesc, ImmutableList<ClassDesc>> getClassUsageMap() {
        return classUsageMap;
    }

    public static EnhancedClassUniverse create(ClassUniverse classUniverse, String packageNameStartString) {
        return create(classUniverse, List.of(packageNameStartString));
    }

    public static EnhancedClassUniverse create(ClassUniverse classUniverse, List<String> packageNameStartStrings) {
        return create(classUniverse, c -> packageNameStartStrings.stream().anyMatch(s -> c.packageName().startsWith(s)));
    }

    public static EnhancedClassUniverse create(ClassUniverse classUniverse, Predicate<ClassDesc> mustBeInClassUsageMap) {
        Map<ClassDesc, List<ClassDesc>> classUsageMapBuilder = new HashMap<>();

        List<ClassModel> classesThatMustBeInClassUsageMap = classUniverse.getUniverse().values().stream()
                .filter(c -> mustBeInClassUsageMap.test(c.thisClass().asSymbol()))
                .toList();

        for (ClassModel classModel : classesThatMustBeInClassUsageMap) {
            classModel.methods().stream()
                    .filter(m -> m.code().isPresent())
                    .map(MethodAndContainingClass::of)
                    .flatMap(m ->
                            m.getMethodModel().code().orElseThrow().elementStream().flatMap(codeElem ->
                                    codeElem instanceof InvokeInstruction invokeInstruction ?
                                            Stream.of(new InvokeInstructionAndContainingMethod(invokeInstruction, m)) :
                                            Stream.empty()
                            )
                    )
                    .forEach(ivk -> {
                        ClassDesc classOwningMethod = ivk.getInvokeInstruction().owner().asSymbol();

                        if (!classUsageMapBuilder.containsKey(classOwningMethod)) {
                            classUsageMapBuilder.put(classOwningMethod, new ArrayList<>());
                        }
                        classUsageMapBuilder.get(classOwningMethod).add(ivk.getMethodAndContainingClass().getClassDesc());
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
