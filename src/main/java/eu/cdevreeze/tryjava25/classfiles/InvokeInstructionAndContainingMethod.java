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

package eu.cdevreeze.tryjava25.classfiles;

import com.google.common.base.Preconditions;

import java.lang.classfile.instruction.InvokeInstruction;

/**
 * An{@link InvokeInstruction} and its containing method as {@link MethodAndContainingClass}.
 *
 * @author Chris de Vreeze
 */
public record InvokeInstructionAndContainingMethod(InvokeInstruction invokeInstruction,
                                                   MethodAndContainingClass methodAndContainingClass) {

    public InvokeInstructionAndContainingMethod {
        Preconditions.checkArgument(
                methodAndContainingClass.methodModel().code().orElseThrow().elementStream()
                        .anyMatch(codeElem ->
                                codeElem instanceof InvokeInstruction ivk && equalInstructions(ivk, invokeInstruction)
                        )
        );
    }

    private static boolean equalInstructions(InvokeInstruction ivk1, InvokeInstruction ivk2) {
        return ivk1.opcode() == ivk2.opcode() &&
                ivk1.owner().asSymbol().equals(ivk2.owner().asSymbol()) &&
                ivk1.name().equalsString(ivk2.name().stringValue()) &&
                ivk1.typeSymbol().equals(ivk2.typeSymbol()) &&
                ivk1.isInterface() == ivk2.isInterface();
    }
}
