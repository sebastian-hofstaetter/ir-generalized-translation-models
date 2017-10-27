package at.ac.tuwien.ifs.parser;

import at.ac.tuwien.ifs.api.ISimilarityApi;
import at.ac.tuwien.ifs.api.SimilarTermModel;
import at.ac.tuwien.ifs.api.SimilarityApi;
import at.ac.tuwien.ifs.api.SimilarityApiMock;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SyntaxError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import at.ac.tuwien.ifs.query.AugmentedTermQuery;
import at.ac.tuwien.ifs.query.TermWeightTuple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Solr query parser, that utilizes the <code>{@link ISimilarityApi}</code>
 * and the <code>{@link AugmentedTermQuery}</code>
 *
 * <remarks>
 *     Usage: to be able to use this parser, you have to define the following params in the requestHandler:
 *     <code>

 <str name="query:method">GT</str> // GT or ET

 <str name="api:type">mock</str> // mock or real
 <str name="api:failOnNotConnected">true</str> // true or false
 <str name="api:url">https://localhost:5000</str> // the url of the real endpoint
 <str name="api:optionalParams">vector_method=we;similarity_method=cos;filter_method=threshold;filter_value=0.7</str> // optional parameter as url appendable string (see api spec for options)
 *     </code>
 * </remarks>
 */
public class SimilarityParser extends QParser {

    private static final String config_query_method = "query:method";

    private static final String config_api_type = "api:type";
    private static final String config_fail = "api:failOnNotConnected";
    private static final String config_url = "api:url";
    private static final String config_optionalParams = "api:optionalParams";
    private final AugmentedTermQuery.ModelMethod modelMethod;

    private ISimilarityApi similarityApi;
    private boolean failOnConnectionError = false;

    private final static Logger logger = LoggerFactory.getLogger(SimilarityParser.class);


    public SimilarityParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        super(qstr, localParams, params, req);

        //
        // create api access via parameters
        //
        if(!checkParams(params)){
            throw new RuntimeException("[SimilarityParser] Params missing in configuration");
        }
        if(params.get(config_fail) != null && params.getBool(config_fail)){
            failOnConnectionError = true;
        }

        if(params.get(config_api_type).equals("mock")){
            similarityApi = new SimilarityApiMock();
        }
        else {
            similarityApi = new SimilarityApi(params.get(config_url), params.get(config_optionalParams));
        }

        if(params.get(config_query_method).equals("GT")){
            modelMethod = AugmentedTermQuery.ModelMethod.Generalized;
        }else{
            modelMethod = AugmentedTermQuery.ModelMethod.Extended;
        }

        if(logger.isInfoEnabled()) {
            logger.info("Class initialized with: " + similarityApi.getClass().getSimpleName());
        }
    }

    public Query parse() throws SyntaxError {

        //
        // parse query string into terms -> use analyzer pipeline
        //

        final IndexSchema schema = req.getSchema();
        String defaultField = QueryParsing.getDefaultField(schema, getParam(CommonParams.DF));
        if(defaultField == null){ // todo ?? check if that is sensible to do
            defaultField = "*";
        }

        final Analyzer analyzer = schema.getQueryAnalyzer();
        TokenStream tokenStream = analyzer.tokenStream(defaultField, qstr);

        String[] queryTerms = null;
        try {
            queryTerms = termsFromTokenStream(tokenStream);
        } catch (IOException e) {
            throw new SyntaxError(e);
        }

        //
        // get similar terms through the api
        //
        SimilarTermModel[] similarTerms = null;

        try{
            similarTerms = similarityApi.GetSimilarTerms(defaultField, queryTerms);
        } catch (IOException e){
            if(failOnConnectionError){
                throw new RuntimeException(e);
            }
            logger.error("Exception while talking to the api @ " + params.get(config_url),e);

            //
            // fill the similar terms only with the query terms - to let the query continue to execute ...
            //
            similarTerms = new SimilarTermModel[queryTerms.length];
            for (int i = 0; i < queryTerms.length; i++) {
                similarTerms[i] = new SimilarTermModel(new Term(defaultField,queryTerms[i]), new TermWeightTuple[0]);
            }
        }

        //
        // create the lucene query
        //
        Query query;
        if(similarTerms.length == 1) {
            query = new AugmentedTermQuery(modelMethod, similarTerms[0].queryTerm, similarTerms[0].similarTerms);
        }else{

            BooleanQuery.Builder builder = new BooleanQuery.Builder();

            for (SimilarTermModel model : similarTerms) {
                builder.add(new AugmentedTermQuery(modelMethod, model.queryTerm, model.similarTerms), BooleanClause.Occur.SHOULD);
            }

            query = builder.build();
        }

        if(logger.isInfoEnabled()) {
            logger.info("Created query: " + query.toString());
        }

        return query;
    }

    private String[] termsFromTokenStream(TokenStream stream) throws IOException {

        List<String> outputTemp=new ArrayList<>();
        CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            outputTemp.add(charTermAttribute.toString());
        }
        stream.end();
        stream.close();

        return outputTemp.toArray(new String[0]);
    }

    private boolean checkParams(SolrParams params){

        if(params.get(config_query_method) == null){
            return false;
        }

        String type;
        if((type = params.get(config_api_type)) != null){
            if(type.equals("mock")){
                return true;
            }
            if(type.equals("real")) {
                if(params.get(config_url) != null){
                    return true;
                }
            }
        }

        return false;
    }
}
