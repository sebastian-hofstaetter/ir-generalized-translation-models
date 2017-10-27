package at.ac.tuwien.ifs.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.index.Term;
import at.ac.tuwien.ifs.query.TermWeightTuple;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Api access class for the https://github.com/neds/similarityAPI, see the api spec for more info
 * about the optional parameters
 */
public class SimilarityApi implements ISimilarityApi {

    private String url;
    private String parameters;

    //parsed parameters
    private String vector_method = "we";
    private String similarity_method = "cos";
    private String filter_method = "threshold";
    private String filter_value = "0.7";

    public SimilarityApi(String url, String parameters) {
        this.url = url;
        this.parameters = parameters;

        if(this.parameters != null){
            if(this.parameters.trim().length() == 0){
                return;
            }

            // parse params
            // vector_method=we;similarity_method=...

            String[] split = this.parameters.split(";");
            for (String s : split){
                String[] innerSplit = s.split("=");
                if(innerSplit.length!=2){
                    continue;
                }
                String method = innerSplit[0].trim();

                if(method.equals("vector_method")) vector_method = innerSplit[1].trim();
                if(method.equals("similarity_method")) similarity_method = innerSplit[1].trim();
                if(method.equals("filter_method")) filter_method = innerSplit[1].trim();
                if(method.equals("filter_value")) filter_value = innerSplit[1].trim();
            }
        }
    }

    @Override
    public SimilarTermModel[] GetSimilarTerms(String field, String[] queryTerms) throws IOException {

        ApiReturnItem[] apiResult = sendApiRequest(queryTerms).items;
        SimilarTermModel[] output = new SimilarTermModel[apiResult.length];

        //
        // main terms loop
        //
        for (int i = 0; i < apiResult.length; i++) {

            //
            // create similar term structure
            //
            List<TermWeightTuple> similar = new ArrayList<>();
            for (int j = 0; j < apiResult[i].similarTerms.length; j++) {

                if(!apiResult[i].similarTerms[j].equals(apiResult[i].mainTerm)) {
                    similar.add(
                            new TermWeightTuple(
                                new Term(field, apiResult[i].similarTerms[j]),
                                apiResult[i].similarWeights[j]));
                }
            }

            output[i] = new SimilarTermModel(new Term(field, apiResult[i].mainTerm), similar.toArray(new TermWeightTuple[0]));
        }

        return output;
    }

    private ApiReturnModel sendApiRequest(String[] queryTerms) throws IOException {

        // convert string[] -> json
        ObjectMapper mapper = new ObjectMapper();

        ApiRequestModel rm = new ApiRequestModel(queryTerms, vector_method, similarity_method, filter_method, filter_value);

        String requestPayload = mapper.writeValueAsString(rm);

        // do request
        HttpURLConnection connection = null;

        try {
            //Create connection
            URL url = new URL(this.url);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(requestPayload);
            wr.close();

            //Get Response
            InputStream inputStream = connection.getInputStream();

            // convert result json -> ApiReturnModel
            return mapper.readValue(inputStream, ApiReturnModel.class);
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
