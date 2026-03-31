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

package eu.cdevreeze.tryjava25.archunitplus.desc;

import module java.base;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Nodes;

import javax.xml.namespace.QName;

/**
 * "Namespace" holding the immutable thread-safe "descriptor model". The record classes in the model
 * know how to "serialize" themselves to XML.
 *
 * @author Chris de Vreeze
 */
public class DescriptorModel {

    private DescriptorModel() {
        // Non-instantiable
    }

    public interface DescriptorModelData {

        Element toXml();

        Element toXml(QName rootElementName);
    }

    public record Method(
            String methodName,
            MethodTypeDesc methodTypeDesc,
            ClassDesc parent,
            ImmutableSet<AccessFlag> accessFlags
    ) implements DescriptorModelData {

        @Override
        public Element toXml() {
            return toXml(new QName("method"));
        }

        @Override
        public Element toXml(QName rootElementName) {
            return Nodes.elem(rootElementName)
                    .plusChild(Nodes.elem(new QName("methodName")).plusText(methodName()))
                    .plusChild(Nodes.elem(new QName("methodTypeSymbol")).plusText(methodTypeDesc().descriptorString()))
                    .plusChild(Nodes.elem(new QName("parent")).plusText(parent().descriptorString()));
        }
    }

    public interface Instruction extends DescriptorModelData {

        Opcode opcode();
    }

    public record InvokeInstruction(
            Opcode opcode,
            ClassDesc owner,
            String name,
            MethodTypeDesc typeSymbol,
            boolean isInterface
    ) implements Instruction {

        @Override
        public Element toXml() {
            return toXml(new QName("invokeInstruction"));
        }

        @Override
        public Element toXml(QName rootElementName) {
            return Nodes.elem(rootElementName)
                    .plusChild(Nodes.elem(new QName("opcode")).plusText(opcode().name()))
                    .plusChild(Nodes.elem(new QName("owner")).plusText(owner().descriptorString()))
                    .plusChild(Nodes.elem(new QName("name")).plusText(name()))
                    .plusChild(Nodes.elem(new QName("typeSymbol")).plusText(typeSymbol().descriptorString()))
                    .plusChild(Nodes.elem(new QName("isInterface")).plusText(String.valueOf(isInterface())));
        }
    }

    // See e.g. https://www.baeldung.com/java-invoke-dynamic

    public record InvokeDynamicInstruction(
            Opcode opcode,
            String name,
            MethodTypeDesc typeSymbol,
            DynamicCallSiteDesc dynamicCallSiteDesc,
            DirectMethodHandleDesc bootstrapMethod,
            ImmutableList<ConstantDesc> bootstrapArgs
    ) implements Instruction {

        @Override
        public Element toXml() {
            return toXml(new QName("invokeDynamicInstruction"));
        }

        @Override
        public Element toXml(QName rootElementName) {
            var lookup = MethodHandles.lookup();

            return Nodes.elem(rootElementName)
                    .plusChild(Nodes.elem(new QName("opcode")).plusText(opcode().name()))
                    .plusChild(Nodes.elem(new QName("name")).plusText(name()))
                    .plusChild(Nodes.elem(new QName("typeSymbol")).plusText(typeSymbol().descriptorString()))
                    .plusChild(Nodes.elem(new QName("bootstrapMethodToString")).plusText(bootstrapMethod().toString()))
                    .plusChild(
                            Nodes.elem(new QName("bootstrapMethod"))
                                    .plusChild(Nodes.elem(new QName("kind")).plusText(bootstrapMethod().kind().name()))
                                    .plusChild(Nodes.elem(new QName("owner")).plusText(bootstrapMethod().owner().descriptorString()))
                                    .plusChild(Nodes.elem(new QName("isOwnerInterface")).plusText(String.valueOf(bootstrapMethod().isOwnerInterface())))
                                    .plusChild(Nodes.elem(new QName("methodName")).plusText(bootstrapMethod().methodName()))
                                    .plusChild(Nodes.elem(new QName("lookupDescriptor")).plusText(bootstrapMethod().lookupDescriptor()))
                                    .plusChild(Nodes.elem(new QName("invocationType")).plusText(bootstrapMethod().invocationType().descriptorString()))
                                    .plusChild(Nodes.elem(new QName("refKind")).plusText(String.valueOf(bootstrapMethod().refKind())))
                    )
                    .plusChild(Nodes.elem(new QName("bootstrapArgs"))
                            .plusChildren(bootstrapArgs()
                                    .stream()
                                    .map(arg ->
                                            Nodes.elem(new QName("arg"))
                                                    .plusAttribute(new QName("type"), toType(arg))
                                                    .plusText(toString(arg))
                                    )
                                    .collect(ImmutableList.toImmutableList())));
        }

        private String toString(ConstantDesc constantDesc) {
            return switch (constantDesc) {
                case ClassDesc classDesc -> classDesc.descriptorString();
                case MethodTypeDesc methodTypeDesc -> methodTypeDesc.descriptorString();
                case DynamicConstantDesc<?> dynamicConstantDesc -> dynamicConstantDesc.toString();
                case MethodHandleDesc methodHandleDesc -> methodHandleDesc.toString();
                case Double n -> n.toString();
                case Float n -> n.toString();
                case Integer n -> n.toString();
                case Long n -> n.toString();
                // case String v -> v; // An invalid XML character (Unicode: 0x1) was found in the node's character data content
                default -> "";
            };
        }

        private String toType(ConstantDesc constantDesc) {
            return switch (constantDesc) {
                case ClassDesc _ -> "java.lang.constant.ClassDesc";
                case MethodTypeDesc _ -> "java.lang.constant.MethodTypeDesc";
                case DynamicConstantDesc<?> _ -> "java.lang.constant.DynamicConstantDesc";
                case DirectMethodHandleDesc _ -> "java.lang.constant.DirectMethodHandleDesc";
                case MethodHandleDesc _ -> "java.lang.constant.MethodHandleDesc";
                case Double _ -> "java.lang.Double";
                case Float _ -> "java.lang.Float";
                case Integer _ -> "java.lang.Integer";
                case Long _ -> "java.lang.Long";
                case String _ -> "java.lang.String";
            };
        }
    }

    public record InvokeInstructionAndContainingMethod(
            InvokeInstruction invokeInstruction,
            Method containingMethod
    ) implements DescriptorModelData {

        @Override
        public Element toXml() {
            return toXml(new QName("invokeInstructionAndContainingMethod"));
        }

        @Override
        public Element toXml(QName rootElementName) {
            return Nodes.elem(rootElementName)
                    .plusChild(invokeInstruction().toXml())
                    .plusChild(containingMethod().toXml(new QName("methodContainingInstruction")));
        }
    }

    public record InvokeDynamicInstructionAndContainingMethod(
            InvokeDynamicInstruction invokeInstruction,
            Method containingMethod
    ) implements DescriptorModelData {

        @Override
        public Element toXml() {
            return toXml(new QName("invokeDynamicInstructionAndContainingMethod"));
        }

        @Override
        public Element toXml(QName rootElementName) {
            return Nodes.elem(rootElementName)
                    .plusChild(invokeInstruction().toXml())
                    .plusChild(containingMethod().toXml(new QName("methodContainingInstruction")));
        }
    }
}
