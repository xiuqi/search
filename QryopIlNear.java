import java.io.*;
import java.util.*;


public class QryopIlNear extends QryopIl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
   */
	
  private int distance = 0;
  
  public QryopIlNear(int dis) {
	    distance = dis;
  }
  
  public QryopIlNear(int dis, Qryop... q) {
	  for (int i = 0; i < q.length; i++)
	      this.args.add(q[i]);
      distance = dis;
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param {q} q The query argument (query operator) to append.
   *  @return void
   *  @throws IOException
   */
  
  public void add (Qryop a) {
    this.args.add(a);
  }

  /**
   *  Evaluates the query operator, including any child operators and
   *  returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    //  Initialization
    allocDaaTPtrs (r);
    syntaxCheckArgResults (this.daatPtrs);

    QryResult result = new QryResult ();
    result.invertedList.field = new String (this.daatPtrs.get(0).invList.field);

    
    
    DaaTPtr ptr0 = this.daatPtrs.get(0);

    EVALUATEDOCUMENTS:
    for ( ; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc ++) {

      int ptr0Docid = ptr0.invList.getDocid (ptr0.nextDoc);

      //  Do the other query arguments have the ptr0Docid?

      for (int j=1; j<this.daatPtrs.size(); j++) {

	DaaTPtr ptrj = this.daatPtrs.get(j);

	while (true) {
	  if (ptrj.nextDoc >= ptrj.invList.postings.size())
	    break EVALUATEDOCUMENTS;		// No more docs can match
	  else
	    if (ptrj.invList.getDocid (ptrj.nextDoc) > ptr0Docid)
	      continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
	  else
	    if (ptrj.invList.getDocid (ptrj.nextDoc) < ptr0Docid)
	      ptrj.nextDoc ++;			// Not yet at the right doc.
	  else
	      break;				// ptrj matches ptr0Docid
	}
      }
      //  The ptr0Docid matched all query arguments, so we can step forward and get positions
      List<Integer> positions = new ArrayList<Integer>();
      ArrayList<Integer> pointers = new ArrayList<Integer>(daatPtrs.size());
      for(int i=0;i<daatPtrs.size();i++){
    	  pointers.add(0);
      }
      while(true){
    	  boolean stop = false;
    	  for(int i=0;i<daatPtrs.size()-1;i++){
    		  DaaTPtr ptr1 = this.daatPtrs.get(i);
    		  DaaTPtr ptr2 = this.daatPtrs.get(i+1);
    		  List<Integer> pos1 = ptr1.invList.postings.get(ptr1.nextDoc).positions;
    		  List<Integer> pos2 = ptr2.invList.postings.get(ptr2.nextDoc).positions;
    		  if( pointers.get(i+1) >= pos2.size() ){stop = true; break;}
    		  if( pointers.get(i) >= pos1.size() ) {stop = true; break;}
    		  int dif = pos2.get(pointers.get(i+1)) - pos1.get(pointers.get(i));
    		  if(dif > 0 && dif <= distance) continue;
    		  if(dif <=0 ){//If the second one is too small
    			  pointers.set(i+1, pointers.get(i+1) + 1);
    			  i = -1;//Restart from the beginning
    			  continue;
    		  }
    		  if(dif > distance){//If the first one is too small
    			  pointers.set(i, pointers.get(i) + 1);
    			  i = -1;
    			  continue;
    		  }
    	  }
    	  if(stop) break;
    	  else{ //We find a pair
    		  positions.add(ptr0.invList.postings.get(ptr0.nextDoc).positions.get(pointers.get(0)));
    		  for(int i=0;i<daatPtrs.size();i++){
    			  pointers.set(i, pointers.get(i)+1);
    		  }
    	  }
      }
      if(positions.size()!=0){
    	  int docid = this.daatPtrs.get(0).invList.getDocid(this.daatPtrs.get(0).nextDoc);
    	  result.invertedList.appendPosting (docid, positions);
      }
    }
    freeDaaTPtrs();
    return result;
  }

  /**
   *  syntaxCheckArgResults does syntax checking that can only be done
   *  after query arguments are evaluated.
   *  @param ptrs A list of DaaTPtrs for this query operator.
   *  @return True if the syntax is valid, false otherwise.
   */
  public Boolean syntaxCheckArgResults (List<DaaTPtr> ptrs) {

    for (int i=0; i<this.args.size(); i++) {

      if (! (this.args.get(i) instanceof QryopIl)) 
	QryEval.fatalError ("Error:  Invalid argument in " +
			    this.toString());
      else
    //To call NEAR, must be in the same field
	if ((i>0) &&
	    (! ptrs.get(i).invList.field.equals (ptrs.get(0).invList.field)))
	  QryEval.fatalError ("Error:  Arguments must be in the same field:  " +
			      this.toString());
    }
    
    if(this.daatPtrs.size()<2)
    	QryEval.fatalError("Error: NEAR operator should have at least two parameters.");
    if(distance < 1)
    	QryEval.fatalError("Error: NEAR operator have invalid distance value.");
    return true;
  }
  
  
  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#NEAR( " + result + ")");
  }
}
