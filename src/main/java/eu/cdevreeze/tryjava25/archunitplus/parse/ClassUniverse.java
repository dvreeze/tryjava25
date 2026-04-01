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

package eu.cdevreeze.tryjava25.archunitplus.parse;

import module java.base;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import eu.cdevreeze.tryjava25.archunitplus.internal.MyGatherers;

/**
 * Utility methods to get all ancestor classes of a class, all implemented interfaces, etc.
 *
 * @author Chris de Vreeze
 */
public final class ClassUniverse {

    // Very inefficient at the moment

    private final JavaClasses universe;
    private final FileSystem jrtFileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));

    public ClassUniverse(JavaClasses universe) {
        this.universe = universe;
    }

    public JavaClasses getUniverse() {
        return universe;
    }

    public static ClassUniverse of(JavaClasses universe) {
        return new ClassUniverse(universe);
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

        Optional<ClassModel> currentSuperclassOption = classModel.superclass().map(this::resolveClass);

        ImmutableList.Builder<ClassModel> builder = ImmutableList.builder();

        while (currentSuperclassOption.isPresent()) {
            builder.add(currentSuperclassOption.orElseThrow());
            currentSuperclassOption = currentSuperclassOption.flatMap(ClassModel::superclass).map(this::resolveClass);
        }

        return builder.build();
    }

    public ImmutableList<ClassModel> findAllSupertypesOrSelf(ClassModel classModel) {
        Preconditions.checkArgument(isClassOrInterface(classModel));

        ImmutableList.Builder<ClassModel> builder = ImmutableList.builder();

        builder.add(classModel);
        builder.addAll(findAllSuperclasses(classModel));
        builder.addAll(findAllInterfaces(classModel));

        return builder.build()
                .stream()
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

        ImmutableList.Builder<ClassModel> builder = ImmutableList.builder();

        builder.add(interfaceModel);

        // Recursive
        builder.addAll(
                interfaceModel.interfaces()
                        .stream()
                        .flatMap(itf -> findAllExtendedInterfacesOrSelf(resolveClass(itf)).stream())
                        .toList()
        );

        return builder.build()
                .stream()
                .gather(MyGatherers.distinctBy(cm -> cm.thisClass().asSymbol()))
                .collect(ImmutableList.toImmutableList());
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

        if (universe.contain(getFullyQualifiedName(classDesc))) {
            JavaClass javaClass = Objects.requireNonNull(universe.get(getFullyQualifiedName(classDesc)));
            return new ClassModelParser(ClassFile.of()).parseClassModel(javaClass);
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

    private String getFullyQualifiedName(ClassDesc classDesc) {
        String packageName = classDesc.packageName();
        return packageName.isEmpty() ? classDesc.displayName() : packageName + "." + classDesc.displayName();
    }
}
