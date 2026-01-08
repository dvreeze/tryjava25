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
 *
 * @author Chris de Vreeze
 */
public class ReactiveSchemaCollector {

    private static final String NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    private static final QName SCHEMA_QNAME = new QName(NS, "schema");
    private static final QName IMPORT_QNAME = new QName(NS, "import");

    private static final QName SCHEMA_LOCATION_QNAME = new QName("schemaLocation");

    private static final int MAX_DEPTH = 100;

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private ReactiveSchemaCollector() {
    }

    public static CompletableFuture<ImmutableList<Document>> collectSchemaTask(URI startSchemaUrl) {
        return collectSchemaTask(parseSchema(startSchemaUrl), MAX_DEPTH);
    }

    private static CompletableFuture<ImmutableList<Document>> collectSchemaTask(Document schema, int maxDepth) {
        Preconditions.checkArgument(maxDepth > 0, "max recursion depth <= 0 not allowed");

        return collectImportsTask(schema)
                .thenApply(ReactiveSchemaCollector::deduplicate)
                .thenCompose(directlyImportedDocs -> {
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
                .thenApply(ReactiveSchemaCollector::deduplicate);
    }

    private static CompletableFuture<ImmutableList<Document>> collectImportsTask(Document schema) {
        return findImportsTask(schema)
                .thenCompose(uris -> {
                    List<CompletableFuture<ImmutableList<Document>>> futures =
                            uris.stream().map(u -> parseSchemaTask(u).thenApply(ImmutableList::of)).toList();
                    return combine(futures);
                })
                .thenApply(ReactiveSchemaCollector::deduplicate);
    }

    private static CompletableFuture<Document> parseSchemaTask(URI schemaUrl) {
        return CompletableFuture.supplyAsync(() -> parseSchema(schemaUrl), executor);
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
