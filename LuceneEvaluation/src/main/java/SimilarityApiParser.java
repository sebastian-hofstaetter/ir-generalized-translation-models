import at.ac.tuwien.ifs.api.ISimilarityApi;
import at.ac.tuwien.ifs.api.SimilarTermModel;
import at.ac.tuwien.ifs.api.SimilarityApi;
import at.ac.tuwien.ifs.query.AugmentedTermQuery;
import at.ac.tuwien.ifs.query.TermWeightTuple;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.benchmark.quality.QualityQuery;
import org.apache.lucene.benchmark.quality.QualityQueryParser;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * used to parse a query string into a lucene query - accesses the ISimilarity api for similarity information
 */
public class SimilarityApiParser implements QualityQueryParser {

    public enum Preprocessing {
        Tokenize,
        Stemmed
    }

    private ISimilarityApi similarityApi;
    private String queryPart;
    private String indexField;
    private boolean useAugmentedVersion;
    private final AugmentedTermQuery.ModelMethod method;
    private Preprocessing preprocessingMethod;

    /**
     * Constructor of a simple qq parser.
     */
    public SimilarityApiParser(String queryPart, String indexField, boolean useAugmentedVersion, AugmentedTermQuery.ModelMethod modelMethod, Preprocessing preprocessingMethod) {
        this.queryPart = queryPart;
        this.indexField = indexField;

        this.useAugmentedVersion = useAugmentedVersion;
        this.method = modelMethod;
        this.preprocessingMethod = preprocessingMethod;
    }

    public void setSimilarityApi(ISimilarityApi similarityApi){
        this.similarityApi = similarityApi;
    }

    /*
     * Parses the given query, with the settings set in the constructor
     */
    @Override
    public Query parse(QualityQuery qq) throws ParseException {

        //System.out.println("\n--------\n"+qq.getQueryID());

        if(useAugmentedVersion) {
            try {
                return getAugmentedTermQuery(qq);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }else{
            return getTermQuery(qq);
        }
    }


    /**
     * Create a standard lucene <code>{@link TermQuery}</code> concatenated with a <code>{@link BooleanQuery}</code>
     * This can be used for a baseline evaluation, without similarity information
     */
    private Query getTermQuery(QualityQuery qq) {
        EnglishAnalyzer analyzer = new EnglishAnalyzer();

        String[] queryTerms;

        try {
            queryTerms = termsFromTokenStream(analyzer.tokenStream(indexField, qq.getValue(queryPart)));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


        //
        // create the lucene query
        //
        Query query;
        if(queryTerms.length == 1) {
            query = new TermQuery(new Term(indexField,queryTerms[0]));
        }else{

            BooleanQuery.Builder builder = new BooleanQuery.Builder();

            for (String term : queryTerms) {
                builder.add(new TermQuery(new Term(indexField,term)), BooleanClause.Occur.SHOULD);
            }

            query = builder.build();
        }

        return query;
    }


    private Query getAugmentedTermQuery(QualityQuery qq) throws IOException {
        EnglishAnalyzer analyzer = new EnglishAnalyzer();

        //
        // Prepare terms for api
        //
        String[] queryTerms = null;

        if(preprocessingMethod == Preprocessing.Stemmed) {

            try {
                queryTerms = termsFromTokenStream(analyzer.tokenStream(indexField, qq.getValue(queryPart)));
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        }else if(preprocessingMethod == Preprocessing.Tokenize) {

            queryTerms = qq.getValue(queryPart).split(" "); // todo remove non text chars (?)

            for (int i = 0; i < queryTerms.length; i++) {
                queryTerms[i] = queryTerms[i].toLowerCase();
            }
        }

        //
        // get similar terms through the api
        //
        SimilarTermModel[] similarTerms = null;

        try{
            similarTerms = similarityApi.GetSimilarTerms(indexField, queryTerms);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //
        // If no preprocessing before -> we have to do it now
        //
        if(preprocessingMethod == Preprocessing.Tokenize) {
            List<SimilarTermModel> transformedSimilarTerms = new ArrayList<>();
            for (SimilarTermModel m : similarTerms) {
                String[] st = termsFromTokenStream(analyzer.tokenStream(indexField, m.queryTerm.text()));
                if (st.length == 0) {
                    continue;
                }
                String mainStemmed = st[0];

                List<TermWeightTuple> similar = new ArrayList<>();
                for (int j = 0; j < m.similarTerms.length; j++) {

                    String[] stemmed = termsFromTokenStream(analyzer.tokenStream(indexField, m.similarTerms[j].term.text()));
                    if (stemmed.length > 0) {
                        if (similar.stream().filter(t -> t.term.text().equals(stemmed[0])).count() == 0 && !stemmed[0].equals(mainStemmed)) {
                            similar.add(
                                    new TermWeightTuple(
                                            new Term(indexField, stemmed[0]),
                                            m.similarTerms[j].weight));
                        }
                    }
                }

                transformedSimilarTerms.add(
                        new SimilarTermModel(new Term(indexField, mainStemmed),
                                similar.toArray(new TermWeightTuple[0])));
            }

            similarTerms = transformedSimilarTerms.toArray(new SimilarTermModel[0]);
        }


        //
        // create the lucene query
        //
        Query query;
        if(similarTerms.length == 1) {
            query = new AugmentedTermQuery(method, similarTerms[0].queryTerm, similarTerms[0].similarTerms);
        }else{

            BooleanQuery.Builder builder = new BooleanQuery.Builder();

            for (SimilarTermModel model : similarTerms) {
                builder.add(new AugmentedTermQuery(method, model.queryTerm, model.similarTerms), BooleanClause.Occur.SHOULD);
            }

            query = builder.build();
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
}
