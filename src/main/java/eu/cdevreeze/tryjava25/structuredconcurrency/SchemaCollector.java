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
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

import static eu.cdevreeze.yaidom4j.dom.immutabledom.ElementPredicates.hasName;

/**
 * Naive XML schema collector, following imports (not includes). It knows nothing about XML catalogs.
 * <p>
 * It uses structured concurrency (preview feature) and therefore it uses virtual threads under the hood.
 * <p>
 * At first, I tried to also use caching for parsed documents. This does not buy us much, however, if
 * we have many cores at our disposal (for increased concurrency). Caching could help for long-running
 * programs where the same documents are retrieved repeatedly. That's not the case here, though.
 * As a consequence, there may be much unnecessary reparsing of already parsed schema documents.
 * <p>
 * See <a href="https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html#GUID-AA992944-AABA-4CBC-8039-DE5E17DE86DB">structured concurrency</a>.
 * Also see <a href="https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html#GUID-DC4306FC-D6C1-4BCC-AECE-48C32C1A8DAA">virtual threads</a>
 * and <a href="https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html#GUID-8AEDDBE6-F783-4D77-8786-AC5A79F517C0">virtual thread adoption guide</a>.
 *
 * @author Chris de Vreeze
 */
public class SchemaCollector {

    private static final String NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    private static final QName SCHEMA_QNAME = new QName(NS, "schema");
    private static final QName IMPORT_QNAME = new QName(NS, "import");

    private static final QName SCHEMA_LOCATION_QNAME = new QName("schemaLocation");

    private static final int MAX_DEPTH = 100;

    private SchemaCollector() {
    }

    public static ImmutableList<Document> collectSchema(URI startSchemaUrl) throws InterruptedException {
        return collectSchema(parseSchema(startSchemaUrl), MAX_DEPTH);
    }

    private static ImmutableList<Document> collectSchema(Document schema, int maxDepth) throws InterruptedException {
        // May import the same schema documents multiple times
        Preconditions.checkArgument(maxDepth > 0, "max recursion depth <= 0 not allowed");

        List<Document> directlyImportedNewDocs = collectImports(schema)
                .stream()
                .gather(MyGatherers.distinctBy(doc -> doc.uriOption().orElseThrow()))
                .toList();

        if (directlyImportedNewDocs.isEmpty()) {
            return ImmutableList.of(schema);
        }

        List<Document> result = new ArrayList<>();
        result.add(schema);
        // Recursion
        for (Document directlyImportedNewDoc : directlyImportedNewDocs) {
            result.addAll(collectSchema(directlyImportedNewDoc, maxDepth - 1));
        }
        return result.stream()
                .gather(MyGatherers.distinctBy(doc -> doc.uriOption().orElseThrow()))
                .collect(ImmutableList.toImmutableList());
    }

    private static ImmutableList<Document> collectImports(Document schema) throws InterruptedException {
        // Using structured concurrency (preview feature)
        // That uses virtual threads under the hood, which is quite appropriate for the blocking subtasks below

        ImmutableList<URI> imports = findImports(schema);

        List<Callable<Document>> subtasks =
                imports.stream()
                        .map(u -> (Callable<Document>) () -> parseSchema(u))
                        .toList();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Document>allSuccessfulOrThrow())) {
            subtasks.forEach(scope::fork);
            return scope.join()
                    .map(StructuredTaskScope.Subtask::get)
                    .gather(MyGatherers.distinctBy(doc -> doc.uriOption().orElseThrow()))
                    .collect(ImmutableList.toImmutableList());
        }
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
        // Scoped values (or "legacy" thread-locals) do not help here, since virtual threads are not pooled

        SAXParserFactory spf = SaxParsers.newNonValidatingSaxParserFactory();
        return DocumentParsers.builder(spf).removingInterElementWhitespace().build();
    }

    static void main(String[] args) throws InterruptedException {
        Objects.checkIndex(0, args.length);
        URI uri = URI.create(args[0]);

        List<Document> docs = collectSchema(uri);

        System.out.println();
        docs.forEach(doc -> System.out.printf("Document URI: %s%n", doc.uriOption().orElseThrow()));
    }
}
