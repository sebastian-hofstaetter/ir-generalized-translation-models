# Evaluation

The goal of the Evaluation is to reproduce the results of TREC-8 described in the paper for BM25 and Language Model similarity measures.

## Methodology

- Windows 10 Education (1703) 64bit 
- Windows Subsystem for Linux - Ubuntu (for trec_eval)
- Lucene version: 6.6.0

### Indexing

- TREC-8 (fbis, fr94, ft, latimes), 528106 documents
- Used Lucene's built-in TREC reader to parse files
- Indexed title + body of TREC documents (concatenated with a space ``" "``)
- Used Lucene's ``EnglishAnalyzer`` (including a porter stemmer, lower case filter) and setting the stop-word list to nltk's 127 word list (like in the paper)
- Re-indexed data after changing the similarity measure, to make sure the correct temp data is used

### Query 

- Used TREC-8 Adhoc Topics 401-450
    - Used only the title of topics for query generation
- Used Lucene's built in TREC classes to parse topic file
- Similarity api parameters: 
    ````
    vector_method = "we"
    similarity_method = "cos"
    filter_method = "threshold"
    filter_value = "0.7"
    ````
 - Language Model class: ``LMDirichletSimilarity`` with ``mu = 1000``
 - BM25 (``BM25Similarity`` and ``BM25SimilarityLossless``) with parameters ``k_1: 1.2 ; b: 0.6``

### Trec_eval
 - Pulled from: https://github.com/usnistgov/trec_eval/ - Version 9.0.4
 - Used MAP and option -J, exact command can be seen in the ``LuceneEvaluation`` project under ``evaluation-rankings/run_trec_eval.sh``
 - Compiled + Run on the "Windows Subsystem for Linux"

## Results

| Similarity      | Model              | MAP      | P @ 10   | P @ 25   | P @ 50   | P @ 100  |
| -------------   | ------------------ |     ---: |     ---: |     ---: |     ---: |     ---: |
| BM25 (Lucene)   | Baseline (no api)  | 0.2500   | 0.4760   | 0.3776   | 0.3080   | 0.2352   |
| BM25 (Lucene)   | Generalized Model  | 0.2691   | 0.4740   | 0.3896   | 0.3244   | 0.2562   |
| BM25 (Lucene)   | Extended Model     | 0.2670   | 0.4660   | 0.3888   | 0.3228   | 0.2558   |
|                 |                    |          |          |          |          |          |
| BM25 (Lossless) | Baseline (no api)  | 0.2573   | 0.4760   | 0.3896   | 0.3100   | 0.2378   |
| BM25 (Lossless) | Generalized Model  | 0.2779   |**0.4780**| 0.3944   |**0.3252**|**0.2572**|
| BM25 (Lossless) | Extended Model     | 0.2732   | 0.4700   |**0.3952**| 0.3236   | 0.2552   |
|                 |                    |          |          |          |          |          |
| Language Model  | Baseline (no api)  | 0.2604   | 0.4580   | 0.3744   | 0.3036   | 0.2258   |
| Language Model  | Generalized Model  |**0.2791**| 0.4600   | 0.3856   | 0.3148   | 0.2432   |
| Language Model  | Extended Model     | 0.2731   | 0.4540   | 0.3880   | 0.3112   | 0.2382   |