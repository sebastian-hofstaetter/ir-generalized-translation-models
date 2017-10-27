# Documentation

This repository allows to extend Lucene & Solr with a Solr Parser and a Lucene Query. Both are used to extend the search process to include similar terms through an api (the api is not part of this repository - see: [SimilarityApi](https://github.com/neds/similarityAPI) for more)
 
![Extension Structure](images/extension-structure.png)

*Interaction between extension classes of this repository*

## Dependencies

This project depends on the api: https://github.com/neds/similarityAPI

The api must be running and accessible from the Solr server / Lucene program that uses the extensions from this repository. See the api repository for infos about the similarity computation and possible options (that can be defined in the solrconfig.xml).

## Documentation index

- [Usage (Solr & Lucene)](Usage.md)

- [Evaluation](Evaluation.md)

- [Technical Report](Technical%20Report.md)

- [Related Work](Related%20Work.md)