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

import module java.base;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

/**
 * Utility methods to parse class files into {@link ClassModel} objects.
 * <p>
 * The typical way to parse class files into {@link ClassModel} objects when introspecting a Maven project
 * is to first do a clean build of that project with "mvn clean install", followed by "mvn dependency:build-classpath".
 * The latter classpath string should then be used as input to this {@link ClassModelParser}. Note that a full
 * "class model universe" includes that classpath, but also the project compilation output and module "java.se".
 * The latter are only "loaded" on demand, so mostly out of scope for this class.
 *
 * @author Chris de Vreeze
 */
public record ClassModelParser(ClassFile classFile) {

    public ClassModel parseClassFile(Path file) {
        try {
            Preconditions.checkArgument(isClassFile(file));

            return classFile.parse(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parses a class file as JAR entry into a {@link ClassModel}. Neither parameter is closed afterward.
     */
    public ClassModel parseJarEntry(JarEntry jarEntry, JarFile jarFile) {
        try {
            Preconditions.checkArgument(jarEntry.getName().endsWith(".class"));

            // See https://www.baeldung.com/convert-input-stream-to-array-of-bytes
            byte[] classBytes = ByteStreams.toByteArray(jarFile.getInputStream(jarEntry));
            return classFile.parse(classBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ImmutableMap<ClassDesc, ClassModel> parseExplodedDirectory(Path directory) {
        Preconditions.checkArgument(Files.exists(directory));

        try (Stream<Path> fileStream = Files.walk(directory)) {
            return fileStream
                    .filter(Files::isRegularFile)
                    .filter(this::isClassFile)
                    .map(this::parseClassFile)
                    .collect(ImmutableMap.toImmutableMap(c -> c.thisClass().asSymbol(), c -> c));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ImmutableMap<ClassDesc, ClassModel> parseJarFile(Path jarFile) {
        Preconditions.checkArgument(Files.isRegularFile(jarFile));
        Preconditions.checkArgument(jarFile.getFileName().toString().endsWith(".jar"));

        try (JarFile jar = new JarFile(jarFile.toFile());
             Stream<JarEntry> jarEntryStream = jar.versionedStream()) {

            return jarEntryStream
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .map(entry -> parseJarEntry(entry, jar))
                    .collect(ImmutableMap.toImmutableMap(c -> c.thisClass().asSymbol(), c -> c));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    public ClassModel parseJavaSeModuleClass(String className) {
        return parseJdkModuleClass("java.se", className);
    }

    public ImmutableMap<ClassDesc, ClassModel> parseClassPath(String classPath) {
        // Expecting a Unix-style classpath string, using colons as separator instead of semicolons

        String colon = Pattern.quote(":");
        List<Path> cpEntries = Arrays.stream(classPath.split(colon)).map(Path::of).toList();

        ImmutableMap.Builder<ClassDesc, ClassModel> builder = ImmutableMap.builder();
        for (Path cpEntry : cpEntries) {
            if (Files.isDirectory(cpEntry)) {
                builder.putAll(parseExplodedDirectory(cpEntry));
            } else {
                Preconditions.checkState(Files.isRegularFile(cpEntry) && cpEntry.getFileName().toString().endsWith(".jar"));
                builder.putAll(parseJarFile(cpEntry));
            }
        }
        return builder.buildKeepingLast();
    }

    /**
     * Parses {@link ClassModel} instances from zero or more classpath strings combined.
     * This method is typically called on a Maven project by combining 2 classpath strings, one from Maven command
     * "mvn dependency:build-classpath", and the other from the "target/classes" directory containing the compilation
     * output.
     */
    public ImmutableMap<ClassDesc, ClassModel> parseClassPaths(List<String> classPaths) {
        // Expecting a Unix-style classpath string, using colons as separator instead of semicolons

        if (classPaths.isEmpty()) {
            return ImmutableMap.of();
        } else {
            return parseClassPath(String.join(":", classPaths));
        }
    }

    private boolean isClassFile(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".class");
    }
}
