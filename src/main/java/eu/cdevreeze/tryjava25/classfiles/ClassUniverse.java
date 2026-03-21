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

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utility methods to get all ancestor classes of a class, all implemented interfaces, etc.
 * This assumes a complete "universe" of {@link ClassModel} objects, as a sort of complete "classpath".
 *
 * @author Chris de Vreeze
 */
public record ClassUniverse(Map<ClassDesc, ClassModel> universe) {

    public List<ClassModel> findAllSuperclasses(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        return findAllSuperclassesOrSelf(classModel).stream().skip(1).toList();
    }

    public List<ClassModel> findAllSuperclassesOrSelf(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        Optional<ClassModel> superclassOption = classModel
                .superclass()
                .map(this::resolveClass);
        // Recursive
        return Stream.concat(
                        Stream.of(classModel),
                        superclassOption.map(c -> findAllSuperclassesOrSelf(c).stream()).orElse(Stream.empty())
                )
                .toList();
    }

    public List<ClassModel> findAllInterfaces(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        // This finds all own implemented/extended interfaces, those of superclasses, and
        // those extending the found interfaces

        return findAllSuperclassesOrSelf(classModel)
                .stream()
                .flatMap(c -> c.interfaces().stream().map(this::resolveClass))
                .distinct()
                .flatMap(itf -> findAllExtendedInterfacesOrSelf(itf).stream())
                .distinct()
                .toList();
    }

    private List<ClassModel> findAllExtendedInterfacesOrSelf(ClassModel interfaceModel) {
        Preconditions.checkArgument(isInterface(interfaceModel));

        // Recursive
        return Stream.concat(
                Stream.of(interfaceModel),
                interfaceModel.interfaces().stream()
                        .flatMap(itf -> findAllExtendedInterfacesOrSelf(resolveClass(itf)).stream())
        ).toList();
    }

    public boolean isInterface(ClassModel classModel) {
        return isClassOrInterface(classModel) && classModel.flags().has(AccessFlag.INTERFACE);
    }

    public boolean isRegularClass(ClassModel classModel) {
        return isClassOrInterface(classModel) && !isInterface(classModel);
    }

    public boolean isClassOrInterface(ClassModel classModel) {
        return classModel.thisClass().asSymbol().isClassOrInterface();
    }

    private ClassModel resolveClass(ClassEntry classEntry) {
        return Objects.requireNonNull(universe().get(classEntry.asSymbol()));
    }
}
