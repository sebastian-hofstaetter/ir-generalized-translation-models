package at.ac.tuwien.ifs.parser;

import at.ac.tuwien.ifs.api.SimilarityApiMock;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.AbstractSolrTestCase;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import at.ac.tuwien.ifs.query.AugmentedTermQuery;
import at.ac.tuwien.ifs.query.TermWeightTuple;

/**
 * Unit tests, that check the correct "query string" -> "lucene augmented query" behavior
 * of the <code>{@link SimilarityParser}</code>
 */
public class SimilarityParserTest extends AbstractSolrTestCase {

    private static ModifiableSolrParams mockCorrectParams;

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

        mockCorrectParams = new ModifiableSolrParams();
        mockCorrectParams.add("api:type","mock");
        mockCorrectParams.add("query:method","GT");
        mockCorrectParams.add("df","text");
        mockCorrectParams.add("defType","similarityApiParser");
    }


    @Test(expected = RuntimeException.class)
    public void test_missingParamsCompletely() throws SyntaxError {

        // arrange
        ModifiableSolrParams emptyParams = new ModifiableSolrParams();

        // act - expect: exception
        QParser parser = new SimilarityParser("query",new ModifiableSolrParams(), emptyParams, req("query"));
    }

    @Test(expected = RuntimeException.class)
    public void test_missingParamsUrl() throws SyntaxError {

        // arrange
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.add("api:type","real");
        // but missing api:url

        //act
        QParser parser = new SimilarityParser("query",new ModifiableSolrParams(),params,req("query"));
    }

    @Test
    public void test_singleTerm() throws SyntaxError {

        // arrange
        QParser parser = new SimilarityParser("first",new ModifiableSolrParams(),mockCorrectParams,req("first"));

        // act
        Query luceneQuery = parser.parse();

        // assert
        AugmentedTermQuery expected = new AugmentedTermQuery(
                AugmentedTermQuery.ModelMethod.Generalized, new Term("text", "first"),
                new TermWeightTuple[]{SimilarityApiMock.similarTerm("text")}
                );

        Assert.assertEquals(expected, luceneQuery);
    }

    @Test
    public void test_twoTerm() throws SyntaxError {

        // arrange
        QParser parser = new SimilarityParser("first second",new ModifiableSolrParams(),mockCorrectParams,req("first second"));

        // act
        Query luceneQuery = parser.parse();

        // assert
        Assert.assertEquals(BooleanQuery.class, luceneQuery.getClass());
        BooleanQuery realQuery = (BooleanQuery)luceneQuery;
        Assert.assertEquals(2,realQuery.clauses().size());

        // first
        AugmentedTermQuery expected1 = new AugmentedTermQuery(
                AugmentedTermQuery.ModelMethod.Generalized, new Term("text", "first"),
                new TermWeightTuple[]{SimilarityApiMock.similarTerm("text")}
        );
        Assert.assertEquals(expected1,realQuery.clauses().get(0).getQuery());

        //second
        AugmentedTermQuery expected2 = new AugmentedTermQuery(
                AugmentedTermQuery.ModelMethod.Generalized, new Term("text", "second"),
                new TermWeightTuple[]{SimilarityApiMock.similarTerm("text")}
        );
        Assert.assertEquals(expected2,realQuery.clauses().get(1).getQuery());

    }
}
