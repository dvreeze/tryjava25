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

package eu.cdevreeze.tryjava25.classfiles.parse;

import module java.base;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import eu.cdevreeze.tryjava25.classfiles.internal.MyGatherers;

/**
 * Utility methods to get all ancestor classes of a class, all implemented interfaces, etc.
 * <p>
 * Of course, it is naive and very inefficient to "load" all classes (except for JDK classes) eagerly.
 *
 * @author Chris de Vreeze
 */
public final class ClassUniverse {

    private final ImmutableMap<ClassDesc, ClassModel> universe; // excludes JDK classes
    private final FileSystem jrtFileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));

    public ClassUniverse(ImmutableMap<ClassDesc, ClassModel> universe) {
        this.universe = universe;
    }

    public ImmutableMap<ClassDesc, ClassModel> getUniverse() {
        return universe;
    }

    public ImmutableList<ClassModel> findAllSuperclasses(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        return findAllSuperclassesOrSelf(classModel)
                .stream()
                .skip(1)
                .collect(ImmutableList.toImmutableList());
    }

    public ImmutableList<ClassModel> findAllSuperclassesOrSelf(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        Optional<ClassModel> superclassOption = classModel
                .superclass()
                .map(this::resolveClass);
        // Recursive
        return Stream.concat(
                        Stream.of(classModel),
                        superclassOption.map(c -> findAllSuperclassesOrSelf(c).stream()).orElse(Stream.empty())
                )
                .collect(ImmutableList.toImmutableList());
    }

    public ImmutableList<ClassModel> findAllSupertypesOrSelf(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        return Stream.concat(
                        Stream.of(classModel),
                        Stream.concat(
                                findAllSuperclasses(classModel).stream(),
                                findAllInterfaces(classModel).stream()
                        )
                )
                .gather(MyGatherers.distinctBy(cm -> cm.thisClass().asSymbol()))
                .collect(ImmutableList.toImmutableList());
    }

    /**
     * Finds all directly or indirectly extended/implemented interfaces. The result does not include this
     * classModel if it is an interface.
     */
    public ImmutableList<ClassModel> findAllInterfaces(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        // This finds all own implemented/extended interfaces, those of superclasses, and
        // those extending the found interfaces

        return findAllSuperclassesOrSelf(classModel)
                .stream()
                .flatMap(c -> c.interfaces().stream().map(this::resolveClass))
                .gather(MyGatherers.distinctBy(cm -> cm.thisClass().asSymbol()))
                .flatMap(itf -> findAllExtendedInterfacesOrSelf(itf).stream())
                .gather(MyGatherers.distinctBy(cm -> cm.thisClass().asSymbol()))
                .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<ClassModel> findAllExtendedInterfacesOrSelf(ClassModel interfaceModel) {
        Preconditions.checkArgument(isInterface(interfaceModel));

        // Recursive
        return Stream.concat(
                Stream.of(interfaceModel),
                interfaceModel.interfaces().stream()
                        .flatMap(itf -> findAllExtendedInterfacesOrSelf(resolveClass(itf)).stream())
        ).collect(ImmutableList.toImmutableList());
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

    public ClassModel resolveClass(ClassEntry classEntry) {
        return resolveClass(classEntry.asSymbol());
    }

    public ClassModel resolveClass(ClassDesc classDesc) {
        // Somehow "Optional.ofNullable(universe.get(classDesc))" did not work
        // This might have to do with the fact that ClassModel data is lazily loaded

        if (universe.containsKey(classDesc)) {
            return Objects.requireNonNull(universe.get(classDesc));
        } else {
            return parseJavaSeClass(classDesc);
        }
    }

    private ClassModel parseJavaSeClass(ClassDesc classDesc) {
        try {
            Preconditions.checkArgument(classDesc.isClassOrInterface());

            String packageNameAsPath = classDesc.packageName().replace('.', '/');
            String simpleClassNameAsFileName = classDesc.displayName() + ".class";
            String classNameAsPath = packageNameAsPath.isEmpty() ? simpleClassNameAsFileName : packageNameAsPath + "/" + simpleClassNameAsFileName;

            // TODO Module java.se
            Path classFilePath = jrtFileSystem.getPath("modules", "java.base", classNameAsPath);

            return ClassFile.of().parse(classFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
