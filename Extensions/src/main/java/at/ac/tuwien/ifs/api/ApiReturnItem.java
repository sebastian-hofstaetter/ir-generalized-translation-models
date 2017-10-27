package at.ac.tuwien.ifs.api;

/**
 * Internal use only, needed to convert json to lucene term format
 */
public class ApiReturnItem {
    public String mainTerm;
    public String[] similarTerms;
    public float[] similarWeights;
}
