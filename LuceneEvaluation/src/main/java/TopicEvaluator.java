import at.ac.tuwien.ifs.api.*;
import at.ac.tuwien.ifs.query.AugmentedTermQuery;
import at.ac.tuwien.ifs.query.BM25SimilarityLossless;
import org.apache.commons.cli.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main class, used to evaluate a lucene index (created with Indexer.main)
 * Needs the index directory, output file name, topic file
 */
public class TopicEvaluator {

    private static String docNameField = "docname";

    private static CommandLine parsedArgs;
    private static FSDirectory indexDirectory;
    private static Path topicsPath;
    private static IndexReader reader;

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

        options.addRequiredOption("s", "similarity-source", true,
                "which similarity source to use to use (rest-api/file/recorder/mock)");

        options.addRequiredOption("so", "similarity-option", true,
                "based on -s, url or file path ... (if s=file, you can set a simple glob (dir/filenamestart) here for multiple evaluations)");

        options.addRequiredOption("sp", "similarity-preprocessing", true,
                "which type of preprocessing should be done before the similarity source is accessed" +
                        " (tokenize-only or full-analyzer)");

        options.addRequiredOption("t", "topic-file", true,
                "location of a TREC topic file");

        options.addRequiredOption("p", "parallel-count", true,
                "hoe many threads should be used for batch evaluation");

        options.addRequiredOption("i", "index-directory", true,
                "location of a lucene index (directory)");

        options.addRequiredOption("o", "output-directory", true,
                "location of the output files per evaluation");

        options.addRequiredOption("ol", "output-file-prefix", true,
                "prefix string for the name of the output file");

        options.addRequiredOption("c", "result-count", true,
                "how many results to return per topic");

        options.addRequiredOption("e", "similarity-classes", true,
                "choice: bm25,bm25lossless,lm - can be multiple sep. by ',' but bm25lossless needs a different index");

        options.addRequiredOption("m", "translation-models", true,
                "choice: none,GT,ET - can be multiple sep. by ','");

        options.addRequiredOption("a", "analyzer", true,
                "english/stop-lower-only");

        CommandLineParser parser = new DefaultParser();
        try {
            parsedArgs = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("topicEvaluator", options);
            return;
        }

        long startTime = System.currentTimeMillis();

        //
        // do the actual work
        //

        indexDirectory = FSDirectory.open(Paths.get(parsedArgs.getOptionValue("i")));
        reader = DirectoryReader.open(indexDirectory);

        topicsPath = Paths.get(parsedArgs.getOptionValue("t"));

        String[] similarityClasses = parsedArgs.getOptionValue("e").split(",");
        String[] translationModels = parsedArgs.getOptionValue("m").split(",");

        String simApiSource = parsedArgs.getOptionValue("s");
        String simApiSourceOption = parsedArgs.getOptionValue("so");

