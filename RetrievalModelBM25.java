/**
 *  The unranked Boolean retrieval model has no parameters.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

public class RetrievalModelBM25 extends RetrievalModel {
	private double k1;
	private double b;
	private double k3;
	
	
	public RetrievalModelBM25(double k1, double b, double k3){
		this.k1 = k1;
		this.b = b;
		this.k3 = k3;
	}

  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, double value) {
	  if(parameterName.equals("k1")) k1 = value;
	  else if(parameterName.equals("b")) b = value;
	  else if(parameterName.equals("k3")) k3 = value;
	  else{
	  
		  System.err.println ("Error: Unknown parameter name for retrieval model " +
			"UnrankedBoolean: " +
			parameterName);
    return false;
	  }
	  return true;
  }

  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, String value) {
    System.err.println ("Error: Unknown parameter name for retrieval model " +
			"UnrankedBoolean: " +
			parameterName);
    return false;
  }
  
  public double getk1(){ return k1;}
  public double getb(){return b;}
  public double getk3(){return k3;}

}
