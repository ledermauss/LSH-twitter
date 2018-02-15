import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads a directory of documents and constructs shingle representations for these documents.
 * 
 * @author Toon Van Craenendonck
 *
 */
public class TwitterReader {

	Shingler shingler;
	int maxDocs;
	int curDoc;
	String filePath;
	BufferedReader br;

	public TwitterReader(int maxDocs, Shingler shingler, String filePath){
		this.shingler = shingler;
		this.maxDocs = maxDocs;
		this.curDoc = 0;
		this.filePath = filePath;

		try {
			FileInputStream fstream = new FileInputStream(filePath);
			this.br = new BufferedReader(new InputStreamReader(fstream));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Set<Integer> next(){
		while (this.curDoc < this.maxDocs) {
		      try {
	     			String line = br.readLine();
				String[] cols = line.split("\t", -1);
				this.curDoc++;
				if (cols.length >= 3) {  // if the text column is not missing
                    return this.shingler.shingle(cols[2]);
                } else {
                    return this.shingler.shingle("\n"); // to avoid breaking if last line is blank
                }
			} catch (IOException e) {
				e.printStackTrace(); 			
			}
		}
		return null;
	};

	public void reset(){
		try {
			FileInputStream fstream = new FileInputStream(filePath);
			this.br = new BufferedReader(new InputStreamReader(fstream));
			System.gc();
			this.curDoc = 0;
		} catch (IOException e) {
			e.printStackTrace();
		}
	};

	public boolean hasNext(){
		return this.curDoc < this.maxDocs - 1;
	};


}
