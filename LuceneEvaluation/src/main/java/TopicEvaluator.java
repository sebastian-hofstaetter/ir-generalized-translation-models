import at.ac.tuwien.ifs.api.*;
import at.ac.tuwien.ifs.query.AugmentedTermQuery;
import at.ac.tuwien.ifs.query.BM25SimilarityLossless;
import org.apache.commons.cli.*;
import org.apache.lucene.benchmark.quality.*;
import org.apache.lucene.benchmark.quality.trec.TrecJudge;
import org.apache.lucene.benchmark.quality.trec.TrecTopicsReader;
import org.apache.lucene.benchmark.quality.utils.SubmissionReport;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Main class, used to evaluate a lucene index (created with Indexer.main)
 * Needs the index directory, output file name, topic file
 */
public class TopicEvaluator {

    private static String docNameField = "docname";

    private static CommandLine parsedArgs;
    private static FSDirectory indexDirectory;
    private static Path topicsPath;
    private static Path qrelsPath;

    public static void main(String[] args) throws Exception {

        //
        // options:
        //
        // - which similarity source to use (rest-api/file/recorder/mock)
        //     - option string (url or file or out-file)
        // - topic/qrel files
        // - index location
        // - result output directory
        // - result count

        // - batch eval: multiple possible
        //    - similarity class (bm25,bm25lossless,lm)
        //    - translation model (none,generalized,extended)

        // - pre-process before or after api communication

        Options options = new Options();

        options.addRequiredOption("s","similarity-source",true,
                "which similarity source to use to use (rest-api/file/recorder/mock)");

        options.addRequiredOption("so","similarity-option",true,
                "based on -s, url or file path ...");

        options.addRequiredOption("t","topic-file",true,
                "location of a TREC topic file");

        options.addRequiredOption("q","qrel-file",true,
                "location of a TREC qrel file");

        options.addRequiredOption("i","index-directory",true,
                "location of a lucene index (directory)");

        options.addRequiredOption("o","output-directory",true,
                "location of the output files per evaluation");

        options.addRequiredOption("c","result-count",true,
                "how many results to return per topic");

        options.addRequiredOption("e","similarity-classes",true,
                "choice: bm25,bm25lossless,lm - can be multiple sep. by ',' but bm25lossless needs a different index");

        options.addRequiredOption("m","translation-models",true,
                "choice: none,GT,ET - can be multiple sep. by ','");

        CommandLineParser parser = new DefaultParser();
        try {
            parsedArgs = parser.parse( options, args );
        }catch (ParseException e){
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "topicEvaluator", options );
            return;
        }

        //
        // do the actual work
        //

        indexDirectory = FSDirectory.open(Paths.get(parsedArgs.getOptionValue("i")));
        topicsPath = Paths.get(parsedArgs.getOptionValue("t"));
        qrelsPath = Paths.get(parsedArgs.getOptionValue("q"));

        String[] similarityClasses = parsedArgs.getOptionValue("e").split(",");
        String[] translationModels = parsedArgs.getOptionValue("m").split(",");

        for(String sim : similarityClasses){
            for(String mod : translationModels) {

                doEvaluation(Paths.get(parsedArgs.getOptionValue("o") ,sim + "-" + mod + ".txt"),
                        sim,
                        mod);
            }
        }

        indexDirectory.close();

    }

    private static void doEvaluation(Path submissionFile, String sim, String model) throws Exception {

        //
        // prepare index + similarity
        //
        IndexReader reader = DirectoryReader.open(indexDirectory);
        IndexSearcher searcher = new IndexSearcher(reader);

        searcher.setSimilarity(getSimilarityFromString(sim));

        //
        // output writers
        //
        PrintWriter logger = new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true);
        SubmissionReport submitLog = new SubmissionReport(new PrintWriter(Files.newBufferedWriter(submissionFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE)), "lucene");

        // use trec utilities to read trec topics into quality queries
        TrecTopicsReader qReader = new TrecTopicsReader();
        QualityQuery qqs[] = qReader.readQueries(Files.newBufferedReader(topicsPath, StandardCharsets.UTF_8));

        // prepare judge, with trec utilities that read from a QRels file
        Judge judge = new TrecJudge(Files.newBufferedReader(qrelsPath, StandardCharsets.UTF_8));

        // validate topics & judgments match each other
        judge.validateData(qqs, logger);

        boolean useAugmented;
        AugmentedTermQuery.ModelMethod mm;

        switch (model) {
            case "none":
                useAugmented = false;
                mm = null;
                break;
            case "ET":
                mm = AugmentedTermQuery.ModelMethod.Extended;
                useAugmented = true;
                break;
            case "GT":
                mm = AugmentedTermQuery.ModelMethod.Generalized;
                useAugmented = true;
                break;
            default:
                throw new RuntimeException("translation model: "+model+" not known");
        }

        // set the parser + api instance
        SimilarityApiParser qqParser = new SimilarityApiParser("title", "body", useAugmented, mm);
        ISimilarityApi similarityApi;

        switch (parsedArgs.getOptionValue("s")){
            case "rest-api":
                similarityApi = new SimilarityApi(parsedArgs.getOptionValue("so"), null);
                break;
            case "file":
                similarityApi = new SimilarityApiFromFile(parsedArgs.getOptionValue("so"));
                break;
            case "recorder":
                similarityApi = new SimilarityRecorder(parsedArgs.getOptionValue("so"));
                break;
            case "mock":
                similarityApi = new SimilarityApiMock();
                break;
            default:
                throw new RuntimeException("similarity option : "+parsedArgs.getOptionValue("s")+" not known");
        }

        qqParser.setSimilarityApi(similarityApi);

        //
        // run the evaluation
        //
        QualityBenchmark qrun = new QualityBenchmark(qqs, qqParser, searcher, docNameField);
        qrun.setMaxResults(Integer.parseInt(parsedArgs.getOptionValue("c")));
        QualityStats stats[] = qrun.execute(judge, submitLog, new PrintWriter(Files.newBufferedWriter(Paths.get("results/log.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE)));

        // print an average sum of the results
        QualityStats avg = QualityStats.average(stats);
        avg.log("SUMMARY", 2, logger, "  ");
        reader.close();
    }

    private static Similarity getSimilarityFromString(String sim) {
        Similarity similarity;

        switch (sim) {
            case "bm25":
                similarity = new BM25Similarity(1.2f, 0.6f);
                break;
            case "bm25lossless":
                similarity = new BM25SimilarityLossless(1.2f, 0.6f);
                break;
            case "lm":
                similarity = new LMDirichletSimilarity(1000);
                break;
            default:
                throw new RuntimeException("similarity: "+sim+" not known");
        }
        return similarity;
    }
}
