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

package eu.cdevreeze.tryjava25.classfiles.desc;

import com.google.common.collect.ImmutableSet;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Nodes;

import javax.xml.namespace.QName;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;

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
}
