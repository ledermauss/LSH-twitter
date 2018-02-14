import java.util.*;
import java.util.function.Function;

/**
 *
 */
public class LSHSearch extends Search {
    private int lshRows;
    private int lshBands;
    private int sigRows;            // rows/hash functions of the signature matrix
    private int nShingles;          // max number of shingles/signature hashing values
    private Function <Integer, Integer>[] sigHashFunctions;

    public LSHSearch(TwitterReader reader, int bands, int rows) {
        super(reader);
        this.lshRows = rows;
        this.lshBands = bands;
        this.sigRows = rows * bands;
        this.nShingles = reader.shingler.nShingles;
        this.sigHashFunctions = new Function[this.sigRows];
        Random rand = new Random();
        int prime = Primes.findLeastPrimeNumber(this.nShingles + 1);

        // init the signature hash functions
        for(int i = 0; i < this.sigRows; i++){
            // in each iteration, new parameters a, b and prime
            this.sigHashFunctions[i] =
                    initHash(rand.nextInt(Integer.MAX_VALUE), rand.nextInt(Integer.MAX_VALUE), prime);
            prime = Primes.findLeastPrimeNumber(prime + 1);
        }
    }


    private Function<Integer, Integer> initHash(int a, int b, int p){
        // lambda: shingle is the argument (int) the generated function expects
        // this.nShingles: max length of the hashValue (max shingles)
        return  shingle ->
                (UniversalHash.hash32(shingle, this.nShingles, a, b, p) & 0x7FFFFFFF);
    }


    // returns the hash of a single, given hashing function index h
    private int hashShingle(int shingle, int h){ return sigHashFunctions[h].apply(shingle); }


    @Override
    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold){
        Set<SimilarPair> sims = new HashSet<SimilarPair>();
        Map<Integer, Set<Integer>> docToShingle = super.getShingledTweets();
        int docs = docToShingle.keySet().size();

        int[][] sig = createSignatureMatrix(docs, docToShingle);

        int murmurSeed = (new Random()).nextInt();

        // finding the candidates with lsh
        for (int b = 0; b < this.sigRows; b += this.lshRows) {       // iterate bands
            Map<Integer, Set<Integer>> bandCandidatePairs = new HashMap<Integer, Set<Integer>>();

            // find the candidate pairs of a band
            for (int doc = 0; doc < docs; doc++) {                   // iterate signatures (columns) and hash them
                String docSignature = "";

                for (int row = b; row < b + this.lshRows; row++) {  // create signature (concat values)
                    // bandSignature += sig[row][doc], concat is faster
                    docSignature = docSignature.concat(Integer.toString(sig[row][doc]));
                }

                int docSignatureHash = MurmurHash.hash32(docSignature, murmurSeed);

                // add the candidate to the corresponding set, or create a new entry with the doc on the set
                if (bandCandidatePairs.containsKey(docSignatureHash)){
                    bandCandidatePairs.get(docSignatureHash).add(doc);
                } else {
                    bandCandidatePairs.put(docSignatureHash, Collections.singleton(doc)); // set with one element
                }
            }

            // extract pairs with similarity above the threshold from the band, store them
            for (Set<Integer> set : bandCandidatePairs.values()) {
                if (set.size() > 1) {
                    Set<SimilarPair> setSims = getSimilarPairsAboveThresholdFromSet(threshold, set, sig);
                    sims.addAll(setSims);
                }
            }
        }
        return sims;
    }


    private int[][] createSignatureMatrix(int docs, Map<Integer, Set<Integer>> docToShingle){
        // 1. initialize sizes
        int[][] sig = new int[this.sigRows][docs];
        for (int[] row: sig)
            Arrays.fill(row, this.nShingles + 1);              // fill with infinite = fill with maximum hashing value

        // 2. go through each shingle and doc,
        for (Integer doc: docToShingle.keySet()){                    // iterate columns (docs)
            for (Integer shingleRow: docToShingle.get(doc)){         // iterate rows (shingles, range 0 - maxShingles)
                for (int i = 0; i < this.sigRows; i++){              // apply each hash function
                    // 3. hash row number when a 1 is found, store min value
                    sig[i][doc] = (hashShingle(shingleRow, i) < sig[i][doc]) ?
                            hashShingle(shingleRow, i) : sig[i][doc];
                }
            }
        }
        return sig;
    }

    //public Map<Integer, Set<Integer>> getBandCandidatePairs(int )


    public Set<SimilarPair> getSimilarPairsAboveThresholdFromSet(double threshold, Set<Integer> docs, int[][] sig){
        Set<SimilarPair>  simPairs = new HashSet<SimilarPair>();
        for (int doc1 : docs)
            for (int doc2 : docs)
                if (doc1 < doc2){ // avoids duplicities
                    double sim = computeSignaturesSimilarity(sig, doc1, doc2);
                    if (sim > threshold) simPairs.add(new SimilarPair(doc1, doc2, sim));
                }
        return simPairs;
    }


    public double computeSignaturesSimilarity(int[][] sig, int doc1, int doc2){
        int union = 0;
        for (int i = 0; i < this.sigRows; i++){
            if (sig[i][doc1] == sig[i][doc2]) union++; // rows matching in the
        }
        return (double) union / this.sigRows;
    }



}
