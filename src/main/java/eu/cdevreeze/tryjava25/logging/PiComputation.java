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

package eu.cdevreeze.tryjava25.logging;

import module java.base;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computation of Pi, using the Leibniz Formula. This program is more about showing (JPMS-enabled) logging
 * using Slf4j 2, with the logger implementation provided as an implementation of service
 * "org.slf4j.spi.SLF4JServiceProvider".
 *
 * @author Chris de Vreeze
 */
public class PiComputation {

    private static final Logger logger = LoggerFactory.getLogger(PiComputation.class);

    static void main(String[] args) {
        Objects.checkIndex(0, args.length);
        int numberOfIterations = Integer.parseInt(args[0]);

        double computedPi = pi(numberOfIterations);
        logger.atInfo()
                .setMessage("PI according to Leibniz formula ({} iterations): {}")
                .addArgument(numberOfIterations)
                .addArgument(computedPi)
                .log();

        double jdkPi = Math.PI;
        logger.atInfo()
                .setMessage("PI according to the JDK: {}")
                .addArgument(jdkPi)
                .log();

        logger.atInfo()
                .setMessage("Module path: {}")
                .addArgument(System.getProperty("jdk.module.path"))
                .log();
        logger.atInfo()
                .setMessage("Class path: {}")
                .addArgument(System.getProperty("java.class.path"))
                .log();
    }

    public static double pi(int numberOfIterations) {
        return IntStream.range(0, numberOfIterations)
                .mapToDouble(PiComputation::term)
                .boxed()
                .gather(Gatherers.fold(() -> 0.0, Double::sum))
                .findFirst()
                .orElseThrow();
    }

    private static double term(int iterationIndex) {
        Preconditions.checkArgument(iterationIndex >= 0);

        double resultWithoutSign = 4.0 / (2 * iterationIndex + 1);
        return iterationIndex % 2 == 0 ? resultWithoutSign : 0.0 - resultWithoutSign;
    }
}
