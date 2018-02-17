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
    private Function <Integer, Integer>[] sigHashFunctions;
    private int[][] sig;

    public LSHSearch(TwitterReader reader, int bands, int rows) {
        super(reader);
        this.lshRows = rows;
        this.lshBands = bands;
        this.sigRows = rows * bands;
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
        return  shingle -> (((a * shingle + b) % p ) % this.nShingles) & 0x7FFFFFFF;
    }


    // returns the hash of a single, given hashing function index h
    private int hashShingle(int shingle, int h){ return sigHashFunctions[h].apply(shingle); }


    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold){
        Set<SimilarPair> sims = new HashSet<SimilarPair>();

        createSignatureMatrix();
        System.out.println("Signature matrix has been created");


        // finding the candidates with lsh
        for (int band = 0; band < this.lshBands; band++) {       // iterate bands
            System.out.println("Looking for pair candidates in band " + band);
            int b = band * this.lshRows;
            Map<Integer, HashSet<Integer>> buckets = new HashMap<Integer, HashSet<Integer>>();
            // find the candidate pairs of a band
            for (int doc = 0; doc < this.nDocs; doc++) {                   // iterate signatures (columns) and hash them

                int docSignatureHash = getDocSignatureHash2(b, doc);

                // add the candidate to the corresponding set, or create a new entry with the doc on the set
                if (doc % 10000 == 0){
                    System.out.println("Processing candidates of document " + doc);
                    System.out.println("Size of similar Pairs: " + sims.size());
                    System.out.println("Size of buckets: " + buckets.size());
                }

                if (buckets.containsKey(docSignatureHash)){
                    for (int prevDoc : buckets.get(docSignatureHash)) {
                        SimilarPair pair = new SimilarPair(prevDoc, doc, 0);
                        if (!sims.contains(pair)) {
                            double sim = computeSignaturesSimilarity(prevDoc, doc);
                            if (sim > threshold) {
                                pair.sim = sim;
                                sims.add(pair);
                            }
                        }
                    }
                    buckets.get(docSignatureHash).add(doc);
                } else {
                    HashSet<Integer> s = new HashSet<Integer>();
                    s.add(doc);
                    buckets.put(docSignatureHash, s);
                }
            }
        }
        System.out.println("Similar pairs: " + sims.size());
        return sims;
    }


    private void createSignatureMatrix(){
        long startTime = System.currentTimeMillis();
        // 1. initialize sizes
        this.sig = new int[this.sigRows][this.nDocs];
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
                    int matVal = sig[h][doc];
                    sig[h][doc] = (shingleHash < matVal) ?         // replace shingle hash in matrix if lower
                             shingleHash : matVal;
                }
            }
            doc++;
        }
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("Seconds for reading the docs and creating signature matrix: " +  elapsedTime/1000);

        this.sigHashFunctions = null;  // hashing functions not needed anymore, can be emptied (dereferenced)
        super.reader = null;            // same for reader
    }


    private void storeHashedShingle(int shingle, HashMap<Integer, int[]> hashedShingles) {
        if (hashedShingles.get(shingle) == null) {  //store only if the shingle has not been hashed yet
            int[] shingleHashes = new int[this.sigRows];
            for (int f = 0; f < this.sigRows; f++) {  //f loops the existing hashing functions
                shingleHashes[f] = hashShingle(shingle, f);
            }
            hashedShingles.put(shingle, shingleHashes);
        }
    }


    private int getDocSignatureHash2(int b, int doc) {
        String signature = "";

        for (int row = b; row < this.lshRows + b; row++) {  // create signature (concat values)
            signature = signature.concat(Integer.toString(sig[row][doc]));
        }
        int signatureHash = MurmurHash.hash32(signature, 9999);
        return signatureHash;
    }


    private int getDocSignatureHash(int b, int doc) {
        byte[] signature = new byte[4 * this.lshRows];          // 4 = integer size in bytes

        for (int row = 0; row < this.lshRows; row++) {  // create signature (concat values)
            byte[] rowBytes = intToByteArray(sig[row * b][doc]);
            // arraycopy (source, srcStart, dest, destStart, length)
            System.arraycopy(rowBytes, 0, signature, 4 * row, 4);
        }
        int signatureHash = MurmurHash.hash32(signature, 4 * this.lshRows, 1234);
        return signatureHash;
    }


    private byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }


    private double computeSignaturesSimilarity(int doc1, int doc2){
        int union = 0;
        for (int i = 0; i < this.sigRows; i++){
            if (sig[i][doc1] == sig[i][doc2]) union++; // rows matching in the signature
        }
        return (double) union / this.sigRows;
    }

}
