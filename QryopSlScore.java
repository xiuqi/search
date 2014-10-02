/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlScore extends QryopSl {
	private int ctf = -1;
	private String field = "";
	/**
	 *  Construct a new SCORE operator.  The SCORE operator accepts just
	 *  one argument.
	 *  @param q The query operator argument.
	 *  @return @link{QryopSlScore}
	 */
	public QryopSlScore(Qryop q) {
		this.args.add(q);
	}

	/**
	 *  Construct a new SCORE operator.  Allow a SCORE operator to be
	 *  created with no arguments.  This simplifies the design of some
	 *  query parsing architectures.
	 *  @return @link{QryopSlScore}
	 */
	public QryopSlScore() {
	}

	/**
	 *  Appends an argument to the list of query operator arguments.  This
	 *  simplifies the design of some query parsing architectures.
	 *  @param q The query argument to append.
	 */
	public void add (Qryop a) {
		this.args.add(a);
	}

	/**
	 *  Evaluate the query operator.
	 *  @param r A retrieval model that controls how the operator behaves.
	 *  @return The result of evaluating the query.
	 *  @throws IOException
	 */
	public QryResult evaluate(RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelUnrankedBoolean)
			return (evaluateBoolean (r));
		if (r instanceof RetrievalModelRankedBoolean)
			return (evaluateBoolean(r));
		if (r instanceof RetrievalModelBM25)
			return (evaluateBM25(r));
		if (r instanceof RetrievalModelIndri)
			return (evaluateIndri(r));

		return null;
	}

	/**
	 *  Evaluate the query operator for boolean retrieval models.
	 *  @param r A retrieval model that controls how the operator behaves.
	 *  @return The result of evaluating the query.
	 *  @throws IOException
	 */
	public QryResult evaluateBM25(RetrievalModel r) throws IOException {

		// Evaluate the query argument.

		QryResult result = args.get(0).evaluate(r);

		// Each pass of the loop computes a score for one document. Note:
		// If the evaluate operation above returned a score list (which is
		// very possible), this loop gets skipped.
		for( int i=0; i< result.invertedList.df; i++){
			String field = result.invertedList.field;
			int id = result.invertedList.postings.get(i).docid;
			int df = result.invertedList.df;
			int N = QryEval.READER.getDocCount(field);
			int tf = result.invertedList.postings.get(i).tf;
			double avglen = QryEval.READER.getSumTotalTermFreq(field) /
					(float) QryEval.READER.getDocCount (field);
			long doclen = QryEval.dls.getDocLength(field, id);
			double k1 = ((RetrievalModelBM25)r).getk1();
			double b = ((RetrievalModelBM25)r).getb();
			double score = Math.log((N-df+0.5)/(df+0.5))*tf*1.0/(tf+k1*(1-b+b*doclen/avglen));
			result.docScores.add(id, score);
		}

		return result;
	}

	public QryResult evaluateBoolean(RetrievalModel r) throws IOException{

	    // Evaluate the query argument.

	    QryResult result = args.get(0).evaluate(r);

	    // Each pass of the loop computes a score for one document. Note:
	    // If the evaluate operation above returned a score list (which is
	    // very possible), this loop gets skipped.

	    for (int i = 0; i < result.invertedList.df; i++) {

	      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY. 
	      // Unranked Boolean. All matching documents get a score of 1.0.
	    	if (r instanceof RetrievalModelUnrankedBoolean)
	    		result.docScores.add(result.invertedList.postings.get(i).docid,
				   (float) 1.0);
	    	else if(r instanceof RetrievalModelRankedBoolean) //For ranked, use tf as the score
	    		result.docScores.add(result.invertedList.postings.get(i).docid,
	    				   result.invertedList.postings.get(i).tf);
	    }

	    // The SCORE operator should not return a populated inverted list.
	    // If there is one, replace it with an empty inverted list.

	    if (result.invertedList.df > 0)
		result.invertedList = new InvList();
	    

	    return result;
	  }
	
	public QryResult evaluateIndri(RetrievalModel r) throws IOException {

		// Evaluate the query argument.
		
		QryResult result = args.get(0).evaluate(r);
		this.ctf = result.invertedList.ctf;
		this.field = result.invertedList.field;
		// Each pass of the loop computes a score for one document. Note:
		// If the evaluate operation above returned a score list (which is
		// very possible), this loop gets skipped.
		for( int i=0; i< result.invertedList.df; i++){
			String field = result.invertedList.field;
			int id = result.invertedList.postings.get(i).docid;
			double lambda = ((RetrievalModelIndri)r).getlambda();
			int mu = ((RetrievalModelIndri)r).getmu();
			double mle = (result.invertedList.ctf)*1.0/QryEval.READER.getSumTotalTermFreq(field);
			int tf = result.invertedList.postings.get(i).tf;
			long doclen = QryEval.dls.getDocLength(field, id);
			double score = lambda*(tf+mu*mle)/(doclen+mu) + (1-lambda)*mle;
			result.docScores.add(id, score);
		}
		return result;
	}
	
	/*
	 *  Calculate the default score for a document that does not match
	 *  the query argument.  This score is 0 for many retrieval models,
	 *  but not all retrieval models.
	 *  @param r A retrieval model that controls how the operator behaves.
	 *  @param docid The internal id of the document that needs a default score.
	 *  @return The default score.
	 */


	public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

		if (r instanceof RetrievalModelUnrankedBoolean)
			return (0.0);
		else if(r instanceof RetrievalModelRankedBoolean)
			return 0.0;
		else if (r instanceof RetrievalModelIndri){
			//QryResult result = args.get(0).evaluate(r);
			String field = this.field;
			double lambda = ((RetrievalModelIndri)r).getlambda();
			int mu = ((RetrievalModelIndri)r).getmu();
			double mle = (this.ctf)*1.0/QryEval.READER.getSumTotalTermFreq(field);
			long doclen = QryEval.dls.getDocLength(field, (int)docid);
			return lambda*(0+mu*mle)/(doclen+mu) + (1-lambda)*mle;
		}
		return 0.0;
	}

	/**
	 *  Return a string version of this query operator.  
	 *  @return The string version of this query operator.
	 */
	public String toString(){

		String result = new String ();

		for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
			result += (i.next().toString() + " ");

		return ("#SCORE( " + result + ")");
	}
}
