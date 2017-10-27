package at.ac.tuwien.ifs.parser;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;

/**
 * Solr plugin to use the <code>{@link SimilarityParser}</code> for query parsing
 */
public class SimilarityParserPlugin extends QParserPlugin {

    public static final String NAME = "similarityApiParser";

    public QParser createParser(String s, SolrParams localParams, SolrParams solrParams, SolrQueryRequest solrQueryRequest) {
        return new SimilarityParser(s,localParams,solrParams,solrQueryRequest);
    }
}