package at.ac.tuwien.ifs.query;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;

/**
 * Contains integration tests for the <code>{@link AugmentedTermQuery}</code>
 * with randomized lucene indices & real similarity classes
 */
public class AugmentedTermQueryIntegrationTest extends LuceneTestCase
{

    public void test_AugmentedTermQuery_IndexIntegration_GT() throws IOException {

        // arrange lucene index
        Directory dir = newDirectory();
        MockAnalyzer analyzer = new MockAnalyzer(random());

        RandomIndexWriter w = new RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(new MockAnalyzer(random()))
                    .setMergePolicy(newLogMergePolicy())
                    .setSimilarity(new BM25Similarity()
                ));

        String[] docs = new String[] {
                "bla",
                "universe bla bla",
                "universe world infinity",
                "universe world bla",
        };
        for (int i = 0; i < docs.length; i++) {
            Document doc = new Document();
            doc.add(newStringField("id", "" + i, Field.Store.YES));
            doc.add(newTextField("field", docs[i], Field.Store.NO));
            w.addDocument(doc);
        }

        w.forceMerge(1);

        IndexReader r = w.getReader();
        IndexSearcher s = newSearcher(r);

        Term searchTerm = new Term("field","universe");
        Term searchTerm_2 = new Term("field","world");
        Term searchTerm_3 = new Term("field","infinity");


        TermWeightTuple[] termWeightTuples = {
                new TermWeightTuple(searchTerm_2, .5f),
                new TermWeightTuple(searchTerm_3, .3f)};

        // basic test - only main term
        {
            AugmentedTermQuery testQuery = new AugmentedTermQuery(AugmentedTermQuery.ModelMethod.Generalized, searchTerm,new TermWeightTuple[]{});
            TopDocs searchResults = s.search(testQuery, 10);

            assertEquals(3, searchResults.totalHits);

            assertEquals(searchResults.scoreDocs[0].score,searchResults.scoreDocs[1].score,0.0001);
            assertEquals(searchResults.scoreDocs[1].score,searchResults.scoreDocs[2].score,0.0001);
        }

        // main term + 1 similar term
        {
            // search for: universe + 0.5 * world
            AugmentedTermQuery testQuery = new AugmentedTermQuery(AugmentedTermQuery.ModelMethod.Generalized, searchTerm,new TermWeightTuple[]{termWeightTuples[0]});
            TopDocs searchResults = s.search(testQuery, 10);

            assertEquals(3, searchResults.totalHits);

            assertEquals(searchResults.scoreDocs[0].score,searchResults.scoreDocs[1].score,0.0001);

            assertTrue(searchResults.scoreDocs[0].doc == 2 || searchResults.scoreDocs[0].doc == 3);
            assertTrue(searchResults.scoreDocs[1].doc == 2 || searchResults.scoreDocs[1].doc == 3);

            assertEquals(1, searchResults.scoreDocs[2].doc);
        }

        // main term + 2 similar terms
        {
            // search for: universe + 0.5 * world + 0.3 * infinity
            AugmentedTermQuery testQuery = new AugmentedTermQuery(AugmentedTermQuery.ModelMethod.Generalized, searchTerm, termWeightTuples);
            TopDocs searchResults = s.search(testQuery, 10);

            assertEquals(3, searchResults.totalHits);

            assertEquals(2, searchResults.scoreDocs[0].doc);
            assertEquals(3, searchResults.scoreDocs[1].doc);
            assertEquals(1, searchResults.scoreDocs[2].doc);
        }

        IOUtils.close(r, w, dir, analyzer);
    }

    public void test_AugmentedTermQuery_IndexIntegration_ET() throws IOException {

        // arrange lucene index
        Directory dir = newDirectory();
        MockAnalyzer analyzer = new MockAnalyzer(random());

        RandomIndexWriter w = new RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig(new MockAnalyzer(random()))
                        .setMergePolicy(newLogMergePolicy())
                        .setSimilarity(new BM25Similarity()
                        ));

        String[] docs = new String[] {
                "bla",
                "universe bla bla",
                "universe world infinity",
                "universe world bla",
        };
        for (int i = 0; i < docs.length; i++) {
            Document doc = new Document();
            doc.add(newStringField("id", "" + i, Field.Store.YES));
            doc.add(newTextField("field", docs[i], Field.Store.NO));
            w.addDocument(doc);
        }

        w.forceMerge(1);

        IndexReader r = w.getReader();
        IndexSearcher s = newSearcher(r);

        Term searchTerm = new Term("field","universe");
        Term searchTerm_2 = new Term("field","world");
        Term searchTerm_3 = new Term("field","infinity");


        TermWeightTuple[] termWeightTuples = {
                new TermWeightTuple(searchTerm_2, .5f),
                new TermWeightTuple(searchTerm_3, .3f)};

        // basic test - only main term
        {
            AugmentedTermQuery testQuery = new AugmentedTermQuery(AugmentedTermQuery.ModelMethod.Extended, searchTerm,new TermWeightTuple[]{});
            TopDocs searchResults = s.search(testQuery, 10);

            assertEquals(3, searchResults.totalHits);

            assertEquals(searchResults.scoreDocs[0].score,searchResults.scoreDocs[1].score,0.0001);
            assertEquals(searchResults.scoreDocs[1].score,searchResults.scoreDocs[2].score,0.0001);
        }

        // main term + 1 similar term
        {
            // search for: universe + 0.5 * world
            AugmentedTermQuery testQuery = new AugmentedTermQuery(AugmentedTermQuery.ModelMethod.Extended, searchTerm,new TermWeightTuple[]{termWeightTuples[0]});
            TopDocs searchResults = s.search(testQuery, 10);

            assertEquals(3, searchResults.totalHits);

            assertEquals(searchResults.scoreDocs[0].score,searchResults.scoreDocs[1].score,0.0001);

            assertTrue(searchResults.scoreDocs[0].doc == 2 || searchResults.scoreDocs[0].doc == 3);
            assertTrue(searchResults.scoreDocs[1].doc == 2 || searchResults.scoreDocs[1].doc == 3);

            assertEquals(1, searchResults.scoreDocs[2].doc);
        }

        // main term + 2 similar terms
        {
            // search for: universe + 0.5 * world + 0.3 * infinity
            AugmentedTermQuery testQuery = new AugmentedTermQuery(AugmentedTermQuery.ModelMethod.Extended, searchTerm, termWeightTuples);
            TopDocs searchResults = s.search(testQuery, 10);

            assertEquals(3, searchResults.totalHits);
        }

        IOUtils.close(r, w, dir, analyzer);
    }
}
