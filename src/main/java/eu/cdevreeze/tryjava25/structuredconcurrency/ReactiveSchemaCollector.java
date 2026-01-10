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

package eu.cdevreeze.tryjava25.structuredconcurrency;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import eu.cdevreeze.yaidom4j.dom.immutabledom.Document;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentParser;
import eu.cdevreeze.yaidom4j.dom.immutabledom.jaxpinterop.DocumentParsers;
import eu.cdevreeze.yaidom4j.jaxp.SaxParsers;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.SAXParserFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static eu.cdevreeze.yaidom4j.dom.immutabledom.ElementPredicates.hasName;

/**
 * Naive XML schema collector, following imports (not includes). It knows nothing about XML catalogs.
 * <p>
 * It uses {@link CompletableFuture}'s instead of structured concurrency (preview feature).
 * Hence, this program is written in a "functional reactive programming" style, somewhat reminiscent of
 * the functional reactive programming style offered by Scala libraries such as ZIO, Cats Effect and Monix,
 * but far less sophisticated and also less pure (because Futures as "program recipes" are not immutable values).
 * <p>
 * This "functional reactive programming" style comes with a some restrictions that must be kept in mind,
 * especially if we are used to a more imperative style of programming (which becomes attractive again with
 * structured concurrency, which in Java 25 is still a preview feature).
 * <p>
 * First of all, if we create `CompletableFuture` instances but fail to connect them, some of them may become
 * no-ops. Note that `CompletableFuture` instances are typically chained using methods such as "thenCompose",
 * which is like a "flatMap" higher-order function in monadic data structures (such as collections, ZIO effects
 * etc.). Also be careful not to combine "eager" and "lazy" code (the latter as `CompletableFuture`'s) where
 * we want chains of `CompletableFuture` instances.
 * <p>
 * Second, make sure that code that must run within a single thread indeed runs in a single thread.
 * An example could be code running within a transactional JPA/Hibernate EntityManager/Session.
 * `CompletableFuture` chains typically use multiple threads (depending on the "Executor"'s used), thus
 * violating single-threaded code execution.
 * <p>
 * For a deep introduction to `CompletableFuture`, see
 * <a href="https://concurrencydeepdives.com/guide-completable-future/">Guide to CompletableFuture</a>.
 *
 * @author Chris de Vreeze
 */
public class ReactiveSchemaCollector {

    private static final String NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    private static final QName SCHEMA_QNAME = new QName(NS, "schema");
    private static final QName IMPORT_QNAME = new QName(NS, "import");

    private static final QName SCHEMA_LOCATION_QNAME = new QName("schemaLocation");

    private static final int MAX_DEPTH = 100;

    private static final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private ReactiveSchemaCollector() {
    }

    public static CompletableFuture<ImmutableList<Document>> collectSchemaTask(URI startSchemaUrl) {
        return collectSchemaTask(parseSchema(startSchemaUrl), MAX_DEPTH);
    }

    private static CompletableFuture<ImmutableList<Document>> collectSchemaTask(Document schema, int maxDepth) {
        Preconditions.checkArgument(maxDepth > 0, "max recursion depth <= 0 not allowed");

        System.out.printf("Current thread: %s; calling 'collectSchemaTask'; maxDepth: %d%n", Thread.currentThread(), maxDepth);

        return collectImportsTask(schema)
                .thenApplyAsync(ReactiveSchemaCollector::deduplicate)
                .thenComposeAsync(directlyImportedDocs -> {
                    if (directlyImportedDocs.isEmpty()) {
                        return CompletableFuture.completedFuture(ImmutableList.of(schema));
                    } else {
                        List<CompletableFuture<ImmutableList<Document>>> futures = new ArrayList<>();
                        futures.add(CompletableFuture.completedFuture(ImmutableList.of(schema)));

                        // Recursion, used in this method that returns a CompletableFuture.
                        // That is, this method recursively creates a CompletableFuture chain!

                        for (Document directlyImportedNewDoc : directlyImportedDocs) {
                            futures.add(collectSchemaTask(directlyImportedNewDoc, maxDepth - 1));
                        }

                        return combine(futures);
                    }
                })
                .thenApplyAsync(ReactiveSchemaCollector::deduplicate);
    }

