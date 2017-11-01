package at.ac.tuwien.ifs.api;

import at.ac.tuwien.ifs.query.TermWeightTuple;
import org.apache.lucene.index.Term;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


/**
 * Records requested terms to the similarity api, and saves them in a file. Does only return the query terms !
 * Useful if you want to prepare batch analysis inputs used in <code>{@link SimilarityApiFromFile}</code>
 */
public class SimilarityRecorder implements ISimilarityApi {

    private String outFile;

    public SimilarityRecorder(String outFile){
        this.outFile = outFile;
    }

    @Override
    public SimilarTermModel[] GetSimilarTerms(String field, String[] queryTerms) throws IOException {
        SimilarTermModel[] output = new SimilarTermModel[queryTerms.length];
        for (int i = 0; i < queryTerms.length; i++) {
            output[i] = new SimilarTermModel(new Term(field, queryTerms[i]), new TermWeightTuple[0]);
        }

        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new FileWriter(outFile, true));
            bw.write(String.join("\n", queryTerms));
            bw.write("\n");
            bw.flush();
        } finally {
            if (bw != null)
                bw.close();
        }

        return output;
    }
}
