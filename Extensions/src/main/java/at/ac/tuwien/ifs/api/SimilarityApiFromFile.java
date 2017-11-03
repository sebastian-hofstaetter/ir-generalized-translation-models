package at.ac.tuwien.ifs.api;

import at.ac.tuwien.ifs.query.TermWeightTuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.index.Term;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Api access class for a pre-computed similar term file (to be used for batch evaluation, were query terms are known)
 */
public class SimilarityApiFromFile implements ISimilarityApi {

    private final ApiReturnItem[] apiReturnItems;

    public SimilarityApiFromFile(String file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ApiReturnModel returnModel = mapper.readValue(Files.readAllBytes(Paths.get(file)), ApiReturnModel.class);
        apiReturnItems = returnModel.items;
    }

    @Override
    public SimilarTermModel[] GetSimilarTerms(String field, String[] queryTerms) throws IOException {

        SimilarTermModel[] output = new SimilarTermModel[queryTerms.length];

        //
        // for every query term:
        //
        for (int i = 0; i < output.length; i++) {

            //
            // check if we've got cached info for that term (by exact equality)
            //
            ApiReturnItem item = null;
            for(ApiReturnItem it : apiReturnItems){
                if(it.mainTerm.equals(queryTerms[i])){
                    item = it;
                    break;
                }
            }

            //
            // create similar term structure
            //
            List<TermWeightTuple> similar = new ArrayList<>();

            if(item != null) { // only add info if we have it
                for (int j = 0; j < item.similarTerms.length; j++) {
                    if (!item.similarTerms[j].equals(item.mainTerm)) {
                        similar.add(
                                new TermWeightTuple(
                                        new Term(field, item.similarTerms[j]),
                                        item.similarWeights[j]));
                    }
                }
            }

            output[i] = new SimilarTermModel(new Term(field, queryTerms[i]), similar.toArray(new TermWeightTuple[0]));
        }

        return output;
    }
}
