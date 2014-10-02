/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;



public class QryEval {

	static String usage = "Usage:  java " + System.getProperty("sun.java.command")
			+ " paramFile\n\n";

	//  The index file reader is accessible via a global variable. This
	//  isn't great programming style, but the alternative is for every
	//  query operator to store or pass this value, which creates its
	//  own headaches.

	public static IndexReader READER;
	public static DocLengthStore dls;

	//  Create and configure an English analyzer that will be used for
	//  query parsing.

	public static EnglishAnalyzerConfigurable analyzer =
			new EnglishAnalyzerConfigurable (Version.LUCENE_43);
	static {
		analyzer.setLowercase(true);
		analyzer.setStopwordRemoval(true);
		analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
	}

	/**
	 *  @param args The only argument is the path to the parameter file.
	 *  @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		// must supply parameter file
		if (args.length < 1) {
			System.err.println(usage);
			System.exit(1);
		}
		long startTime = System.nanoTime();
		// read in the parameter file; one parameter per line in format of key=value
		Map<String, String> params = new HashMap<String, String>();
		Scanner scan = new Scanner(new File(args[0]));
		String line = null;
		do {
			line = scan.nextLine();
			String[] pair = line.split("=");
			params.put(pair[0].trim(), pair[1].trim());
		} while (scan.hasNext());
		scan.close();

		// parameters required for this example to run
		if (!params.containsKey("indexPath") || !params.containsKey("retrievalAlgorithm")
				||!params.containsKey("queryFilePath")||!params.containsKey("trecEvalOutputPath")) {
			System.err.println("Error: Parameters were missing.");
			System.exit(1);
		}


		// open the index
		READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

		if (READER == null) {
			System.err.println(usage);
			System.exit(1);
		}

		dls = new DocLengthStore(READER);

		//Decide model
		RetrievalModel model;
		if(params.get("retrievalAlgorithm").equals("UnrankedBoolean"))
			model = new RetrievalModelUnrankedBoolean();
		else if(params.get("retrievalAlgorithm").equals("RankedBoolean")){
			model = new RetrievalModelRankedBoolean();
		}
		else if(params.get("retrievalAlgorithm").toLowerCase().equals("bm25")){
			double k1 = Double.parseDouble(params.get("BM25:k_1"));
			double b = Double.parseDouble(params.get("BM25:b"));
			double k3 = Double.parseDouble(params.get("BM25:k_3"));
			model = new RetrievalModelBM25(k1,b,k3);
		}
		else if(params.get("retrievalAlgorithm").toLowerCase().equals("indri")){
			int mu = Integer.parseInt(params.get("Indri:mu"));
			double lambda = Double.parseDouble(params.get("Indri:lambda"));
			String smoothing = params.get("Indri:smoothing");
			if(smoothing.equals("ctf")) 
				model = new RetrievalModelIndri(mu, lambda);
			else{
				model = null;
				System.err.println("Error: Unknown Indri smoothing type.");
				System.exit(1);
			}
		}
		else{
			model = null;
			System.err.println("Error: Unknown boolean retrieval type.");
			System.exit(1);
		}


		//Read from query file
		BufferedReader br = null;
		try{
			br = new BufferedReader(new FileReader(new File(params.get("queryFilePath"))));
		}
		catch(IOException e){
			System.err.println("Error: Query file not found");
			System.exit(1);
		}

		BufferedWriter writer = null;

		try{
			writer = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
		}
		catch(IOException e){
			System.err.println("Error: Result file can't be created");
			System.exit(1);
		}
		String singleLine;
		while ((singleLine = br.readLine()) != null) {
			// process the line.
			Qryop qTree;
			int tempindex = singleLine.indexOf(":");
			if(tempindex == -1) continue;
			int queryno = Integer.parseInt(singleLine.substring(0,tempindex).trim());
			String query = singleLine.substring(tempindex+1).trim();
			System.out.println(queryno + " : [" + query + "] start");
			qTree = parseQuery (query, model);
			QryResult res= qTree.evaluate(model);
			printResults (queryno, query, res,writer);
			System.out.println(queryno + " : [" + query + "] done");
			writer.write("\n");
		}
		br.close();
		writer.close();

		// Later HW assignments will use more RAM, so you want to be aware
		// of how much memory your program uses.
		long estimatedTime = System.nanoTime() - startTime;
		System.out.println(TimeUnit.MILLISECONDS.convert(estimatedTime, TimeUnit.NANOSECONDS));
		printMemoryUsage(false);

	}

	/**
	 *  Write an error message and exit.  This can be done in other
	 *  ways, but I wanted something that takes just one statement so
	 *  that it is easy to insert checks without cluttering the code.
	 *  @param message The error message to write before exiting.
	 *  @return void
	 */
	static void fatalError (String message) {
		System.err.println (message);
		System.exit(1);
	}

