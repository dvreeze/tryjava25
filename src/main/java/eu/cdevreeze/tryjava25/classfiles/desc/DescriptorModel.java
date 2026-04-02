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

import module java.base;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

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

    public record Method(
            String methodName,
            MethodTypeDesc methodTypeDesc,
            ClassDesc parent,
            ImmutableSet<AccessFlag> accessFlags
    ) {
    }

    public interface Instruction {

        Opcode opcode();
    }

    public record InvokeInstruction(
            Opcode opcode,
            ClassDesc owner,
            String name,
            MethodTypeDesc typeSymbol,
            boolean isInterface
    ) implements Instruction {
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
    ) {
    }

    public record InvokeDynamicInstructionAndContainingMethod(
            InvokeDynamicInstruction invokeInstruction,
            Method containingMethod
    ) {
    }

    public static final class MethodSerializer extends StdSerializer<Method> {

        public MethodSerializer() {
            this(null);
        }

        public MethodSerializer(@Nullable Class<Method> t) {
            super(t);
        }

        @Override
        public void serialize(Method value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeStringProperty("methodName", value.methodName());
            gen.writeStringProperty("methodTypeSymbol", value.methodTypeDesc().descriptorString());
            gen.writeStringProperty("parent", value.parent().descriptorString());
            gen.writeEndObject();
        }
    }

    public static final class InvokeInstructionSerializer extends StdSerializer<InvokeInstruction> {

        public InvokeInstructionSerializer() {
            this(null);
        }

        public InvokeInstructionSerializer(@Nullable Class<InvokeInstruction> t) {
            super(t);
        }

        @Override
        public void serialize(InvokeInstruction value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeStringProperty("opcode", value.opcode().name());
            gen.writeStringProperty("owner", value.owner().descriptorString());
            gen.writeStringProperty("name", value.name());
            gen.writeStringProperty("typeSymbol", value.typeSymbol().descriptorString());
            gen.writeBooleanProperty("isInterface", value.isInterface());
            gen.writeEndObject();
        }
    }

    public static final class InvokeDynamicInstructionSerializer extends StdSerializer<InvokeDynamicInstruction> {

        public InvokeDynamicInstructionSerializer() {
            this(null);
        }

        public InvokeDynamicInstructionSerializer(@Nullable Class<InvokeDynamicInstruction> t) {
            super(t);
        }

        @Override
        public void serialize(InvokeDynamicInstruction value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeStringProperty("opcode", value.opcode().name());
            gen.writeStringProperty("name", value.name());
            gen.writeStringProperty("typeSymbol", value.typeSymbol().descriptorString());
            gen.writeStringProperty("bootstrapMethodToString", value.bootstrapMethod().toString());

            gen.writeObjectPropertyStart("bootstrapMethod");
            gen.writeStringProperty("kind", value.bootstrapMethod().kind().name());
            gen.writeStringProperty("owner", value.bootstrapMethod().owner().descriptorString());
            gen.writeBooleanProperty("isOwnerInterface", value.bootstrapMethod().isOwnerInterface());
            gen.writeStringProperty("methodName", value.bootstrapMethod().methodName());
            gen.writeStringProperty("lookupDescriptor", value.bootstrapMethod().lookupDescriptor());
            gen.writeStringProperty("invocationType", value.bootstrapMethod().invocationType().descriptorString());
            gen.writeNumberProperty("refKind", value.bootstrapMethod().refKind());
            gen.writeEndObject();

            gen.writeArrayPropertyStart("bootstrapArgs");
            value.bootstrapArgs().forEach(bootstrapArg -> {
                gen.writeStartObject();
                gen.writeStringProperty("type", value.toType(bootstrapArg));
                gen.writeStringProperty("value", value.toString(bootstrapArg));
                gen.writeEndObject();
            });
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }

    public static final class InvokeInstructionAndContainingMethodSerializer extends StdSerializer<InvokeInstructionAndContainingMethod> {

        public InvokeInstructionAndContainingMethodSerializer() {
            this(null);
        }

        public InvokeInstructionAndContainingMethodSerializer(@Nullable Class<InvokeInstructionAndContainingMethod> t) {
            super(t);
        }

        @Override
        public void serialize(InvokeInstructionAndContainingMethod value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeObjectPropertyStart("invokeInstructionAndContainingMethod");
            // Assumes availability of InvokeInstructionSerializer
            gen.writePOJOProperty("invokeInstruction", value.invokeInstruction());
            // Assumes availability of MethodSerializer
            gen.writePOJOProperty("methodContainingInstruction", value.containingMethod());
            gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    public static final class InvokeDynamicInstructionAndContainingMethodSerializer extends StdSerializer<InvokeDynamicInstructionAndContainingMethod> {

        public InvokeDynamicInstructionAndContainingMethodSerializer() {
            this(null);
        }

        public InvokeDynamicInstructionAndContainingMethodSerializer(@Nullable Class<InvokeDynamicInstructionAndContainingMethod> t) {
            super(t);
        }

        @Override
        public void serialize(InvokeDynamicInstructionAndContainingMethod value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeObjectPropertyStart("invokeDynamicInstructionAndContainingMethod");
            // Assumes availability of InvokeDynamicInstructionSerializer
            gen.writePOJOProperty("invokeDynamicInstruction", value.invokeInstruction());
            // Assumes availability of MethodSerializer
            gen.writePOJOProperty("methodContainingInstruction", value.containingMethod());
            gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    /**
     * {@link SimpleModule} to be registered with the {@link tools.jackson.databind.json.JsonMapper}.
     */
    public static SimpleModule createSimpleModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Method.class, new MethodSerializer());
        module.addSerializer(InvokeInstruction.class, new InvokeInstructionSerializer());
        module.addSerializer(InvokeDynamicInstruction.class, new InvokeDynamicInstructionSerializer());
        module.addSerializer(InvokeInstructionAndContainingMethod.class, new InvokeInstructionAndContainingMethodSerializer());
        module.addSerializer(InvokeDynamicInstructionAndContainingMethod.class, new InvokeDynamicInstructionAndContainingMethodSerializer());
        return module;
    }
}
