import java.util.*;

/**
 *
 */
public class LSHSearch {
    private TwitterReader reader;
    private int lshRows;
    private int lshBands;
    private int sigRows;            // rows/hash functions of the signature matrix
    private int nShingles;          // max number of shingles/signature hashing values
    private int nDocs;
    private int p;
    private int[] a;
    private int[] b;
    private int[][] sig;

    public LSHSearch(TwitterReader reader, int bands, int rows) {
        this.reader = reader;
        this.lshRows = rows;
        this.lshBands = bands;
        this.sigRows = rows * bands;
        this.nShingles = reader.shingler.nShingles;
        this.nDocs = reader.maxDocs;  // updated later if it is actually smaller
        this.p = 2147482951;
        this.a = new int[this.sigRows];
        this.b = new int[this.sigRows];
        Random rand = new Random();

        // init the signature hash functions
        for(int i = 0; i < this.sigRows; i++){
            // in each iteration, new parameters a, b and prime
            a[i] = rand.nextInt(Integer.MAX_VALUE);
            b[i] = rand.nextInt(Integer.MAX_VALUE);
        }
    }


    // returns the hash of a single, given hashing function index h
    private int hashShingle(int shingle, int h){
        return  (((a[h] * shingle + b[h]) % p ) % this.nShingles) & 0x7FFFFFFF;
    }


    public Set<SimilarPair> getSimilarPairsAboveThreshold(double threshold){
        Set<SimilarPair> sims = new HashSet<SimilarPair>();

        createSignatureMatrix();
        System.out.println("Signature matrix has been created");


        // finding the candidates with lsh
        for (int band = 0; band < this.lshBands; band++) {       // iterate bands
            int b = band * this.lshRows;
            Map<Integer, HashSet<Integer>> buckets = new HashMap<Integer, HashSet<Integer>>();
            // find the candidate pairs of a band
            for (int doc = 0; doc < this.nDocs; doc++) {                   // iterate signatures (columns) and hash them

                int docSignatureHash = getDocSignatureHash(b, doc);

                // add the candidate to the corresponding set, or create a new entry with the doc on the set

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
        while (reader.hasNext()){                                 // iterate docs
            for (int shingle: reader.next()){                     // iterate shingles per doc
                if (!hashedShingles.containsKey(shingle)) {  //store only if the shingle has not been hashed yet
                    storeHashedShingle(shingle, hashedShingles);
                }
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

        reader = null;            // same for reader
    }


    private void storeHashedShingle(int shingle, HashMap<Integer, int[]> hashedShingles) {
        int[] shingleHashes = new int[this.sigRows];
        for (int f = 0; f < this.sigRows; f++) {  //f loops the existing hashing functions
            shingleHashes[f] = hashShingle(shingle, f);
        }
        hashedShingles.put(shingle, shingleHashes);
    }


    private int getDocSignatureHash(int b, int doc) {
        String signature = "|";

        for (int row = b; row < this.lshRows + b; row++) {  // create signature (concat values)
            // the | avoids collisions of the type: [1,23,4] - [12,3,4]
            signature = signature.concat(Integer.toString(sig[row][doc])).concat("|");
        }
        int signatureHash = MurmurHash.hash32(signature, 9999);
        return signatureHash;
    }


    private double computeSignaturesSimilarity(int doc1, int doc2){
        int union = 0;
        for (int i = 0; i < this.sigRows; i++){
            if (sig[i][doc1] == sig[i][doc2]) union++; // rows matching in the signature
        }
        return (double) union / this.sigRows;
    }

}
