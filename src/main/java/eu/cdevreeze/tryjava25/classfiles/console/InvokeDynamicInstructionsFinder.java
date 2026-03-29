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
import eu.cdevreeze.tryjava25.classfiles.data.InvokeDynamicInstructionAndContainingMethod;
import eu.cdevreeze.tryjava25.classfiles.data.MethodAndContainingClass;
import eu.cdevreeze.tryjava25.classfiles.parse.ClassModelParser;
import eu.cdevreeze.tryjava25.classfiles.parse.ClassUniverse;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Nodes;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinter;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinters;

import javax.xml.namespace.QName;

/**
 * Program that finds all invoke-dynamic instructions in a given class.
 * <p>
 * The only program argument is the fully-qualified class-name of the class within which all invoke-dynamic
 * instructions should be found.
 * <p>
 * The following system property is used: "inspectionClasspath".
 * This is a classpath string, as output by Maven command "mvn dependency:build-classpath", preferably
 * enhanced with a directory containing the compilation output (such as "target/classes").
 * <p>
 * There are many limitations in this program, mainly due to an incomplete understanding of the invoke-dynamic
 * instruction.
 *
 * @author Chris de Vreeze
 */
public class InvokeDynamicInstructionsFinder {

    private final ClassUniverse classUniverse;

    public InvokeDynamicInstructionsFinder(ClassUniverse classUniverse) {
        this.classUniverse = Objects.requireNonNull(classUniverse);
    }

    public ImmutableList<InvokeDynamicInstructionAndContainingMethod> findInvokeDynamicInstructions(ClassDesc classDesc) {
        ClassModel classModel = classUniverse.resolveClass(classDesc);
        return findInvokeDynamicInstructions(classModel);
    }

    public ImmutableList<InvokeDynamicInstructionAndContainingMethod> findInvokeDynamicInstructions(ClassModel classModel) {
        Preconditions.checkArgument(classUniverse.isClassOrInterface(classModel)); // no-op for interfaces

        if (classUniverse.isInterface(classModel)) {
            return ImmutableList.of();
        }

        Preconditions.checkState(classUniverse.isRegularClass(classModel));

        List<MethodAndContainingClass> methods = classModel.methods().stream().map(MethodAndContainingClass::of).toList();

        return methods.stream()
                .filter(m -> m.getMethodModel().code().isPresent())
                .flatMap(m ->
                        m.getMethodModel().code().orElseThrow().elementStream().flatMap(codeElem ->
                                codeElem instanceof InvokeDynamicInstruction invokeInstruction ?
                                        Stream.of(new InvokeDynamicInstructionAndContainingMethod(invokeInstruction, m)) :
                                        Stream.empty()
                        )
                )
                .collect(ImmutableList.toImmutableList());
    }

    private static ClassDesc parseClassDesc(String className) {
        int idx = className.lastIndexOf('.');
        String packageName = idx < 0 ? "" : className.substring(0, idx);
        String simpleClassName = idx < 0 ? className : className.substring(idx + 1);
        return ClassDesc.of(packageName, simpleClassName);
    }

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        String className = args[0];

        String inspectionClasspath = System.getProperty("inspectionClasspath");
        Objects.requireNonNull(inspectionClasspath);

        ClassModelParser classModelParser = new ClassModelParser(ClassFile.of());
        // Expensive call
        ClassUniverse classUniverse = new ClassUniverse(classModelParser.parseClassPath(inspectionClasspath));

        InvokeDynamicInstructionsFinder invokeDynamicInstructionsFinder = new InvokeDynamicInstructionsFinder(classUniverse);

        ImmutableList<InvokeDynamicInstructionAndContainingMethod> invokeDynamicInstructions =
                invokeDynamicInstructionsFinder.findInvokeDynamicInstructions(parseClassDesc(className));

        Element invokeInstructionsRootElem = Nodes.elem(new QName("invokeDynamicInstructions"))
                .plusChildren(invokeDynamicInstructions.stream().map(InvokeDynamicInstructionAndContainingMethod::toXml).collect(ImmutableList.toImmutableList()));

        DocumentPrinter docPrinter = DocumentPrinters.instance();
        String xml = docPrinter.print(invokeInstructionsRootElem);
        System.out.println(xml);
    }
}
