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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Parsing utility for {@link java.lang.classfile.ClassModel} instances, given {@link com.tngtech.archunit.core.domain.JavaClass}
 * instances.
 *
 * @author Chris de Vreeze
 */
public record ClassModelParser(ClassFile classFile) {

    public ClassModel parseClassModel(JavaClass javaClass) {
        if (isJavaSeClass(javaClass)) {
            return parseJavaSeModuleClass(javaClass.getFullName());
        }

        Optional<Source> sourceOption = javaClass.getSource();

        return sourceOption
                .flatMap(source -> {
                    try (InputStream is = source.getUri().toURL().openStream()) {
                        byte[] bytes = is.readAllBytes();
                        return Optional.of(classFile.parse(bytes));
                    } catch (IOException | RuntimeException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow();
    }

    public ClassModel parseJavaSeModuleClass(String className) {
        return parseJdkModuleClass("java.se", className);
    }

    public ClassModel parseJdkModuleClass(String moduleName, String className) {
        try {
            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path classFilePath = fs.getPath("modules", moduleName, className);

            return classFile().parse(classFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isJavaSeClass(JavaClass javaClass) {
        ModuleReference javaSeModuleRef = ModuleFinder.ofSystem().find("java.se").orElseThrow();
        Set<String> packages = javaSeModuleRef.descriptor().packages();
        return packages.contains(javaClass.getPackage().getName());
    }
}