    private static CompletableFuture<ImmutableList<Document>> collectImportsTask(Document schema) {
        return findImportsTask(schema)
                .thenComposeAsync(uris -> {
                    List<CompletableFuture<ImmutableList<Document>>> futures =
                            uris.stream().map(u -> parseSchemaTask(u).thenApply(ImmutableList::of)).toList();
                    return combine(futures);
                })
                .thenApplyAsync(ReactiveSchemaCollector::deduplicate);
    }

    private static CompletableFuture<Document> parseSchemaTask(URI schemaUrl) {
        return CompletableFuture.supplyAsync(() -> parseSchema(schemaUrl), virtualThreadExecutor);
    }

    private static CompletableFuture<ImmutableList<URI>> findImportsTask(Document schema) {
        return CompletableFuture.supplyAsync(() -> findImports(schema));
    }

    private static Document parseSchema(URI schemaUrl) {
        System.out.printf("Current thread: %s; parsing document URL: %s%n", Thread.currentThread(), schemaUrl);
        return getDocumentParser().parse(schemaUrl);
    }

    private static ImmutableList<URI> findImports(Document schema) {
        Preconditions.checkArgument(schema.documentElement().name().equals(SCHEMA_QNAME));

        return schema.documentElement()
                .childElementStream(hasName(IMPORT_QNAME))
                .map(e -> e.attribute(SCHEMA_LOCATION_QNAME))
                .map(u -> schema.uriOption().map(b -> b.resolve(u)).orElseThrow())
                .distinct()
                .collect(ImmutableList.toImmutableList());
    }

    private static DocumentParser getDocumentParser() {
        // Expensive if called many times, but that does avoid thread-safety issues
        // Scoped values (or "legacy" thread-locals) do not help for virtual threads, since virtual threads are not pooled

        SAXParserFactory spf = SaxParsers.newNonValidatingSaxParserFactory();
        return DocumentParsers.builder(spf).removingInterElementWhitespace().build();
    }

    private static ImmutableList<Document> deduplicate(List<Document> docs) {
        return docs.stream()
                .gather(MyGatherers.distinctBy(doc -> doc.uriOption().orElseThrow()))
                .collect(ImmutableList.toImmutableList());
    }

    private static CompletableFuture<ImmutableList<Document>> combine(List<CompletableFuture<ImmutableList<Document>>> futures) {
        return combine(
                futures,
                (docs1, docs2) -> ImmutableList.<Document>builder().addAll(docs1).addAll(docs2).build(),
                ImmutableList.of()
        );
    }

    private static <T> CompletableFuture<T> combine(
            List<CompletableFuture<T>> futures,
            BinaryOperator<T> fn,
            T defaultValue
    ) {
        // See https://www.baeldung.com/java-completablefuture-list-convert

        // Note that arrays of parameterized types are not allowed; hence, the wildcard
        // We need this array, though, to pass to method "CompletableFuture.allOf"
        // Yet, on the other hand, we still have the variable "futures" of the parameterized type when we need it
        CompletableFuture<?>[] futuresArray = futures.toArray(new CompletableFuture<?>[0]);

        return CompletableFuture.allOf(futuresArray)
                .thenApply(ignored ->
                        futures.stream().map(CompletableFuture::join).collect(Collectors.toList())
                )
                .thenApply(collection -> collection.stream().reduce(fn).orElse(defaultValue));
    }

    static void main(String[] args) throws InterruptedException, ExecutionException {
        Objects.checkIndex(0, args.length);
        URI uri = URI.create(args[0]);

        // Blocking call, waiting for the CompletableFuture chain to finish.
        List<Document> docs = collectSchemaTask(uri).get();

        System.out.println();
        docs.forEach(doc -> System.out.printf("Document URI: %s%n", doc.uriOption().orElseThrow()));
    }
}