	/**
	 *  Get the external document id for a document specified by an
	 *  internal document id. If the internal id doesn't exists, returns null.
	 *  
	 * @param iid The internal document id of the document.
	 * @throws IOException 
	 */
	static String getExternalDocid (int iid) throws IOException {
		Document d = QryEval.READER.document (iid);
		String eid = d.get ("externalId");
		return eid;
	}

	/**
	 *  Finds the internal document id for a document specified by its
	 *  external id, e.g. clueweb09-enwp00-88-09710.  If no such
	 *  document exists, it throws an exception. 
	 * 
	 * @param externalId The external document id of a document.s
	 * @return An internal doc id suitable for finding document vectors etc.
	 * @throws Exception
	 */
	static int getInternalDocid (String externalId) throws Exception {
		Query q = new TermQuery(new Term("externalId", externalId));

		IndexSearcher searcher = new IndexSearcher(QryEval.READER);
		TopScoreDocCollector collector = TopScoreDocCollector.create(1,false);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		if (hits.length < 1) {
			throw new Exception("External id not found.");
		} else {
			return hits[0].doc;
		}
	}

	/**
	 * parseQuery converts a query string into a query tree.
	 * 
	 * @param qString
	 *          A string containing a query.
	 * @param qTree
	 *          A query tree
	 * @throws IOException
	 */
	static Qryop parseQuery(String qString, RetrievalModel r) throws IOException {

		Qryop currentOp = null;
		Stack<Qryop> stack = new Stack<Qryop>();

		// Add a default query operator to an unstructured query. This
		// is a tiny bit easier if unnecessary whitespace is removed.

		qString = qString.trim();

		if (qString.charAt(0) != '#') {
			if(r instanceof RetrievalModelRankedBoolean ||
					r instanceof RetrievalModelUnrankedBoolean){
				qString = "#or(" + qString + ")";
			}
			else if(r instanceof RetrievalModelBM25){
				qString = "#sum(" + qString + ")";
			}
			else if(r instanceof RetrievalModelIndri){
				qString = "#and(" + qString + ")";
			}
			else{}
		}
		if(r instanceof RetrievalModelBM25 && !qString.startsWith("#SUM") && !qString.startsWith("#sum")){
			qString = "#sum(" + qString + ")";
		}
		if(r instanceof RetrievalModelIndri && !qString.startsWith("#AND") && !qString.startsWith("#and")){
			qString = "#and(" + qString + ")";
		}
		// Tokenize the query.

		StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
		String token = null;

		// Each pass of the loop processes one token. To improve
		// efficiency and clarity, the query operator on the top of the
		// stack is also stored in currentOp.

		while (tokens.hasMoreTokens()) {

			token = tokens.nextToken();

			if (token.matches("[ ,(\t\n\r]")) {
				// Ignore most delimiters.
			} else if (token.equalsIgnoreCase("#and")) {
				if(r instanceof RetrievalModelIndri){
					currentOp = new QryopSlIndriAnd();
					stack.push(currentOp);
				}
				else{
					currentOp = new QryopSlAnd();
					stack.push(currentOp);
				}
			} else if (token.equalsIgnoreCase("#or")) {
				currentOp = new QryopSlOr();
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#syn")) {
				currentOp = new QryopIlSyn();
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#sum")) {

				currentOp = new QryopSlSum();
				stack.push(currentOp);
			}else if (token.length()>6 && token.substring(0, 6).equalsIgnoreCase("#near/") && isInt(token.substring(6))){
				currentOp = new QryopIlNear(Integer.parseInt(token.substring(6)));
				stack.push(currentOp);
			}
			else if (token.startsWith(")")) { // Finish current query operator.
				// If the current query operator is not an argument to
				// another query operator (i.e., the stack is empty when it
				// is removed), we're done (assuming correct syntax - see
				// below). Otherwise, add the current operator as an
				// argument to the higher-level operator, and shift
				// processing back to the higher-level operator.

				stack.pop();

				if (stack.empty())
					break;

				Qryop arg = currentOp;
				currentOp = stack.peek();
				currentOp.add(arg);
			} else {

				// NOTE: You should do lexical processing of the token before
				// creating the query term, and you should check to see whether
				// the token specifies a particular field (e.g., apple.title).
				int indexTmp = token.lastIndexOf(".");
				if(indexTmp != -1){	
					String curField = token.substring(indexTmp+1);
					String[] tokenArray = tokenizeQuery(token.substring(0, indexTmp));
					for(int j=0;j<tokenArray.length;j++)
						currentOp.add(new QryopIlTerm(tokenArray[j], curField));
				}
				else{
					String[] tokenArray = tokenizeQuery(token);
					for(int j=0;j<tokenArray.length;j++)
						currentOp.add(new QryopIlTerm(tokenArray[j]));
				}
			}
		}

		// A broken structured query can leave unprocessed tokens on the
		// stack, so check for that.

		if (tokens.hasMoreTokens()) {
			System.err.println("Error:  Query syntax is incorrect.  " + qString);
			return null;
		}
		if(currentOp instanceof QryopIl){
			if(r instanceof RetrievalModelRankedBoolean ||
					r instanceof RetrievalModelUnrankedBoolean){
				Qryop opnew = currentOp;
				currentOp = new QryopSlScore();
				currentOp.add(opnew);
			}
			else if(r instanceof RetrievalModelBM25){
				Qryop opnew = currentOp;
				currentOp = new QryopSlSum();
				currentOp.add(opnew);
			}
			else if(r instanceof RetrievalModelIndri){
				Qryop opnew = currentOp;
				currentOp = new QryopSlIndriAnd();
				currentOp.add(opnew);
			}
			else{}

		}
		return currentOp;
	}