        //
        // file api can use multiple files one after another
        //
        if (simApiSource.equals("file")) {

            int sep_pos = simApiSourceOption.lastIndexOf(File.separator);
            String basePath = simApiSourceOption.substring(0, sep_pos + 1);
            String glob = simApiSourceOption.substring(sep_pos + 1);

            List<Path> files = new ArrayList<>();

            Files.walkFileTree(Paths.get(basePath), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {

                    if (path.getFileName().toString().startsWith(glob)) {
                        files.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                        throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });

            int threadCount = Integer.parseInt(parsedArgs.getOptionValue("p"));
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            for (Path file : files) {
                for (String sim : similarityClasses) {
                    for (String mod : translationModels) {
                        executorService.submit(() -> {

                            String fname = file.getFileName().toString();
                            int pos = fname.lastIndexOf(".");
                            if (pos > 0) {
                                fname = fname.substring(0, pos);
                            }

                            Path outputPath = Paths.get(parsedArgs.getOptionValue("o"),
                                    parsedArgs.getOptionValue("ol") + fname + "_" + sim + "_" + mod + ".txt");

                            System.out.println("Evaluating: " + fname + " " + sim + " " + mod);

                            try {
                                doEvaluation(outputPath, sim, mod, file.toString());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                    }
                }
            }

            executorService.shutdown();
            executorService.awaitTermination(7, TimeUnit.DAYS); // = no timeout

        }

        //
        // all other api sources - eval by simClass & translation models only
        //
        else {
            for (String sim : similarityClasses) {
                for (String mod : translationModels) {

                    Path outputPath = Paths.get(parsedArgs.getOptionValue("o"),
                            parsedArgs.getOptionValue("ol") + sim + "_" + mod + ".txt");

                    doEvaluation(outputPath, sim, mod, simApiSourceOption);
                }
            }
        }

        System.out.println("All completed !\n Total time: (s): " + ((System.currentTimeMillis() - startTime) / 1000f));

        reader.close();
        indexDirectory.close();
    }

    private static final Object _lock = new Object();

    private static void doEvaluation(Path submissionFile, String sim, String model, String similarityOption) throws Exception {

        PrintWriter logger = new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true);

        long startTime;
        synchronized (_lock) {
            startTime = System.currentTimeMillis();
            logger.println("Starting: " + submissionFile.toString());
            logger.println("with: " + sim + " - " + model);
            logger.flush();
        }

        //
        // prepare index + similarity
        //
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(getSimilarityFromString(sim));

        // use trec utilities to read trec topics into quality queries
        // + output writers

        SubmissionReport submitLog;
        // don't output anything when recording only
        if (parsedArgs.getOptionValue("s").equals("recorder")) {
            submitLog = new SubmissionReport(new PrintWriter(new OutputStream() {
                public void write(int b) {
                }
            }), "");
        } else {
            submitLog = new SubmissionReport(new PrintWriter(Files.newBufferedWriter(submissionFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE)), "lucene");
        }

        TrecTopicsReader qReader = new TrecTopicsReader();
        QualityQuery qqs[] = qReader.readQueries(Files.newBufferedReader(topicsPath, StandardCharsets.UTF_8));


        //
        // prepare + set the parser + api instance
        //
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
                throw new RuntimeException("translation model: " + model + " not known");
        }

        SimilarityApiParser.Preprocessing apiPrePro = SimilarityApiParser.Preprocessing.Tokenize;
        if (parsedArgs.getOptionValue("sp").equals("full-analyzer")) {
            apiPrePro = SimilarityApiParser.Preprocessing.FullAnalyzer;
        }

        Analyzer analyzer = null;

        switch (parsedArgs.getOptionValue("a")) {
            case "english":
                analyzer = new EnglishAnalyzer(StopWords.nltkStopWords());
                break;
            case "stop-lower-only":
                analyzer = new StopAnalyzer(StopWords.nltkStopWords());
                break;
            default:
                throw new RuntimeException("analyzer not supported: " + parsedArgs.getOptionValue("a"));
        }

        SimilarityApiParser qqParser = new SimilarityApiParser("title", "body", useAugmented, mm, apiPrePro, analyzer);
        qqParser.setSimilarityApi(getISimilarityApi(similarityOption));

        //
        // run the evaluation
        //
        QualityBenchmark qrun = new QualityBenchmark(qqs, qqParser, searcher, docNameField);
        qrun.setMaxResults(Integer.parseInt(parsedArgs.getOptionValue("c")));

        qrun.execute(null, submitLog, new PrintWriter(new OutputStream() {
            public void write(int b) {
            }
        }));

        synchronized (_lock) {
            System.out.println("Finishing " + submissionFile.toString() + " after (s): " + ((System.currentTimeMillis() - startTime) / 1000f));
        }
    }

    private static ISimilarityApi getISimilarityApi(String similarityOption) throws IOException {
        ISimilarityApi similarityApi;
        switch (parsedArgs.getOptionValue("s")) {
            case "rest-api":
                similarityApi = new SimilarityApi(similarityOption, null);
                break;
            case "file":
                similarityApi = new SimilarityApiFromFile(similarityOption);
                break;
            case "recorder":
                similarityApi = new SimilarityRecorder(similarityOption);
                break;
            case "mock":
                similarityApi = new SimilarityApiMock();
                break;
            default:
                throw new RuntimeException("similarity option : " + parsedArgs.getOptionValue("s") + " not known");
        }
        return similarityApi;
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
                throw new RuntimeException("similarity: " + sim + " not known");
        }
        return similarity;
    }
}
