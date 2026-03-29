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

package eu.cdevreeze.tryjava25.classfiles.data;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import eu.cdevreeze.tryjava25.classfiles.desc.DescriptorModel;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;

import javax.xml.namespace.QName;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * An {@link InvokeDynamicInstruction} and its containing method as {@link MethodAndContainingClass}.
 * <p>
 * This is not a {@link Record} with properly defined value equality, because of the lazily evaluated {@link MethodModel}
 * inside {@link MethodAndContainingClass}.
 *
 * @author Chris de Vreeze
 */
public final class InvokeDynamicInstructionAndContainingMethod {

    private static boolean equalInstructions(InvokeDynamicInstruction ivk1, InvokeDynamicInstruction ivk2) {
        return ivk1.opcode() == ivk2.opcode() &&
                ivk1.name().equalsString(ivk2.name().stringValue()) &&
                ivk1.typeSymbol().equals(ivk2.typeSymbol()) &&
                ivk1.invokedynamic().asSymbol() == ivk2.invokedynamic().asSymbol() &&
                ivk1.bootstrapMethod().kind().equals(ivk2.bootstrapMethod().kind()) &&
                ivk1.bootstrapMethod().owner().equals(ivk2.bootstrapMethod().owner()) &&
                ivk1.bootstrapMethod().methodName().equals(ivk2.bootstrapMethod().methodName()) &&
                ivk1.bootstrapMethod().invocationType().equals(ivk2.bootstrapMethod().invocationType()) &&
                ivk1.bootstrapMethod().isOwnerInterface() == ivk2.bootstrapMethod().isOwnerInterface() &&
                ivk1.bootstrapMethod().lookupDescriptor().equals(ivk2.bootstrapMethod().lookupDescriptor()) &&
                ivk1.bootstrapMethod().refKind() == ivk2.bootstrapMethod().refKind() &&
                ivk1.bootstrapArgs().stream().filter(v -> v instanceof MethodTypeDesc).toList()
                        .equals(ivk2.bootstrapArgs().stream().filter(v -> v instanceof MethodTypeDesc).toList()) &&
                ivk1.bootstrapArgs().stream().filter(v -> v instanceof MethodHandleDesc).toList()
                        .equals(ivk2.bootstrapArgs().stream().filter(v -> v instanceof MethodHandleDesc).toList());
    }

    private final InvokeDynamicInstruction invokeInstruction;
    private final MethodAndContainingClass methodAndContainingClass;

    public InvokeDynamicInstructionAndContainingMethod(InvokeDynamicInstruction invokeInstruction, MethodAndContainingClass methodAndContainingClass) {
        Preconditions.checkArgument(
                methodAndContainingClass.getMethodModel().code().orElseThrow().elementStream()
                        .anyMatch(codeElem ->
                                codeElem instanceof InvokeDynamicInstruction ivk && equalInstructions(ivk, invokeInstruction)
                        )
        );

        this.invokeInstruction = invokeInstruction;
        this.methodAndContainingClass = methodAndContainingClass;
    }

    public InvokeDynamicInstruction getInvokeInstruction() {
        return invokeInstruction;
    }

    public MethodAndContainingClass getMethodAndContainingClass() {
        return methodAndContainingClass;
    }

    public DescriptorModel.InvokeDynamicInstructionAndContainingMethod toDescriptorModel() {
        return new DescriptorModel.InvokeDynamicInstructionAndContainingMethod(
                new DescriptorModel.InvokeDynamicInstruction(
                        getInvokeInstruction().opcode(),
                        getInvokeInstruction().name().stringValue(),
                        getInvokeInstruction().typeSymbol(),
                        getInvokeInstruction().invokedynamic().asSymbol(),
                        getInvokeInstruction().bootstrapMethod(),
                        getInvokeInstruction().bootstrapArgs().stream().collect(ImmutableList.toImmutableList())
                ),
                getMethodAndContainingClass().toDescriptorModel()
        );
    }

    public Element toXml() {
        return toDescriptorModel().toXml();
    }

    public Element toXml(QName rootElementName) {
        return toDescriptorModel().toXml(rootElementName);
    }
}
