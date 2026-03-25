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

import module java.base;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import eu.cdevreeze.tryjava25.classfiles.data.InvokeInstructionAndContainingMethod;
import eu.cdevreeze.tryjava25.classfiles.data.MethodAndContainingClass;
import eu.cdevreeze.tryjava25.classfiles.parse.ClassModelParser;
import eu.cdevreeze.tryjava25.classfiles.parse.ClassUniverse;
import eu.cdevreeze.tryjava25.classfiles.parse.EnhancedClassUniverse;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Nodes;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinter;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinters;

import javax.xml.namespace.QName;

/**
 * Program that finds callers (and potential callers) of a given method.
 * <p>
 * The program arguments are the owner of the method, as fully qualified class name, the method name,
 * and optionally a method type descriptor, as specified in
 * <a href="https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3.3">Method Descriptors</a>.
 * <p>
 * The following system properties are used: "inspectionClasspath" and "inspectionRootPackage".
 * The first one is a classpath string, as output by Maven command "mvn dependency:build-classpath", preferably
 * enhanced with a directory containing the compilation output (such as "target/classes").
 * <p>
 * The "inspectionRootPackage" limits the scope of the code where the method calls are searched.
 * <p>
 * There are many limitations in this program. Most importantly, use of reflection will not be detected.
 *
 * @author Chris de Vreeze
 */
public class MethodCallsFinder {

    private final EnhancedClassUniverse classUniverse;
    private final String rootPackage;

    public MethodCallsFinder(EnhancedClassUniverse classUniverse, String rootPackage) {
        this.classUniverse = Objects.requireNonNull(classUniverse);
        this.rootPackage = Objects.requireNonNull(rootPackage);
    }

    public Optional<MethodModel> findMethodModel(String className, String methodName, Optional<MethodTypeDesc> methodTypeDescOption) {
        int idx = className.lastIndexOf('.');
        String packageName = idx < 0 ? "" : className.substring(0, idx);
        String simpleClassName = idx < 0 ? className : className.substring(idx + 1);
        ClassDesc classDesc = ClassDesc.of(packageName, simpleClassName);
        ClassModel classModel = classUniverse.getClassUniverse().resolveClass(classDesc);
        return classModel.methods().stream()
                .filter(methodModel -> methodModel.methodName().equalsString(methodName))
                .filter(methodModel -> methodTypeDescOption.stream().allMatch(mtd -> methodModel.methodTypeSymbol().equals(mtd)))
                .findFirst();
    }

    public ImmutableList<InvokeInstructionAndContainingMethod> findMethodCalls(MethodModel methodModel) {
        ClassDesc classContainingMethod = methodModel.parent().orElseThrow().thisClass().asSymbol();
        List<ClassDesc> classesContainingCallsToTheMethod = classUniverse.getClassUsageMap().containsKey(classContainingMethod) ?
                classUniverse.getClassUsageMap().get(classContainingMethod) :
                ImmutableList.of();
        Objects.requireNonNull(classesContainingCallsToTheMethod);

        return classesContainingCallsToTheMethod
                .stream()
                .map(c -> classUniverse.getClassUniverse().resolveClass(c))
                .filter(cm -> {
                    String pkg = cm.thisClass().asSymbol().packageName();
                    return pkg.equals(rootPackage) || pkg.startsWith(rootPackage + ".");
                })
                .flatMap(cm -> findOwnInvokeInstructions(cm).stream())
                .filter(inv -> isMatchingMethodCall(inv.getInvokeInstruction(), methodModel))
                .distinct()
                .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<InvokeInstructionAndContainingMethod> findOwnInvokeInstructions(ClassModel classModel) {
        Preconditions.checkArgument(classUniverse.getClassUniverse().isClassOrInterface(classModel)); // no-op for interfaces

        if (classUniverse.getClassUniverse().isInterface(classModel)) {
            return ImmutableList.of();
        }

        Preconditions.checkState(classUniverse.getClassUniverse().isRegularClass(classModel));

        List<MethodAndContainingClass> methods = classModel.methods().stream().map(MethodAndContainingClass::of).toList();

        return methods.stream()
                .filter(m -> m.getMethodModel().code().isPresent())
                .flatMap(m ->
                        m.getMethodModel().code().orElseThrow().elementStream().flatMap(codeElem ->
                                codeElem instanceof InvokeInstruction invokeInstruction ?
                                        Stream.of(new InvokeInstructionAndContainingMethod(invokeInstruction, m)) :
                                        Stream.empty()
                        )
                )
                .collect(ImmutableList.toImmutableList());
    }

    private boolean isMatchingMethodCall(InvokeInstruction invokeInstruction, MethodModel methodModel) {
        if (!invokeInstruction.name().equalsString(methodModel.methodName().stringValue())) {
            return false;
        }

        // TODO Check against JVM spec, i.e., https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
        // TODO Also look at https://www.guardsquare.com/blog/behind-the-scenes-of-jvm-method-invocations

        // TODO Invoke-dynamic (for lambdas)

        ClassModel methodOwner = methodModel.parent().orElseThrow();

        return invokeInstruction.owner().matches(methodOwner.thisClass().asSymbol()) &&
                invokeInstruction.method().name().equalsString(methodModel.methodName().stringValue()) &&
                invokeInstruction.typeSymbol().equals(methodModel.methodTypeSymbol());
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
        // Expensive call
        ClassUniverse rawClassUniverse = new ClassUniverse(classModelParser.parseClassPath(inspectionClasspath));

        // Expensive call
        EnhancedClassUniverse classUniverse = EnhancedClassUniverse.create(rawClassUniverse, inspectionRootPackage);

        MethodCallsFinder methodCallsFinder = new MethodCallsFinder(classUniverse, inspectionRootPackage);

        MethodModel methodModel = methodCallsFinder.findMethodModel(className, methodName, methodTypeDescOption).orElseThrow();

        ImmutableList<InvokeInstructionAndContainingMethod> invokeInstructions = methodCallsFinder.findMethodCalls(methodModel);

        Element invokeInstructionsRootElem = Nodes.elem(new QName("invokeInstructions"))
                .plusChildren(invokeInstructions.stream().map(InvokeInstructionAndContainingMethod::toXml).collect(ImmutableList.toImmutableList()));

        DocumentPrinter docPrinter = DocumentPrinters.instance();
        String xml = docPrinter.print(invokeInstructionsRootElem);
        System.out.println(xml);
    }
}
