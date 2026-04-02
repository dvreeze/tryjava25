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

package eu.cdevreeze.tryjava25.archunitplus.console;

import module java.base;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import eu.cdevreeze.tryjava25.archunitplus.parse.ClassUniverse;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.datatype.guava.GuavaModule;

/**
 * Program that finds all supertypes (or self) of an interface or class.
 * It combines the use of ArchUnit and the Java Class File API.
 * <p>
 * The only program argument is the fully qualified class name.
 * <p>
 * The following system property is used: "inspectionClasspath".
 * This is a classpath string, as output by Maven command "mvn dependency:build-classpath", preferably
 * enhanced with a directory containing the compilation output (such as "target/classes").
 *
 * @author Chris de Vreeze
 */
public class SupertypesFinder {

    public record SupertypesOrSelfResult(ClassDesc startType, ImmutableList<ClassDesc> superTypesOrSelf) {

        public static SupertypesOrSelfResult from(ClassDesc startType, ImmutableList<ClassModel> superTypesOrSelf) {
            return new SupertypesOrSelfResult(
                    startType,
                    superTypesOrSelf.stream().map(v -> v.thisClass().asSymbol()).collect(ImmutableList.toImmutableList())
            );
        }
    }

    private final JavaClasses javaClasses;

    public SupertypesFinder(JavaClasses javaClasses) {
        this.javaClasses = Objects.requireNonNull(javaClasses);
    }

    public ImmutableList<ClassModel> findAllSupertypesOrSelf(String className) {
        int idx = className.lastIndexOf('.');
        String packageName = idx < 0 ? "" : className.substring(0, idx);
        String simpleClassName = idx < 0 ? className : className.substring(idx + 1);
        ClassDesc classDesc = ClassDesc.of(packageName, simpleClassName);

        return findAllSupertypesOrSelf(classDesc);
    }

    public ImmutableList<ClassModel> findAllSupertypesOrSelf(ClassDesc classDesc) {
        ClassUniverse classUniverse = ClassUniverse.of(javaClasses);
        ClassModel cls = classUniverse.resolveClass(classDesc);

        return classUniverse.findAllSupertypesOrSelf(cls);
    }

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        String className = args[0];

        String inspectionClasspath = System.getProperty("inspectionClasspath");
        Objects.requireNonNull(inspectionClasspath);
        String[] paths = inspectionClasspath.split(":");

        ClassFileImporter classFileImporter = new ClassFileImporter();
        // Expensive call
        JavaClasses javaClasses = classFileImporter.importPaths(paths);

        SupertypesFinder supertypesFinder = new SupertypesFinder(javaClasses);

        ImmutableList<ClassModel> supertypesOrSelf = supertypesFinder.findAllSupertypesOrSelf(className);

        int idx = className.lastIndexOf('.');
        Preconditions.checkState(idx > 0);
        String packageName = className.substring(0, idx);
        String simpleClassName = className.substring(idx + 1);
        ClassDesc startType = ClassDesc.of(packageName, simpleClassName);
        SupertypesOrSelfResult supertypesOrSelfResult = SupertypesOrSelfResult.from(startType, supertypesOrSelf);

        JsonMapper jsonMapper = JsonMapper.builder()
                .addModule(new GuavaModule())
                .addModule(createSimpleModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        String resultJson = jsonMapper.writeValueAsString(supertypesOrSelfResult);
        System.out.println(resultJson);
    }

    private static final class SupertypesOrSelfResultSerializer extends StdSerializer<SupertypesOrSelfResult> {

        public SupertypesOrSelfResultSerializer() {
            this(null);
        }

        public SupertypesOrSelfResultSerializer(@Nullable Class<SupertypesOrSelfResult> t) {
            super(t);
        }

        @Override
        public void serialize(SupertypesOrSelfResult value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeStringProperty("startType", value.startType().descriptorString());
            gen.writeArrayPropertyStart("superTypesOrSelf");
            value.superTypesOrSelf().forEach(superTypeOrSelf -> {
                gen.writeString(superTypeOrSelf.descriptorString());
            });
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }

    /**
     * {@link SimpleModule} to be registered with the {@link tools.jackson.databind.json.JsonMapper}.
     */
    private static SimpleModule createSimpleModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(SupertypesOrSelfResult.class, new SupertypesOrSelfResultSerializer());
        return module;
    }
}
