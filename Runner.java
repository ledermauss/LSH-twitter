import java.util.*;
import java.io.*;

/**
 * The Runner can be ran from the commandline to find the most similar pairs of tweets.
 * Example command to run with brute force similarity search:
 * 				java Runner -threshold 0.5 -method bf -maxFiles 100 -inputPath ../data/tweets -outputPath myoutput -shingleLength 3
 * @author Toon Van Craenendonck
 */

public class Runner {

	public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

		String inputPath = "";
		String outputPath = "";
		String algorithm = "lsh"; // expect "brute" for brute force
		int maxFiles = -1;
		int shingleLength = -1;
		int nShingles = -1;
		float threshold = -1;

		int i = 0;
		while (i < args.length && args[i].startsWith("-")) {
			String arg = args[i];
			if(arg.equals("-inputPath")) {
				inputPath = args[i + 1];
			}else if(arg.equals("-maxFiles")){
				maxFiles = Integer.parseInt(args[i+1]);
			}else if(arg.equals("-shingleLength")) {
				shingleLength = Integer.parseInt(args[i + 1]);
			}else if(arg.equals("-nShingles")){
				nShingles = Integer.parseInt(args[i+1]);
			}else if(arg.equals("-threshold")){
				threshold = Float.parseFloat(args[i+1]);
			}else if(arg.equals("-outputPath")){
				outputPath = args[i + 1];
			}else if(arg.equals("-algorithm")){
				algorithm = args[i + 1];
            }
			i += 2;
		}

		Shingler shingler = new Shingler(shingleLength, nShingles);
   		TwitterReader reader = new TwitterReader(maxFiles, shingler, inputPath);
		Search searcher;
		if (algorithm.equals("brute")){
			searcher = new BruteForceSearch(reader);
		} else{
            System.out.println("Performing LSH search");
			searcher = new LSHSearch(reader, 10, 4);
		}
		Set<SimilarPair> similarItems = searcher.getSimilarPairsAboveThreshold(threshold);
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("Time: " +  elapsedTime);
		printPairs(similarItems, outputPath);
	}


	
	/**
	 * Prints pairs and their similarity.
	 * @param similarItems the set of similar items to print
	 * @param outputFile the path of the file to which they will be printed
	 */
	public static void printPairs(Set<SimilarPair> similarItems, String outputFile){
		try {
			File fout = new File(outputFile);
			FileOutputStream fos = new FileOutputStream(fout);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

			List<SimilarPair> sim = new ArrayList<SimilarPair>(similarItems);
			Collections.sort(sim, Collections.reverseOrder());
			for(SimilarPair p : sim){
				bw.write(p.getId1() + "," + p.getId2() + "," + p.getSimilarity());
				bw.newLine();
			}

			bw.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
