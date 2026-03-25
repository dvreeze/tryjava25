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

package eu.cdevreeze.tryjava25.classfiles.console;

import module java.base;
import com.google.common.collect.ImmutableList;
import eu.cdevreeze.tryjava25.classfiles.parse.ClassModelParser;
import eu.cdevreeze.tryjava25.classfiles.parse.ClassUniverse;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Element;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Nodes;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinter;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentPrinters;

import javax.xml.namespace.QName;

/**
 * Program that finds all supertypes (or self) of an interface or class.
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

    private final ClassUniverse classUniverse;

    public SupertypesFinder(ClassUniverse classUniverse) {
        this.classUniverse = Objects.requireNonNull(classUniverse);
    }

    public ImmutableList<ClassModel> findAllSupertypesOrSelf(String className) {
        int idx = className.lastIndexOf('.');
        String packageName = idx < 0 ? "" : className.substring(0, idx);
        String simpleClassName = idx < 0 ? className : className.substring(idx + 1);
        ClassDesc classDesc = ClassDesc.of(packageName, simpleClassName);

        return findAllSupertypesOrSelf(classDesc);
    }

    public ImmutableList<ClassModel> findAllSupertypesOrSelf(ClassDesc classDesc) {
        ClassModel cls = classUniverse.resolveClass(classDesc);

        return classUniverse.findAllSupertypesOrSelf(cls);
    }

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        String className = args[0];

        String inspectionClasspath = System.getProperty("inspectionClasspath");
        Objects.requireNonNull(inspectionClasspath);

        ClassModelParser classModelParser = new ClassModelParser(ClassFile.of());
        ClassUniverse classUniverse = new ClassUniverse(classModelParser.parseClassPath(inspectionClasspath));

        SupertypesFinder supertypesFinder = new SupertypesFinder(classUniverse);

        ImmutableList<ClassModel> supertypesOrSelf = supertypesFinder.findAllSupertypesOrSelf(className);

        Element rootElem = Nodes.elem(new QName("superTypesOrSelf"))
                .plusAttribute(new QName("type"), className)
                .plusChildren(
                        supertypesOrSelf.stream()
                                .map(tpe -> Nodes.elem("type").plusText(tpe.thisClass().asSymbol().descriptorString()))
                                .collect(ImmutableList.toImmutableList())
                );

        DocumentPrinter docPrinter = DocumentPrinters.instance();
        String xml = docPrinter.print(rootElem);
        System.out.println(xml);
    }
}
