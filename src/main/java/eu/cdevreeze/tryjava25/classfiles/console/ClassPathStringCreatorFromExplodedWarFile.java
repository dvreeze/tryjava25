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

import eu.cdevreeze.tryjava25.classfiles.parse.ClassModelParser;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Simple program creating a classpath string from an exploded WAR file directory. That directory is the
 * only program argument.
 *
 * @author Chris de Vreeze
 */
public class ClassPathStringCreatorFromExplodedWarFile {

    static void main(String... args) {
        Objects.checkIndex(0, args.length);
        Path explodedWarRootDir = Path.of(args[0]);

        String classPathString = ClassModelParser.createClassPathStringFromExplodedWarDirectory(explodedWarRootDir);

        System.out.println(classPathString);
    }
}
