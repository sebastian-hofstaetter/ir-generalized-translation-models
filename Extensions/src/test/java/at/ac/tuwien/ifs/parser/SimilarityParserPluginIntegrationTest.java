package at.ac.tuwien.ifs.parser;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.BasicResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocSlice;
import org.apache.solr.util.AbstractSolrTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import at.ac.tuwien.ifs.query.AugmentedTermQuery;

/**
 * Integration tests for <code>{@link SimilarityParser}</code> function in the context of a running
 * and configured solr instance. based on resources/solrconfig.xml & resources/schema.xml
 */
public class SimilarityParserPluginIntegrationTest extends AbstractSolrTestCase {

    private static EmbeddedSolrServer server;

    @BeforeClass
    public static void beforeTests() throws Exception {

        // This is relative to the current working directory
        // when running the tests with intellij the current working directory is: the main Extensions folder
        // THIS could/will break test runs with other working directories !
        // - resource folder has to be set explicitly, otherwise the base class takes the relative "solr/collection1"
        //   and will not find it ...
        initCore("solrconfig.xml",
                "schema.xml",
                "src\\test\\resources\\");
        CoreContainer coreContainer = h.getCoreContainer();
        server = new EmbeddedSolrServer(coreContainer, "collection1" );

    }

    @AfterClass
    public static void afterTests() throws Exception{
        server.close();
    }

    /**
     * Basic test that checks that the "/similarity-query"
     * defined in the resources/solrconfig.xml does set the query parser
     * to the <code>{@link SimilarityParserPlugin}</code>
     */
    public void test_QParserGetsCalled() throws Exception {

        // arrange
        SolrQueryRequest queryRequest = req("testQueryString");

        // act
        SolrQueryResponse resp = h.queryAndResponse("/similarity-query", queryRequest);

        // assert - the only way to check that the similarity parser was used is to check
        //          the type of the query returned by the similarity parser (for a single term): AugmentedTermQuery
        BasicResultContext basicResultContext = (BasicResultContext)resp.getResponse();
        Query usedLuceneQuery = basicResultContext.getQuery();
        assertTrue(usedLuceneQuery instanceof AugmentedTermQuery);

        // cleanup
        queryRequest.close();
    }

    /**
     * Basic test that checks that the "/mlt"
     * defined in the resources/solrconfig.xml does match the documents inserted
     */
    public void test_QParserGetsCalled_LikeThis() throws Exception {


        SolrInputDocument newDoc = new SolrInputDocument();
        newDoc.addField("id", "c2eb8869-f70c-4164-a79f-ee1579b2d124");
        newDoc.addField("text", "Hello world fill fill");
        server.add(newDoc);

        newDoc = new SolrInputDocument();
        newDoc.addField("id", "c2eb8869-f70c-4164-a79f-ee1579b2d125");
        newDoc.addField("text", "Hello world bla bla");
        server.add(newDoc);
        server.commit();

        // arrange
        SolrQueryRequest queryRequest = req("hello world");

        // act
        SolrQueryResponse resp = h.queryAndResponse("/mlt", queryRequest);

        // assert - the only way to check that the similarity parser was used is to check
        //          that bla bla has a higher score (mock api similar word)
        DocSlice match = (DocSlice) resp.getValues().get("match");

        assertEquals(2, match.matches());
        assertEquals(1, match.iterator().nextDoc()); // match id 1 because of bla bla (from the mock api)

        // cleanup
        queryRequest.close();
    }

    /**
     * Checks that two terms are parsed and 2 <code>{@link AugmentedTermQuery}</code> inside
     * 1 <code>{@link org.apache.lucene.search.BooleanQuery}</code> are returned.
     * The schema.xml must define an analyzer for the default field defined in solrconfig.xml
     */
    public void test_QParserTwoTerms() throws Exception {

        // arrange
        SolrQueryRequest queryRequest = req("good days");

        // act
        SolrQueryResponse resp = h.queryAndResponse("/similarity-query", queryRequest);

        // assert - the only way to check that the similarity parser was used is to check
        //          the type of the query returned by the similarity parser (for a single term): AugmentedTermQuery
        BasicResultContext basicResultContext = (BasicResultContext)resp.getResponse();
        Query usedLuceneQuery = basicResultContext.getQuery();
        assertTrue(usedLuceneQuery instanceof BooleanQuery);
        BooleanQuery booleanQuery = (BooleanQuery) usedLuceneQuery;

        assertEquals(2, booleanQuery.clauses().size());
        assertTrue(booleanQuery.clauses().get(0).getQuery() instanceof AugmentedTermQuery);
        assertTrue(booleanQuery.clauses().get(1).getQuery() instanceof AugmentedTermQuery);

        // cleanup
        queryRequest.close();
    }

    /**
     * Checks that two terms are parsed and 2 <code>{@link AugmentedTermQuery}</code> inside
     * 1 <code>{@link org.apache.lucene.search.BooleanQuery}</code> are returned.
     * The schema.xml must define an analyzer for the default field defined in solrconfig.xml
     */
    public void test_QParserTwoTerms_ET() throws Exception {

        // arrange
        SolrQueryRequest queryRequest = req("good days");

        // act
        SolrQueryResponse resp = h.queryAndResponse("/similarity-query-et", queryRequest);

        // assert - the only way to check that the similarity parser was used is to check
        //          the type of the query returned by the similarity parser (for a single term): AugmentedTermQuery
        BasicResultContext basicResultContext = (BasicResultContext)resp.getResponse();
        Query usedLuceneQuery = basicResultContext.getQuery();
        assertTrue(usedLuceneQuery instanceof BooleanQuery);
        BooleanQuery booleanQuery = (BooleanQuery) usedLuceneQuery;

        assertEquals(2, booleanQuery.clauses().size());
        assertTrue(booleanQuery.clauses().get(0).getQuery() instanceof AugmentedTermQuery);
        assertTrue(booleanQuery.clauses().get(1).getQuery() instanceof AugmentedTermQuery);

        // cleanup
        queryRequest.close();
    }
}
