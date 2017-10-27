import at.ac.tuwien.ifs.query.AugmentedTermQuery;
import at.ac.tuwien.ifs.query.BM25SimilarityLossless;
import org.apache.lucene.benchmark.quality.*;
import org.apache.lucene.benchmark.quality.trec.TrecJudge;
import org.apache.lucene.benchmark.quality.trec.TrecTopicsReader;
import org.apache.lucene.benchmark.quality.utils.SubmissionReport;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
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


    private static int maxResults = 1000;
    private static String docNameField = "docname";

    private static String usageText =
            " Hi, please add 4 args to run all combinations, or 6 args to run specific arguments: " +
                    "\n\t1. the 1-byte compressed-index location  " +
                    //"\n\t2. the uncompressed-index location  " +
                    "\n\t2. the trec topic file location" +
                    "\n\t3. the trec qrels file location" +
                    "\n\t4. the output file name" +
                    "\n\t5. the similarity (bm2b,bm25lossless,lm)" +
                    "\n\t6. the model method (none,generalized,extended)";

    public static void main(String[] args) throws Exception {

        if (!(args.length == 6 || args.length == 4)) {
            System.out.println(usageText);
            return;
        }

        FSDirectory indexDirCompressed = FSDirectory.open(Paths.get(args[0]));
        //FSDirectory indexDirUncompressed = FSDirectory.open(Paths.get(args[1]));
        Path topicsFile = Paths.get(args[1]);
        Path qrelsFile = Paths.get(args[2]);
        Path submissionFile = Paths.get(args[3]);

        if (args.length == 6) {
            // run specified
            String sim = args[4];
            String model = args[5];

            doEvaluation(indexDirCompressed, topicsFile, qrelsFile, submissionFile, sim, model);
        }

        if (args.length == 4) {

            // run all combinations
            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "bm25", "none");
            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "bm25lossless", "none");
            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "lm", "none");

            // doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "bm25", "generalized");
            doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "bm25lossless", "generalized");
            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "lm", "generalized");

            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "bm25", "extended");
            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "bm25lossless", "extended");
            //doEvaluationWrapper(indexDirCompressed, topicsFile, qrelsFile, args[3], "lm", "extended");
        }

        indexDirCompressed.close();

    }

    private static void doEvaluationWrapper(FSDirectory indexDir, Path topicsFile, Path qrelsFile,String basePathSubmission, String sim, String model) throws Exception {
        doEvaluation(indexDir, topicsFile, qrelsFile, Paths.get(basePathSubmission + "/" + sim + "-" + model + ".txt"), sim, model);
    }

    private static void doEvaluation(FSDirectory indexDir, Path topicsFile, Path qrelsFile, Path submissionFile, String sim, String model) throws Exception {
        SubmissionReport submitLog = new SubmissionReport(new PrintWriter(Files.newBufferedWriter(submissionFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE)), "lucene");

        IndexReader reader = DirectoryReader.open(indexDir);
        IndexSearcher searcher = new IndexSearcher(reader);

        if (sim.equals("bm25")) {
            searcher.setSimilarity(new BM25Similarity(1.2f, 0.6f));
        } else if (sim.equals("bm25lossless")) {
            searcher.setSimilarity(new BM25SimilarityLossless(1.2f, 0.6f));
        } else {
            searcher.setSimilarity(new LMDirichletSimilarity(1000));
        }

        PrintWriter logger = new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true);

        // use trec utilities to read trec topics into quality queries
        TrecTopicsReader qReader = new TrecTopicsReader();
        QualityQuery qqs[] = qReader.readQueries(Files.newBufferedReader(topicsFile, StandardCharsets.UTF_8));

        // prepare judge, with trec utilities that read from a QRels file
        Judge judge = new TrecJudge(Files.newBufferedReader(qrelsFile, StandardCharsets.UTF_8));

        // validate topics & judgments match each other
        judge.validateData(qqs, logger);

        //Set<String> fieldSet = new HashSet<>();
        //fieldSet.add("title");

        boolean useAugmented = true;
        AugmentedTermQuery.ModelMethod mm = AugmentedTermQuery.ModelMethod.Generalized;
        if (model.equals("none")) {
            useAugmented = false;
        }
        if (model.equals("extended")) {
            mm = AugmentedTermQuery.ModelMethod.Extended;
        }

        // set the parsing of quality queries into Lucene queries.
        QualityQueryParser qqParser = new SimilarityApiParser("title", "body", useAugmented, mm);

        // run the benchmark
        QualityBenchmark qrun = new QualityBenchmark(qqs, qqParser, searcher, docNameField);
        qrun.setMaxResults(maxResults);
        QualityStats stats[] = qrun.execute(judge, submitLog, new PrintWriter(Files.newBufferedWriter(Paths.get("results/log.txt"), StandardCharsets.UTF_8, StandardOpenOption.CREATE)));

        // print an avarage sum of the results
        QualityStats avg = QualityStats.average(stats);
        avg.log("SUMMARY", 2, logger, "  ");
        reader.close();
    }
}
