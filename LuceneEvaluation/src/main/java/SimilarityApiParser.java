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
 *
 */
public class SimilarityApiParser implements QualityQueryParser {

    private final ISimilarityApi similarityApi;
    private String queryPart;
    private String indexField;
    private boolean useAugmentedVersion;
    private final AugmentedTermQuery.ModelMethod method;

    /**
     * Constructor of a simple qq parser.
     */
    public SimilarityApiParser(String queryPart, String indexField,boolean useAugmentedVersion, AugmentedTermQuery.ModelMethod method) {
        this.queryPart = queryPart;
        this.indexField = indexField;

        this.useAugmentedVersion = useAugmentedVersion;
        this.method = method;

        similarityApi = new SimilarityApi("http://localhost:5000/api/v1.0/relatedterms", null);
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.benchmark.quality.QualityQueryParser#parse(org.apache.lucene.benchmark.quality.QualityQuery)
     */
    @Override
    public Query parse(QualityQuery qq) throws ParseException {

        System.out.println("\n--------\n"+qq.getQueryID());

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

        String[] queryTerms;

        try {
            queryTerms = termsFromTokenStream(analyzer.tokenStream(indexField, qq.getValue(queryPart)));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        String[] rawQueryTerms = qq.getValue(queryPart).split(" ");

        for(int i = 0;i<rawQueryTerms.length;i++){
            rawQueryTerms[i] = rawQueryTerms[i].toLowerCase();
        }

        //
        // get similar terms through the api
        //
        SimilarTermModel[] similarTerms = null;

        try{
            similarTerms = similarityApi.GetSimilarTerms(indexField, rawQueryTerms);//rawQueryTerms
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<SimilarTermModel> transformedSimilarTerms = new ArrayList<>();
        for(SimilarTermModel m : similarTerms){
            String mainStemmed =termsFromTokenStream(analyzer.tokenStream(indexField, m.queryTerm.text()))[0];

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
