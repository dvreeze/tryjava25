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

package eu.cdevreeze.tryjava25.archunitplus.console;

import com.google.common.collect.ImmutableList;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import eu.cdevreeze.tryjava25.archunitplus.data.InvokeInstructionAndContainingMethod;
import eu.cdevreeze.tryjava25.archunitplus.internal.MyGatherers;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Nodes;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinter;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinters;

import javax.xml.namespace.QName;
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

    private final JavaClasses javaClasses;
    private final String rootPackage;
    private final int maxRecursionDepth;

    public RecursiveMethodCallsFinder(JavaClasses javaClasses, String rootPackage) {
        this.javaClasses = javaClasses;
        this.rootPackage = rootPackage;
        this.maxRecursionDepth = Integer.parseInt(System.getProperty("maxRecursionDepth", "20"));
    }

    public Optional<MethodModel> findMethodModel(String className, String methodName, Optional<MethodTypeDesc> methodTypeDescOption) {
        MethodCallsFinder methodCallsFinder = new MethodCallsFinder(javaClasses, rootPackage);
        return methodCallsFinder.findMethodModel(className, methodName, methodTypeDescOption);
    }

    public ImmutableList<InvokeInstructionAndContainingMethod> findMethodCalls(MethodModel methodModel) {
        MethodCallsFinder methodCallsFinder = new MethodCallsFinder(javaClasses, rootPackage);
        return methodCallsFinder.findMethodCalls(methodModel);
    }

    public ImmutableList<InvokeInstructionAndContainingMethod> findMethodCallsRecursively(MethodModel methodModel) {
        return findMethodCallsRecursively(methodModel, maxRecursionDepth);
    }

    private ImmutableList<InvokeInstructionAndContainingMethod> findMethodCallsRecursively(MethodModel methodModel, int maxRecursionDepth) {
        if (maxRecursionDepth <= 0) {
            return ImmutableList.of();
        }

        ImmutableList<InvokeInstructionAndContainingMethod> directCallers = findMethodCalls(methodModel);

        // Recursion
        return directCallers.stream()
                .flatMap(inv -> {
                    MethodModel methodContainingCaller = inv.getMethodAndContainingClass().getMethodModel();
                    return Stream.concat(
                            Stream.of(inv),
                            findMethodCallsRecursively(methodContainingCaller, maxRecursionDepth - 1).stream()
                    );
                })
                .gather(MyGatherers.distinctBy(InvokeInstructionAndContainingMethod::toDescriptorModel))
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
        String[] paths = inspectionClasspath.split(":");

        String inspectionRootPackage = System.getProperty("inspectionRootPackage");
        Objects.requireNonNull(inspectionRootPackage);

        ClassFileImporter classFileImporter = new ClassFileImporter();
        // Expensive call
        JavaClasses javaClasses = classFileImporter.importPaths(paths);

        RecursiveMethodCallsFinder methodCallsFinder = new RecursiveMethodCallsFinder(javaClasses, inspectionRootPackage);

        MethodModel methodModel = methodCallsFinder.findMethodModel(className, methodName, methodTypeDescOption).orElseThrow();

        ImmutableList<InvokeInstructionAndContainingMethod> invokeInstructions = methodCallsFinder.findMethodCallsRecursively(methodModel);

        Element invokeInstructionsRootElem = Nodes.elem(new QName("invokeInstructions"))
                .plusChildren(invokeInstructions.stream().map(InvokeInstructionAndContainingMethod::toXml).collect(ImmutableList.toImmutableList()));

        DocumentPrinter docPrinter = DocumentPrinters.instance();
        String xml = docPrinter.print(invokeInstructionsRootElem);
        System.out.println(xml);
    }
}
