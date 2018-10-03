# LSH-twitter
Implementation of a Nearest Neighbour algorithm to find pairs of tweets with the
highest similarity. Similarity is measured using shingles as features and jaccard 
similarity as a measure. 

## Shingling and hashing
Shingles are n-grams of consecutive words. For example, on the sentence _This is
a tweet_, using shingles of length 2 one obtains the following features:

{(this, is), (is, a), (a, tweet)}

To avoid sparsity and to prevent memory overflow, shingles are hashed using 
the Murmur Hash. That fixes the memory size to _N*nShingles_. Tweets are  
described as a set of integers (see `TwitterReader` class). 
Both the shingle length and the number of buckets of the hashing function are 
tuning parameters, specified with the `-shingleLength` and `-nShingles` option,
respectively.

## The LSH algorithm
LSH stands for Local Sensitive Hashing. Its main goal is reducing the amount of
computations on a Nearest Neighbour search. Using a Brute Force search has
a complexity of _O(N^2)_: for each data point the algorithm looks
for the most similar one on all the remaining _N-1_ points. That is extremely
inefficient. LSH hashes all data points multiple times and creates
a signature matrix with the result. Then, it divides this matrix in rows and bands,
and finds _candidate pairs_ that match on some part of the divided matrix. That
way, the algorithm performs the search on a smaller subset of the data. A detailed 
description of how it works can be found on this
[link](http://infolab.stanford.edu/~ullman/mining/2006/lectureslides/cs345-lsh.pdf).

## The data
The data consists on a set of 100k tweets located in the data/tweetSubset folder. 
It is stored in a tsv file. Only the third column, containing the text, is relevant.

## Running the code
After compiling, the code can be run with the following command:

```java
java Runner -algorithm lsh -threshold 0.3 -maxFiles 30000 -inputPath data/tweetsSubset -outputPath path_to_csv -shingleLength 3 -nShingles 300000 -rows 1 -bands 40 
```

The program will produce a file with all pairs with a similarity above the input
`-threshold`. `-maxfiles` indicates the maximum amount of tweets to process. Rows 
and bands parameters modify the size of the signature matrix (_N\*rows\*bands_).

## Experiments
The file `exp/report.pdf` evaluates the effect of the tuning parameters on the algorithm.
The most important ones are the rows and the bands of the LSH search. The `exp` folder
also contains the results after running some of the experiments, but no tall of them,
because some files were too large -- particularly if there were too many candidate pairs.
