/**
 *  The unranked Boolean retrieval model has no parameters.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

public class RetrievalModelIndri extends RetrievalModel {
	private int mu;
	private double lambda;

  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
	public RetrievalModelIndri( int mu, double lambda){
		this.mu = mu;
		this.lambda = lambda;
	}
  public boolean setParameter (String parameterName, double value) {
	  if(parameterName.equals("mu")) mu = (int)value;
	  else if(parameterName.equals("lamda")) lambda = value;
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
  public int getmu() {return mu;}
  public double getlambda() {return lambda;}
}