	/**
	 *  Print a message indicating the amount of memory used.  The
	 *  caller can indicate whether garbage collection should be
	 *  performed, which slows the program but reduces memory usage.
	 *  @param gc If true, run the garbage collector before reporting.
	 *  @return void
	 */
	public static void printMemoryUsage (boolean gc) {

		Runtime runtime = Runtime.getRuntime();

		if (gc) {
			runtime.gc();
		}

		System.out.println ("Memory used:  " +
				((runtime.totalMemory() - runtime.freeMemory()) /
						(1024L * 1024L)) + " MB");
	}

	/**
	 * Print the query results. 
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT.  YOU MUST CHANGE THIS
	 * METHOD SO THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK
	 * PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryName Original query.
	 * @param result Result object generated by {@link Qryop#evaluate()}.
	 * @throws IOException 
	 */
	static void printResults(int queryno, String queryName, QryResult result, BufferedWriter writer) throws IOException {
		class Entry{
			public String exID;
			public double score;
			public Entry(String exID, double score){
				this.exID = exID;

				this.score = score;
			}
		}
		ArrayList<Entry> scores = new ArrayList<Entry>();
		for(int i=0;i<result.docScores.scores.size();i++){
			Entry temp= new Entry(getExternalDocid
					(((ScoreList.ScoreListEntry)result.docScores.scores.get(i)).getDocID()),
					((ScoreList.ScoreListEntry)result.docScores.scores.get(i)).getScore());
			scores.add(temp);
		}
		Collections.sort(scores, new Comparator() 
		{
			public int compare(Object str1, Object str2)
			{
				double score1 = ((Entry)str1).score;
				double score2 = ((Entry)str2).score;
				if(score1 > score2) return -1;
				else if(score1 < score2) return 1;
				else{
					
					return ((Entry)str1).exID.compareTo(((Entry)str2).exID);
				}
			}
		});
		/*Collections.sort(result.docScores.scores, new Comparator() 
	    {
			public int compare(Object str1, Object str2)
			{
				double score1 = ((ScoreList.ScoreListEntry)str1).getScore();
				double score2 = ((ScoreList.ScoreListEntry)str2).getScore();
				if(score1 > score2) return -1;
				else if(score1 < score2) return 1;
				else{
					String exid1 = "Not found";
					String exid2 = "Not found";
					try{
						exid1 = getExternalDocid(((ScoreList.ScoreListEntry)str1).getDocID());
						exid2 = getExternalDocid(((ScoreList.ScoreListEntry)str2).getDocID());
					}
					catch(IOException e){}

					return exid1.compareTo(exid2);
				}
			}
	    });*/
		if(writer == null){
	    System.out.println(queryName + ":  ");
	    if (scores.size() < 1) {
	      System.out.println("\tNo results.");
	    } else {
	      for (int i = 0; i < Math.min(result.docScores.scores.size(),100); i++) {
	        System.out.println("\t" + i + ":  "
				   + scores.get(i).exID
				   + ", "
				   + scores.get(i).score);
	      }
	    }
		}
		else{
			//writer.write(queryName + ":  ");
		    if (scores.size() < 1) {
		    	writer.write("10 Q0 dummy 1 0 run-1");
		    } else {
		      for (int i = 0; i < Math.min(result.docScores.scores.size(),100); i++) {
		    	  String temp = queryno + "  Q0 "
						   + scores.get(i).exID
						   + " "+ (i+1) + " "
						   + scores.get(i).score + " run-1";
		    	  if(i==0) writer.write(temp);
		    	  else writer.write("\n" + temp);
		      }
		    }
		}
	}

	/**
	 *  Given a query string, returns the terms one at a time with stopwords
	 *  removed and the terms stemmed using the Krovetz stemmer. 
	 * 
	 *  Use this method to process raw query terms. 
	 * 
	 *  @param query String containing query
	 *  @return Array of query tokens
	 *  @throws IOException
	 */
	static String[] tokenizeQuery(String query) throws IOException {

		TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
		TokenStream tokenStream = comp.getTokenStream();

		CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
		tokenStream.reset();

		List<String> tokens = new ArrayList<String>();
		while (tokenStream.incrementToken()) {
			String term = charTermAttribute.toString();
			tokens.add(term);
		}
		return tokens.toArray(new String[tokens.size()]);
	}

	public static boolean isInt(String str){
		try{
			Integer.parseInt(str);
			return true;
		}
		catch(NumberFormatException e){ return false;}
	}
}
