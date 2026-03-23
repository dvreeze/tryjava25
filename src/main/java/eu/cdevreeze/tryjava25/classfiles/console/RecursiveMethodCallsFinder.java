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

package eu.cdevreeze.tryjava25.classfiles.console;

import com.google.common.collect.ImmutableList;
import eu.cdevreeze.tryjava25.classfiles.ClassModelParser;
import eu.cdevreeze.tryjava25.classfiles.ClassUniverse;

import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.constant.MethodTypeDesc;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Like {@link MethodCallsFinder}, but recursive, in that also caller of callers are found, etc.
 * <p>
 * The program arguments are the same as for {@link MethodCallsFinder}. The same holds for the required system properties.
 * <p>
 * There are many limitations in this program. Most importantly, use of reflection will not be detected.
 *
 * @author Chris de Vreeze
 */
public class RecursiveMethodCallsFinder {

    private final ClassUniverse classUniverse;
    private final String rootPackage;
    private final int maxRecursionDepth;

    public RecursiveMethodCallsFinder(ClassUniverse classUniverse, String rootPackage) {
        this.classUniverse = classUniverse;
        this.rootPackage = rootPackage;
        this.maxRecursionDepth = Integer.parseInt(System.getProperty("maxRecursionDepth", "20"));
    }

    public Optional<MethodModel> findMethodModel(String className, String methodName, Optional<MethodTypeDesc> methodTypeDescOption) {
        MethodCallsFinder methodCallsFinder = new MethodCallsFinder(classUniverse, rootPackage);
        return methodCallsFinder.findMethodModel(className, methodName, methodTypeDescOption);
    }

    public ImmutableList<MethodCallsFinder.InvokeInstructionInMethod> findMethodCalls(MethodModel methodModel) {
        MethodCallsFinder methodCallsFinder = new MethodCallsFinder(classUniverse, rootPackage);
        return methodCallsFinder.findMethodCalls(methodModel);
    }

    public ImmutableList<MethodCallsFinder.InvokeInstructionInMethod> findMethodCallsRecursively(MethodModel methodModel) {
        return findMethodCallsRecursively(methodModel, maxRecursionDepth);
    }

    private ImmutableList<MethodCallsFinder.InvokeInstructionInMethod> findMethodCallsRecursively(MethodModel methodModel, int maxRecursionDepth) {
        if (maxRecursionDepth <= 0) {
            return ImmutableList.of();
        }
        // Extremely inefficient implementation

        ImmutableList<MethodCallsFinder.InvokeInstructionInMethod> directCallers = findMethodCalls(methodModel);

        // Recursion
        return directCallers.stream()
                .flatMap(inv -> {
                    MethodModel methodContainingCaller = inv.methodInClass().methodModel();
                    return Stream.concat(
                            Stream.of(inv),
                            findMethodCallsRecursively(methodContainingCaller, maxRecursionDepth - 1).stream()
                    );
                })
                .collect(ImmutableList.toImmutableList());
    }

    static void main(String... args) {
        Objects.checkIndex(1, args.length);
        String className = args[0];
        String methodName = args[1];
        Optional<MethodTypeDesc> methodTypeDescOption =
                args.length == 3 ? Optional.of(MethodTypeDesc.ofDescriptor(args[2])) : Optional.empty();

        String inspectionClasspath = System.getProperty("inspectionClasspath");
        Objects.requireNonNull(inspectionClasspath);

        String inspectionRootPackage = System.getProperty("inspectionRootPackage");
        Objects.requireNonNull(inspectionRootPackage);

        ClassModelParser classModelParser = new ClassModelParser(ClassFile.of());
        ClassUniverse classUniverse = new ClassUniverse(classModelParser.parseClassPath(inspectionClasspath));

        RecursiveMethodCallsFinder recursiveMethodCallsFinder = new RecursiveMethodCallsFinder(classUniverse, inspectionRootPackage);

        MethodModel methodModel = recursiveMethodCallsFinder.findMethodModel(className, methodName, methodTypeDescOption).orElseThrow();

        ImmutableList<MethodCallsFinder.InvokeInstructionInMethod> invokeInstructions = recursiveMethodCallsFinder.findMethodCallsRecursively(methodModel);

        System.out.println();

        invokeInstructions.forEach(ivk -> System.out.printf("Invoke instruction: %s%n", ivk));
    }
}
