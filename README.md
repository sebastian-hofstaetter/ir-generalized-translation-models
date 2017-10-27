# Hello
This repository contains the Lucene &amp; Solr implementation of the paper: 

  *Translation Models in the Probabilistic Relevance Framework*, Rekabsaz et al., CIKM '16 (http://dl.acm.org/citation.cfm?id=2983833)

## Structure

- **Extensions** contains the extension classes to be used by Lucene &amp; Solr + self-contained Unit & Integration Tests 
- **LuceneEvaluation** contains indexing & evaluation code using Lucene for the Extensions (evaluating on TREC-8)
- **Documentation** contains explanations of the code / concept and the transformation from the formulas described in the paper to Lucene useable code

The Java projects and their dependencies are managed via maven.
