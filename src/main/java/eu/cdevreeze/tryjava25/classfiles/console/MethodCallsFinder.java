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
import eu.cdevreeze.tryjava25.classfiles.ClassModelParser;
import eu.cdevreeze.tryjava25.classfiles.ClassUniverse;

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

    public record MethodInClass(MethodModel methodModel, ClassDesc classDesc) {

        public MethodInClass {
            Preconditions.checkArgument(methodModel.parent().isPresent());
            Preconditions.checkArgument(methodModel.parent().orElseThrow().thisClass().asSymbol().equals(classDesc));
        }

        public ClassModel parent() {
            return methodModel().parent().orElseThrow();
        }

        public static MethodInClass of(MethodModel methodModel) {
            return new MethodInClass(methodModel, methodModel.parent().orElseThrow().thisClass().asSymbol());
        }
    }

    public record InvokeInstructionInMethod(InvokeInstruction invokeInstruction, MethodInClass methodInClass) {

        public InvokeInstructionInMethod {
            // Assuming equality for ClassFileElement is well-defined
            /*
            Preconditions.checkArgument(
                    methodInClass.methodModel().code().orElseThrow().elementStream()
                            .anyMatch(codeElem -> codeElem.equals(invokeInstruction))

            );
            */
        }
    }

    private final ClassUniverse classUniverse;
    private final String rootPackage;

    public MethodCallsFinder(ClassUniverse classUniverse, String rootPackage) {
        this.classUniverse = Objects.requireNonNull(classUniverse);
        this.rootPackage = Objects.requireNonNull(rootPackage);
    }

    public ImmutableList<InvokeInstructionInMethod> findPotentialMethodCalls(MethodModel methodModel) {
        ClassModel owningClass = methodModel.parent().orElseThrow();
        ImmutableList<ClassModel> supertypesOrSelf = classUniverse.findAllSupertypesOrSelf(owningClass);

        return classUniverse.getUniverse()
                .values()
                .stream()
                .filter(cm -> {
                    String pkg = cm.thisClass().asSymbol().packageName();
                    return pkg.equals(rootPackage) || pkg.startsWith(rootPackage + ".");
                })
                .flatMap(cm -> findOwnInvokeInstructions(cm).stream())
                .filter(inv -> isMatchingMethodCall(inv.invokeInstruction(), methodModel, supertypesOrSelf))
                .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<InvokeInstructionInMethod> findOwnInvokeInstructions(ClassModel classModel) {
        Preconditions.checkArgument(classUniverse.isClassOrInterface(classModel)); // no-op for interfaces

        if (classUniverse.isInterface(classModel)) {
            return ImmutableList.of();
        }

        Preconditions.checkState(classUniverse.isRegularClass(classModel));

        List<MethodInClass> methods = classModel.methods().stream().map(MethodInClass::of).toList();

        return methods.stream()
                .filter(m -> m.methodModel().code().isPresent())
                .flatMap(m ->
                        m.methodModel().code().orElseThrow().elementStream().flatMap(codeElem ->
                                codeElem instanceof InvokeInstruction invokeInstruction ?
                                        Stream.of(new InvokeInstructionInMethod(invokeInstruction, m)) :
                                        Stream.empty()
                        )
                )
                .collect(ImmutableList.toImmutableList());
    }

    private boolean isMatchingMethodCall(InvokeInstruction invokeInstruction, MethodModel methodModel, List<ClassModel> ownerSupertypesOrSelf) {
        if (!invokeInstruction.name().equalsString(methodModel.methodName().stringValue())) {
            return false;
        }

        // TODO Check against JVM spec, and/or study API better (e.g. InvokeInstruction.of methods)

        ClassModel methodOwner = methodModel.parent().orElseThrow();

        return switch (invokeInstruction.opcode()) {
            case Opcode.INVOKESPECIAL, Opcode.INVOKESTATIC -> !invokeInstruction.isInterface() &&
                    invokeInstruction.owner().matches(methodOwner.thisClass().asSymbol()) &&
                    invokeInstruction.method().name().equalsString(methodModel.methodName().stringValue()) &&
                    invokeInstruction.method().type().equalsString(methodModel.methodType().stringValue());
            case Opcode.INVOKEINTERFACE -> invokeInstruction.isInterface() &&
                    ownerSupertypesOrSelf.stream()
                            .filter(classUniverse::isInterface)
                            .anyMatch(cls ->
                                    invokeInstruction.owner().matches(cls.thisClass().asSymbol()) &&
                                            invokeInstruction.method().name().equalsString(methodModel.methodName().stringValue()) &&
                                            invokeInstruction.method().type().equalsString(methodModel.methodType().stringValue())
                            );
            case Opcode.INVOKEVIRTUAL -> !invokeInstruction.isInterface() &&
                    ownerSupertypesOrSelf.stream()
                            .filter(cls -> !classUniverse.isInterface(cls))
                            .anyMatch(cls ->
                                    invokeInstruction.owner().matches(cls.thisClass().asSymbol()) &&
                                            invokeInstruction.method().name().equalsString(methodModel.methodName().stringValue()) &&
                                            invokeInstruction.method().type().equalsString(methodModel.methodType().stringValue())
                            );
            default -> false;
        };
    }

    public Optional<MethodModel> findMethodModel(String className, String methodName) {
        // TODO Use method type descriptor

        int idx = className.lastIndexOf('.');
        String packageName = idx < 0 ? "" : className.substring(0, idx);
        String simpleClassName = idx < 0 ? className : className.substring(idx + 1);
        ClassDesc classDesc = ClassDesc.of(packageName, simpleClassName);
        ClassModel classModel = Objects.requireNonNull(classUniverse.getUniverse().get(classDesc));
        return classModel.elementStream()
                .flatMap(classElem ->
                        classElem instanceof MethodModel methodModel ? Stream.of(methodModel) : Stream.empty()
                )
                .filter(methodModel -> methodModel.methodName().equalsString(methodName))
                .findFirst();
    }

    static void main(String... args) {
        Objects.checkIndex(1, args.length);
        String className = args[0];
        String methodName = args[1];

        String inspectionClasspath = System.getProperty("inspectionClasspath");
        Objects.requireNonNull(inspectionClasspath);

        String inspectionRootPackage = System.getProperty("inspectionRootPackage");
        Objects.requireNonNull(inspectionRootPackage);

        ClassModelParser classModelParser = new ClassModelParser(ClassFile.of());
        ClassUniverse classUniverse = new ClassUniverse(classModelParser.parseClassPath(inspectionClasspath));

        MethodCallsFinder methodCallsFinder = new MethodCallsFinder(classUniverse, inspectionRootPackage);

        MethodModel methodModel = methodCallsFinder.findMethodModel(className, methodName).orElseThrow();

        ImmutableList<InvokeInstructionInMethod> invokeInstructions = methodCallsFinder.findPotentialMethodCalls(methodModel);

        invokeInstructions.forEach(System.out::println);
    }
}
