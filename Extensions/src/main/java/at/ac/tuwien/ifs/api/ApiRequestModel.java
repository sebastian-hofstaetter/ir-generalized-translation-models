package at.ac.tuwien.ifs.api;

/**
 * Internal use only, needed to convert json to lucene term format
 */
public class ApiRequestModel {
    public String[] terms;

    public String vector_method;
    public String similarity_method;
    public String filter_method;
    public String filter_value;

    ApiRequestModel(String[] terms, String vector_method, String similarity_method, String filter_method, String filter_value) {
        this.terms = terms;
        this.vector_method = vector_method;
        this.similarity_method = similarity_method;
        this.filter_method = filter_method;
        this.filter_value = filter_value;
    }
}
