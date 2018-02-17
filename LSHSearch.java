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
    private int nDocs;
    private int murmurSeed;         // murmur hash seed for the band buckets
    private Function <Integer, Integer>[] sigHashFunctions;

    public LSHSearch(TwitterReader reader, int bands, int rows) {
        super(reader);
        this.lshRows = rows;
        this.lshBands = bands;
        this.sigRows = rows * bands;
        this.murmurSeed = (new Random()).nextInt();
        this.nShingles = super.reader.shingler.nShingles;
        this.nDocs = super.reader.maxDocs;  // updated later if it is actually smaller
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


    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold){
        Set<SimilarPair> sims = new HashSet<SimilarPair>();

        int[][] sig = createSignatureMatrix();
        System.out.println("Signature matrix has been created");

        if (this.lshBands == 1) { //no lsh, calculate just on the minHash
            for (int doc1 = 0; doc1 < this.nDocs; doc1++)
                for (int doc2 = doc1; doc2 < this.nDocs; doc2++){
                    double sim = computeSignaturesSimilarity(sig, doc1, doc2);
                    sims.add(new SimilarPair(doc1, doc2, sim));
                }
                return sims;
        }

        // finding the candidates with lsh
        for (int band = 0; band < this.lshBands; band++) {       // iterate bands
            System.out.println("Looking for pair candidates in band " + band);
            Map<Long, HashSet<Integer>> bandCandidatePairs = new HashMap<Long, HashSet<Integer>>();
            getBandCandidatePairs(band, sig, bandCandidatePairs); //this was quick

            // extract pairs with similarity above the threshold from the band, store them
            System.out.println("Extracting the candidate pairs from the hashes");
            int iter = 0;
            for (HashSet<Integer> cands : bandCandidatePairs.values()) {
                if (iter == 0) System.out.println ("Pairs to examine: " + bandCandidatePairs.values().size());
                if (iter % 10000 == 0) System.out.println ("Examining candidates set number " + iter + " of band " + band);
                if (cands.size() > 1) {
                    // the method adds directly to sims. Saves memory
                    getSimilarPairsAboveThresholdFromSet(threshold, cands, sig, sims);
                }
                iter++;
            }
        }
        System.out.println("Found similar pairs");
        return sims;
    }


    private int[][] createSignatureMatrix(){
        long startTime = System.currentTimeMillis();
        // 1. initialize sizes
        int[][] sig = new int[this.sigRows][this.nDocs];
        // idea: speed computation by hashing each shingle only the first time it appears
        // later, retrieve this hashes instead of recalculating
        HashMap <Integer, int[]> hashedShingles = new HashMap<Integer,  int[]>();
        for (int[] row: sig)
            Arrays.fill(row, this.nShingles + 1);              // fill with infinite = fill with maximum hashing value

        // 2. go through each shingle and doc,
        int doc = 0;
        while (super.reader.hasNext()){                                 // iterate docs
            if (doc % 100000 == 0) System.out.println("Current doc is: " + doc);
            for (int shingle: super.reader.next()){                     // iterate shingles per doc
                storeHashedShingle(shingle, hashedShingles);
                for (int h = 0; h < this.sigRows; h++){
                    int shingleHash = hashedShingles.get(shingle)[h];
                    sig[h][doc] = (shingleHash < sig[h][doc]) ?         // replace shingle hash in matrix if lower
                             shingleHash : sig[h][doc];
                }
            }
            doc++;
        }
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("Seconds for reading the docs and creating signature matrix: " +  elapsedTime/1000);

        this.sigHashFunctions = null;  // hashing functions not needed anymore, can be emptied (dereferenced)
        return sig;
    }


    public void storeHashedShingle(int shingle, HashMap<Integer, int[]> hashedShingles) {
        if (hashedShingles.get(shingle) == null) {  //store only if the shingle has not been hashed yet
            int[] shingleHashes = new int[this.sigRows];
            for (int f = 0; f < this.sigRows; f++) {  //f loops the existing hashing functions
                shingleHashes[f] = hashShingle(shingle, f);
            }
            hashedShingles.put(shingle, shingleHashes);
        }
    }


    public void getBandCandidatePairs(int band, int[][] sig, Map<Long,
            HashSet<Integer>> bandCandidatePairs){
        int b = band * this.lshRows;
        //Map<Long, HashSet<Integer>> bandCandidatePairs = new HashMap<Long, HashSet<Integer>>();
        // find the candidate pairs of a band
        for (int doc = 0; doc < this.nDocs; doc++) {                   // iterate signatures (columns) and hash them
            String docSignature = "";

            if (doc % 100000 == 0) System.out.println("BandHashes are " + bandCandidatePairs.size());
            for (int row = b; row < b + this.lshRows; row++) {  // create signature (concat values)
                docSignature = docSignature.concat(Integer.toString(sig[row][doc]));
            }

            long docSignatureHash = MurmurHash.hash64(docSignature, this.murmurSeed);

            // add the candidate to the corresponding set, or create a new entry with the doc on the set
            if (bandCandidatePairs.get(docSignatureHash) == null) {
                HashSet<Integer> s = new HashSet<Integer>();
                s.add(doc);
                bandCandidatePairs.put(docSignatureHash, s);
            }
            bandCandidatePairs.get(docSignatureHash).add(doc);
        }
    }


    public void getSimilarPairsAboveThresholdFromSet(double threshold, Set<Integer> docs, int[][] sig,
                                                     Set<SimilarPair> sims){
        // it is assumed that the list is already ordered (since docs are were inserted in ascending order)
        for (int doc1: docs) {
            for (int doc2: docs)
                if (doc1 < doc2 && !sims.contains(new SimilarPair(doc1, doc2, 0))) {
                    double sim = computeSignaturesSimilarity(sig, doc1, doc2);
                    if (sim > threshold) sims.add(new SimilarPair(doc1, doc2, sim));
                }
        }
        return;
    }

    public double computeSignaturesSimilarity(int[][] sig, int doc1, int doc2){
        int union = 0;
        for (int i = 0; i < this.sigRows; i++){
            if (sig[i][doc1] == sig[i][doc2]) union++; // rows matching in the signature
        }
        return (double) union / this.sigRows;
    }



}
