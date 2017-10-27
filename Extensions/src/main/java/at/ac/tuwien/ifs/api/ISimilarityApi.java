package at.ac.tuwien.ifs.api;

import java.io.IOException;

/**
 * Describes the abstract interaction layer of the similarity api
 */
public interface ISimilarityApi {

    /**
     * Makes a call to the api and returns a complete term -> similarTerms[] structure
     * that can be used to create lucene queries
     *
     * The field value is only used to create the lucene terms, it is not used in the api call
     */
    SimilarTermModel[] GetSimilarTerms(String field, String[] queryTerms) throws IOException;

}
