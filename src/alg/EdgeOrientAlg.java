/*
 * Copyright (c) 2009, Anthony Gitter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of Carnegie Mellon University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package alg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

//import lpsolve.LpSolve;
//import lpsolve.LpSolveException;

/**
 * Runs the edge orientation algorithm of choice.
 *
 */
public class EdgeOrientAlg {

	private Graph graph;
	private ArrayList<Path> paths;
	/** A list of all edges whose orientation is in conflict. That
	 * is, at least one path wishes to use them in each direction. */
	private ArrayList<UndirEdge> conflictEdges;
	/** Stores the best found orientations for the conflict edges */
	private int[] savedOrientations;

	int configsChecked;

	private static final int MAX_DEPTH = 5;
	/** The default number of times to restart the randomized rounding
	 * portion of the minSatSln algorithm */
	private static final int BERTSIMAS_ROUND_RESTARTS = 100;
	/** The default number of times to restart the randomized orientation
	 * followed by local search in randPlusSearchSln */
	private static final int RAND_PLUS_SEARCH_RESTARTS = 10;

	/**
	 * @param g the Graph must have already been initialized with the
	 * edges and vertices
	 */
	public EdgeOrientAlg(Graph g)
	{
		graph = g;
	}


	/**
	 * Randomly orients the conflict edges with randomOrient and then
	 * performs local search with localSearchSln.  Repeats this
	 * process the default number of times.  Returns the global
	 * score of the best found configuration and sets the conflict edges
	 * to have this orientation.
	 * 
	 * @return
	 */
	public double randPlusSearchSln()
	{
		return randPlusSearchSln(RAND_PLUS_SEARCH_RESTARTS);
	}

	/**
	 * Randomly orients the conflict edges with randomOrient and then
	 * performs local search with localSearchSln.  Repeats this
	 * process the specified number of times.  Returns the global
	 * score of the best found configuration and sets the conflict edges
	 * to have this orientation.
	 * @param iterations
	 * @return
	 */
	public double randPlusSearchSln(int iterations)
	{
		double bestGlobal = Double.NEGATIVE_INFINITY;

		for(int rep = 0; rep < iterations; rep++)
		{
			randomOrient();
			localSearchSln();

			// If a new best configuration is found, save it
			double newGlobal = globalScore();
			if(newGlobal > bestGlobal)
			{
				bestGlobal = newGlobal;
				saveConflictOrientations();
			}	
		}

		// Reload the best conflict edge orientations in case the caller
		// wishes to access it
		loadConflictOrientations();
		graphStateChanged();

		System.out.println("\nBest random + edge flip local search after " + 
				iterations + " iterations: " + bestGlobal);
		System.out.println("Max possible: " + maxGlobalScore() + "\n");

		return bestGlobal;		
	}
	
	
	/**
	 * Randomly orients the conflict edges with randomOrient.  Repeats this
	 * process the default number of times.  Returns the global
	 * score of the best found configuration and sets the conflict edges
	 * to have this orientation.
	 * @param iterations
	 * @return
	 */
	public double randSln()
	{
		return randSln(RAND_PLUS_SEARCH_RESTARTS);
	}
	
	
	/**
	 * Randomly orients the conflict edges with randomOrient.  Repeats this
	 * process the specified number of times.  Returns the global
	 * score of the best found configuration and sets the conflict edges
	 * to have this orientation.
	 * @param iterations
	 * @return
	 */
	public double randSln(int iterations)
	{
		double bestGlobal = Double.NEGATIVE_INFINITY;

		for(int rep = 0; rep < iterations; rep++)
		{
			randomOrient();

			// If a new best configuration is found, save it
			double newGlobal = globalScore();
			if(newGlobal > bestGlobal)
			{
				bestGlobal = newGlobal;
				saveConflictOrientations();
			}	
		}

		// Reload the best conflict edge orientations in case the caller
		// wishes to access it
		loadConflictOrientations();
		graphStateChanged();

		System.out.println("\nBest random orientation after " + 
				iterations + " iterations: " + bestGlobal);
		System.out.println("Max possible: " + maxGlobalScore() + "\n");

		return bestGlobal;		
	}


	/**
	 * Iteratively flips the edge
	 * that yields the larges global score increase until a local
	 * maximum is reached.  If any of the conflict edges has not been 
	 * oriented (it's orientation is Edge.UNORIENTED) all edges will
	 * be randomly oriented first.
	 * 
	 * Changes the state of the stored Graph.
	 * 
	 * 
	 * @return the global score
	 */
	public double localSearchSln()
	{
		// Randomly orient all conflict edges if any edges have not
		// been assigned an orientation
		// This automatically finds paths and the conflict edges if necessary
		if(!conflictEdgesOriented())
		{
			System.out.println("Conflict edges not oriented so performing " +
			"random orientation");
			randomOrient();
		}

		double global = globalScore();
		int counter = 0;

		// Don't try to flip edges if there are no conflict edges
		if(conflictEdges.size() > 0)
		{
			long start = System.currentTimeMillis();
			System.out.println("Beginning edge flip local search");
			double oldGlobal = Double.NEGATIVE_INFINITY;

			// Iterate as long as flipping an edge will yield an improvement
			while(oldGlobal < global)
			{
				counter++;
				oldGlobal = global;

				// Find the edge whose flip will yield the greatest
				// increase in global score
				UndirEdge bestEdge = conflictEdges.get(0);
				double bestEdgeDelta = Double.NEGATIVE_INFINITY;
				for(UndirEdge e : conflictEdges)
				{
					double curDelta = e.computeFlipDelta();
					if(bestEdgeDelta < curDelta)
					{
						bestEdge = e;
						bestEdgeDelta = curDelta;
					}
				}

				// Only perform the flip if it will give a positive change
				// in the global objective function
				if(bestEdgeDelta > 0)
				{
					bestEdge.flip();
					global += bestEdgeDelta;
				}

			} // end local search loop

			long stop = System.currentTimeMillis();
			System.out.println("Finished local search");
			System.out.println("Time (ms): " + (stop - start) + "\n");
			
			graphStateChanged();

		} // end check for conflictEdges.size() > 0

		global = globalScore();
		System.out.println("\nEdge flip local search: " + global);
		System.out.println("Max possible: " + maxGlobalScore() + "\n");

		return global;
	}


	/**
	 * Get the LP value of the problem formlation and use it
	 * to upper bound the actual optimum.
	 * 
	 * Changes the state of the stored Graph.
	 * 
	 * @return the max global score - the value of the LP objective function
	 * @throws LpSolveException
	 */
	// Remove references to LpSolve
	/*
	public double lpValue() throws LpSolveException
	{
		// Make sure that the paths and conflict edges
		// have already been identified
		if(paths == null || conflictEdges == null)
		{
			findConflicts();
		}

		long start = System.currentTimeMillis();
		System.out.println("Creating the linear program");

		// Set ids based on the array list index so that Paths can
		// easily formulate the linear program constraints
		// with their references to the edges they consist of
		for(int e = 0; e < conflictEdges.size(); e++)
		{
			conflictEdges.get(e).setId(e);
		}

		// Identify those paths that use conflict edges.  The other paths
		// will be satisfied no matter what and do not need to be incluced
		// in the linear program
		ArrayList<Path> conflictPaths = new ArrayList<Path>(paths.size());
		for(Path p : paths)
		{
			if(p.hasConflicts())
			{
				conflictPaths.add(p);
			}
		}

		int numCp = conflictPaths.size();
		int numCe = conflictEdges.size();
		int numCv = numCp + numCe;

		// Create an LP instance with (# conflict edges + # conflict paths) variables and
		// 0 initial constraints.
		// The variables corresponding to paths will be be variables indexed 1 through
		// numCp and the edge variables are those from (numCp + 1) to (numCp +numCe)
		// The LP variables are 1-indexed
		LpSolve lpSolver = LpSolve.makeLp(0, numCp + numCe);


		// Construct the objective function.  Path are weighted with their maximum
		// possible weight and variables are not weighted.
		// colno stores the column number (variable number) the corresponding
		// coefficient in the row array refers to
		int[] colno = new int[numCp];
		// row stores the coefficients for the variables
		double[] row = new double[numCp];

		// Iterate through all conflict paths to get their maximum weight
		for(int p = 0; p < numCp; p++)
		{
			// The linear program columns/variables are 1-indexed
			colno[p] = p + 1;
			row[p] = conflictPaths.get(p).maxWeight();
		}

		// Set the objective function
		lpSolver.setObjFnex(numCp, row, colno);

		// This is a minimization problem.  We are minimizing the sum
		// of the weights of paths that are not connected from source
		// to target
		lpSolver.setMinim();

		// Set to add row mode so that adding constraints row-by-row is faster
		lpSolver.setAddRowmode(true);

		// Add contraints for each conflict path
		for(int p = 0; p < numCp; p++)
		{
			conflictPaths.get(p).addLpConstraints(lpSolver, p + 1, numCp + 1);

			if((p + 1) % 10000 == 0)
			{
				System.out.println("Added constraints for " + (p + 1) +
						" of " + numCp + " conflict paths");
			}
		}
		System.out.println("Added constraints for all conflict paths");



		// Set the upper bounds on all path and edge variables
		// They all must be <= 1 because this is a relaxation of
		// a 0-1 integer program.  The >= 0 lower bound is already
		// in place by default
		for(int v = 1; v <= numCv; v++)
		{
			// The upper bound of variable v is 1
			lpSolver.setUpbo(v, 1);
		}


		lpSolver.setAddRowmode(false);

		long stop = System.currentTimeMillis();
		System.out.println("Time (ms) to create the linear program: " +
				(stop - start) + "\n");


		start = System.currentTimeMillis();;
		System.out.println("Solving the linear program");

		// Run the linear program solver
		// See http://lpsolve.sourceforge.net/5.5/solve.htm for possible return values
		int retVal = lpSolver.solve();
		if(retVal != LpSolve.OPTIMAL)
		{
			throw new LpSolveException("Could not find optimal solution: " + retVal);
		}

		// If the optimal solution was found store the variables' values.
		// Row will contain the path variables' values followed by the
		// edge variables'
		row = new double[numCv];
		lpSolver.getVariables(row);

		stop = System.currentTimeMillis();
		System.out.println("Time (ms) to solve the linear program: " +
				(stop - start) + "\n");
		
		double lpValMin = lpSolver.getObjective();
		double maxGlobal = maxGlobalScore();
		double lpValMax = maxGlobal - lpValMin;


		// Report the results
		System.out.println("Original minimization LP value: " + lpValMin);
		System.out.println("Max possible - LP value: " + lpValMax);
		System.out.println("Max possible: " + maxGlobal + "\n");

		return lpValMax;
	}
	*/
	
	
	
	/**
	 * Uses the Min-SAT algorithm of Bertsimas et al. from
	 * "On dependent randomized rounding algorithms" to obtain
	 * an edge orientation.  Involves solving a linear programming
	 * relaxation and using a dependent randomized rounding scheme
	 * to obtain orients from the linear program solution.
	 * 
	 * Changes the state of the stored Graph.
	 * 
	 * 
	 * @return the global score
	 * @throws LpSolveException
	 */
	// Remove references to LpSolve
	/*
	public double minSatSln() throws LpSolveException
	{
		return minSatSln(BERTSIMAS_ROUND_RESTARTS);
	}
	*/


	/**
	 * Uses the Min-SAT algorithm of Bertsimas et al. from
	 * "On dependent randomized rounding algorithms" to obtain
	 * an edge orientation.  Involves solving a linear programming
	 * relaxation and using a dependent randomized rounding scheme
	 * to obtain orients from the linear program solution.
	 * 
	 * Changes the state of the stored Graph.
	 * 
	 * @param randRoundItr the number of times to perform the randomized
	 * rounding after solving the linear program relaxation
	 * @return the global score
	 * @throws LpSolveException
	 */
	// Remove references to LpSolve
	/*
	public double minSatSln(int randRoundItr) throws LpSolveException
	{
		// Make sure that the paths and conflict edges
		// have already been identified
		if(paths == null || conflictEdges == null)
		{
			findConflicts();
		}

		long start = System.currentTimeMillis();
		System.out.println("Creating the linear program");

		// Set ids based on the array list index so that Paths can
		// easily formulate the linear program constraints
		// with their references to the edges they consist of
		for(int e = 0; e < conflictEdges.size(); e++)
		{
			conflictEdges.get(e).setId(e);
		}

		// Identify those paths that use conflict edges.  The other paths
		// will be satisfied no matter what and do not need to be incluced
		// in the linear program
		ArrayList<Path> conflictPaths = new ArrayList<Path>(paths.size());
		for(Path p : paths)
		{
			if(p.hasConflicts())
			{
				conflictPaths.add(p);
			}
		}

		int numCp = conflictPaths.size();
		int numCe = conflictEdges.size();
		int numCv = numCp + numCe;

		// Create an LP instance with (# conflict edges + # conflict paths) variables and
		// 0 initial constraints.
		// The variables corresponding to paths will be be variables indexed 1 through
		// numCp and the edge variables are those from (numCp + 1) to (numCp +numCe)
		// The LP variables are 1-indexed
		LpSolve lpSolver = LpSolve.makeLp(0, numCp + numCe);


		// Construct the objective function.  Path are weighted with their maximum
		// possible weight and variables are not weighted.
		// colno stores the column number (variable number) the corresponding
		// coefficient in the row array refers to
		int[] colno = new int[numCp];
		// row stores the coefficients for the variables
		double[] row = new double[numCp];

		// Iterate through all conflict paths to get their maximum weight
		for(int p = 0; p < numCp; p++)
		{
			// The linear program columns/variables are 1-indexed
			colno[p] = p + 1;
			row[p] = conflictPaths.get(p).maxWeight();
		}

		// Set the objective function
		lpSolver.setObjFnex(numCp, row, colno);

		// This is a minimization problem.  We are minimizing the sum
		// of the weights of paths that are not connected from source
		// to target
		lpSolver.setMinim();

		// Set to add row mode so that adding constraints row-by-row is faster
		lpSolver.setAddRowmode(true);

		// Add contraints for each conflict path
		for(int p = 0; p < numCp; p++)
		{
			conflictPaths.get(p).addLpConstraints(lpSolver, p + 1, numCp + 1);

			if((p + 1) % 10000 == 0)
			{
				System.out.println("Added constraints for " + (p + 1) +
						" of " + numCp + " conflict paths");
			}
		}
		System.out.println("Added constraints for all conflict paths");



		// Set the upper bounds on all path and edge variables
		// They all must be <= 1 because this is a relaxation of
		// a 0-1 integer program.  The >= 0 lower bound is already
		// in place by default
		for(int v = 1; v <= numCv; v++)
		{
			// The upper bound of variable v is 1
			lpSolver.setUpbo(v, 1);
		}


		lpSolver.setAddRowmode(false);

		long stop = System.currentTimeMillis();
		System.out.println("Time (ms) to create the linear program: " +
				(stop - start) + "\n");


		start = System.currentTimeMillis();;
		System.out.println("Solving the linear program");

		// Run the linear program solver
		// See http://lpsolve.sourceforge.net/5.5/solve.htm for possible return values
		int retVal = lpSolver.solve();
		if(retVal != LpSolve.OPTIMAL)
		{
			throw new LpSolveException("Could not find optimal solution: " + retVal);
		}

		// If the optimal solution was found store the variables' values.
		// Row will contain the path variables' values followed by the
		// edge variables'
		row = new double[numCv];
		lpSolver.getVariables(row);

		stop = System.currentTimeMillis();
		System.out.println("Time (ms) to solve the linear program: " +
				(stop - start) + "\n");


		// Use the randomized rounding scheme in Bertsimas et al.
		// to determine the edges' orientations from the linear
		// program solution.  Repeat it the specified number
		// of times
		double bestGlobal = Double.NEGATIVE_INFINITY;
		for(int rep = 0; rep < randRoundItr; rep++)
		{
			bertsimasRound(row, numCp);

			// If a new best configuration is found, save it
			double newGlobal = globalScore();

			if(newGlobal > bestGlobal)
			{
				bestGlobal = newGlobal;
				saveConflictOrientations();
			}	    	
		}
		// Reload the best conflict edge orientations in case the caller
		// wishes to access it or perform local search
		loadConflictOrientations();
		graphStateChanged();

		// Report the results
		System.out.println("\nMIN-kSAT approximation: " + bestGlobal);
		System.out.println("Max possible: " + maxGlobalScore() + "\n");

		return bestGlobal;
	}
	*/


	/**
	 * Use a dependent randomized rounding scheme to obtain
	 * edge orientations from the solution to the linear program
	 * forumulation described in Bertsimas et al.  After calling
	 * this function, all edges in the conflictEdges list will
	 * have their orientation set.
	 * 
	 * It is assumed
	 * that the edge variables are in the same order as the edges in
	 * the global conflictEdges list.
	 * @param lpSolution The values of the variables in the optimal
	 * linear program solution.  Path variables come first followed by
	 * edge variables.
	 * @param numCp The number of conflict paths, which gives the
	 * number of path variables in the set of variables in the linear
	 * program.
	 */
	private void bertsimasRound(double[] lpSolution, int numCp)
	{
		if(conflictEdges == null)
		{
			throw new IllegalStateException("Cannot set the conflict edges' orientations " +
			"because the conflict edges have not yet been identified");
		}

		int numCe = lpSolution.length - numCp;

		// The first step is to generate U from [0,1].
		// Technically U will be in the range [0,1) but the probability
		// of getting 1.0 exaclty is so low it makes no practical difference
		Random rand = new Random();
		double U = rand.nextDouble();

		// Iterate through all edge variables to assign an orientation
		// to the corresponding conflict edge
		for(int e = 0; e < numCe; e++)
		{
			UndirEdge curEdge = conflictEdges.get(e);
			double edgeLpVal = lpSolution[e + numCp];

			// The edge variable is in set A with probability 1/2 and set
			// B with probabiilty 1/2
			if(rand.nextDouble() < 0.5)
			{
				// The edge variable is in set A so orient the edge in the
				// forward direction iff it's LP solution value is > U
				if(edgeLpVal > U)
				{
					curEdge.setOrientation(Edge.FORWARD);
				}
				else
				{
					curEdge.setOrientation(Edge.BACKWARD);
				}
			}
			else
			{
				// The edge variable is in set B so orient the edge in the
				// forward direction iff it's LP solution value is > (1 - U)
				if(edgeLpVal > (1 - U))
				{
					curEdge.setOrientation(Edge.FORWARD);
				}
				else
				{
					curEdge.setOrientation(Edge.BACKWARD);
				}
			}
		}
	}
		
	/**
	 * Uses toulbar2 (http://carlit.toulouse.inra.fr/cgi-bin/awki.cgi/ToolBarIntro),
	 * a weighted CSP solver, to oriented all conflict edges.
	 * 
	 * Generates an input file for the toulbar2 program
	 * @param toulbar2Instance the name of file to be written that will be
	 * used as input for toulbar2
	 * @throws IOException
	 */
	public void genToulbar2(String toulbar2Instance) throws IOException
	{
		// Make sure that the paths and conflict edges
		// have already been identified
		if(paths == null || conflictEdges == null)
		{
			findConflicts();
		}

		long start = System.currentTimeMillis();
		PrintWriter writer = new PrintWriter(new FileWriter(toulbar2Instance));

		// Identify those paths that use conflict edges.  The other paths
		// will be satisfied no matter what and do not need to be incluced
		// in the linear program
		ArrayList<Path> conflictPaths = new ArrayList<Path>(paths.size());
		for(Path p : paths)
		{
			if(p.hasConflicts())
			{
				conflictPaths.add(p);
			}
		}

		int numCp = conflictPaths.size();
		int numCe = conflictEdges.size();

		// Print the XCSP 2.1 format headers
		writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writer.println("<instance>");
		writer.println("<presentation name=\"EdgeOrientation\" " +
				"format=\"XCSP 2.1\" type=\"WCSP\"/>\n");
		
		// The only variables are binary so only one domain is needed
		writer.println("<domains nbDomains=\"1\">");
		writer.println("<domain name=\"D0\" nbValues=\"2\">0..1</domain>");
		writer.println("</domains>\n");
		
		
		// Create a variable for each conflict edge.  Set the 
		// edge ids at the same time so that Paths can
		// easily formulate the linear program constraints
		// with their references to the edges they consist of
		writer.println("<variables nbVariables=\""+ numCe + "\">");
		for(int e = 0; e < conflictEdges.size(); e++)
		{
			conflictEdges.get(e).setId(e);
			writer.println("<variable name=\"E" + e + "\" domain=\"D0\"/>");
		}
		writer.println("</variables>\n");
		

		// Add relation tags for each conflict path
		writer.println("<relations nbRelations=\"" + numCp + "\">");
		for(int p = 0; p < numCp; p++)
		{
			conflictPaths.get(p).writeToulbar2Relation(writer, p);

			if((p + 1) % 10000 == 0)
			{
				System.out.println("Added relation for " + (p + 1) +
						" of " + numCp + " conflict paths");
			}
		}
		System.out.println("Added relations for all conflict paths");
		writer.println("</relations>\n");

		// Use (1000 * numCp) + 1 as a proxy to infinity because
		// this cost will never be achieved
		int maxCost = ((1000 * numCp) + 1);
		writer.println("<constraints nbConstraints=\"" + numCp +
			"\" maximalCost=\"" + maxCost + "\">");
		// Add constraint tags for each conflict path
		for(int p = 0; p < numCp; p++)
		{
			conflictPaths.get(p).writeToulbar2Constraint(writer, p);

			if((p + 1) % 10000 == 0)
			{
				System.out.println("Added constraint for " + (p + 1) +
						" of " + numCp + " conflict paths");
			}
		}
		System.out.println("Added constraints for all conflict paths");

		writer.println("</constraints>");
		writer.println("</instance>");
		writer.close();

		long stop = System.currentTimeMillis();
		System.out.println("Time (ms) to create the CSP: " +
				(stop - start) + "\n");
	}

	/**
	 * Uses toulbar2 (http://carlit.toulouse.inra.fr/cgi-bin/awki.cgi/ToolBarIntro),
	 * a weighted CSP solver, to oriented all conflict edges.
	 * 
	 * The complement to genToulbar2, it orients the edges once toulbar2 finds a
	 * solution to the WCSP.
	 * 
	 * @param toulbar2Sol the solution file generated by toulbar2 for this
	 * set of edges, sources, and targets.
	 * @return
	 * @throws IOException
	 */
	public double scoreToulbar2(String toulbar2Sol) throws IOException
	{
		// Make sure that the paths and conflict edges
		// have already been identified
		if(paths == null || conflictEdges == null)
		{
			findConflicts();
		}
		
		int numCe = conflictEdges.size();		
		
		// Read the solution file
		BufferedReader reader = new BufferedReader(new FileReader(toulbar2Sol));
		
		// The solution should be printed on one line
		String solution = reader.readLine().trim();
		String solParts[] = solution.split(" ");
		reader.close();
		
		// Make sure the solution gives the expected number of edge orientations
		if(solParts.length != numCe)
		{
			throw new IllegalArgumentException("Expected " + numCe + " edge orientation " +
					"assignments but found " + solParts.length + " in the file " +
					toulbar2Sol);
		}
		
		System.out.println("Read " + solParts.length + " edge orientations from " +
				toulbar2Sol);
		
		// Make the edge assignemnts.  1 corresponds to forward.
		for(int e = 0; e < numCe; e++)
		{
			if(Integer.parseInt(solParts[e]) == 0)
			{
				conflictEdges.get(e).setOrientation(Edge.BACKWARD);
			}
			else if(Integer.parseInt(solParts[e]) == 1)
			{
				conflictEdges.get(e).setOrientation(Edge.FORWARD);
			}
			else
			{
				throw new IllegalArgumentException(solParts[e] + " is not a valid " +
						"edge orientation");
			}
		}
		
		graphStateChanged();
		
		double global = globalScore();

		// Report the results
		System.out.println("\nToulbar2 WCSP approximation: " + global);
		System.out.println("Max possible: " + maxGlobalScore() + "\n");

		return global;
	}
	

	/**
	 * Randomly orients all conflict edges (undirected edges that
	 * have one or more paths that wish to use them in each direction).
	 * Path and conflict edges will be identified if they have not
	 * already been
	 *
	 */
	public void randomOrient()
	{
		if(paths == null || conflictEdges == null)
		{
			findConflicts();
		}

		// Randomly orient the edges that had conflicts
		for(UndirEdge e : conflictEdges)
		{
			e.randOrient();
			e.resetFlipCount();
		}
		
		graphStateChanged();
	}

	
	/**
	 * Stores all conflict edges in the graph and sets the class's
	 * conflictEdges variable.  Also fixes all edges that
	 * do not have conflicts.  Returns the number of edges that
	 * have conflicts.
	 *
	 */
	public int findConflicts()
	{
		if(paths == null)
		{
			findPaths();
		}

		long start = System.currentTimeMillis();
		System.out.println("Fixing edges without conflicts and finding conflict edges");
		ArrayList<UndirEdge> edges = graph.getUndirEdges();
		conflictEdges = new ArrayList<UndirEdge>();


		int fixCount = 0, usedCount = 0;
		for(UndirEdge e: edges)
		{
			if(e.isUsed())
			{
				usedCount++;
			}

			// fixIfNoConflicts() returns true iff the edge was not
			// already fixed but is now fixed because there were no conflicts.
			// We also want to count edges that were already fixed.
			if(e.isFixed() || e.fixIfNoConflicts())
			{
				fixCount++;
			}
			else
			{
				conflictEdges.add(e);
			}
		}
		long stop = System.currentTimeMillis();
		System.out.println(usedCount + " of " + edges.size() + " edges were used in at least one path");
		System.out.println(fixCount + " of " + edges.size() + " edges did not have conflicts");
		System.out.println("Time (ms): " + (stop - start) + "\n");

		return conflictEdges.size();
	}

	public ArrayList<UndirEdge> getConflictEdges()
	{
		if(conflictEdges == null)
		{
			findConflicts();
		}

		return conflictEdges;
	}

	
	/**
	 * 
	 * @return true iff all conflict edges have been oriented as either
	 * FORWARD or BACKWARD
	 */
	private boolean conflictEdgesOriented()
	{
		for(UndirEdge e: conflictEdges)
		{
			if(e.getOrientation() == Edge.UNORIENTED)
			{
				return false;
			}
		}

		return true;
	}


	/**
	 * Use the graph to find paths containing up to the default
	 * max depth number of edges
	 * from sources to targets.  The paths are stored by the
	 * edge orientation algorithm and by default not visible
	 * to the caller.  Sets the class's paths variable.
	 * @return the number of paths
	 *
	 */
	public int findPaths()
	{
		return findPaths(MAX_DEPTH);
	}


	/**
	 * Use the graph to find paths containing up to depth edges
	 * from sources to targets.  The paths are stored by the
	 * edge orientation algorithm and by default not visible
	 * to the caller.  Sets the class's paths variable.
	 * @param depth
	 * @retun the number of paths
	 */
	public int findPaths(int depth)
	{
		long start = System.currentTimeMillis();
		System.out.println("Finding paths up to depth " + depth);

		paths = graph.findPaths(depth);

		long stop = System.currentTimeMillis();
		System.out.println("Found " + paths.size() + " paths using depth " + depth);
		System.out.println("Time (ms): " + (stop - start) + "\n");

		return paths.size();
	}
	
	/**
	 * Return a reference to the list of paths.  Finds all
	 * paths first if they have not been found already.
	 * @return
	 */
	public ArrayList<Path> getPaths()
	{
		if(paths == null)
		{
			findPaths();
		}
		
		return paths;
	}
	
	/**
	 * Return a new list containing only satisfied (connected) paths.  Finds all
	 * paths first if they have not been found already.
	 * @return
	 */
	public ArrayList<Path> getSatisfiedPaths()
	{
		ArrayList<Path> satisfied = new ArrayList<Path>();
		
		for(Path p : getPaths())
		{
			if(p.isConnected())
			{
				satisfied.add(p);
			}
		}
		
		return satisfied;
	}
	
	/**
	 * Must be called after the state of the graph
	 * has been changed to maintain consistency
	 * among data structures.  Updates
	 * path edge use counts and clears the degree cache.
	 *
	 */
	public void graphStateChanged()
	{
		graph.clearDegreeCache();
		updateEdgeUses();
	}
	
	/**
	 * Update the edge use statistics for all paths.
	 * Should be called after any changes in edge
	 * orientation.
	 *
	 */
	public void updateEdgeUses()
	{
		if(paths == null)
		{
			findPaths();
		}
		
		for(Path p : paths)
		{
			p.updateEdgeUses();
		}
	}

	/**
	 * Calculates the global score for the network orientation.
	 * If the paths in the graph have not alread been found,
	 * they will be searched for first.
	 * @return the global network score
	 */
	public double globalScore()
	{
		if(paths == null)
		{
			findPaths();
		}

		double global = 0;
		for(Path p: paths)
		{
			global += p.weight();
		}

		return global;
	}

	/**
	 * Calculates the maximum possible score for the network orientation,
	 * which is the score that would be obtained if there were no conflicts
	 * and every path could be optimally oriented.  This score is rarely
	 * feasible.
	 * @return
	 */
	public double maxGlobalScore()
	{
		if(paths == null)
		{
			findPaths();
		}

		double max = 0;
		for(Path p: paths)
		{
			max += p.maxWeight();
		}

		return max;
	}

	/**
	 * Stores the orientations of the conflict edges.  Calling this
	 * method will overwrite the previous saved orientations
	 * stored in the savedOrientations member variable.
	 * @return A reference to the saved orienations.
	 */
	public int[] saveConflictOrientations()
	{
		if(conflictEdges == null)
		{
			throw new IllegalStateException("Cannot save the conflict edges' orientations " +
			"because the conflict edges have not yet been identified");
		}

		// Create a new array to hold the orientations
		savedOrientations = new int[conflictEdges.size()];

		// Save each edges' orientation
		for(int e = 0; e < savedOrientations.length; e++)
		{
			savedOrientations[e] = conflictEdges.get(e).getOrientation();
		}

		return savedOrientations;
	}
	
	
	/**
	 * Stores the orientations of the conflict edges to a file.
	 */
	public void writeConflictOrientations(PrintWriter writer)
	{
		if(conflictEdges == null)
		{
			throw new IllegalStateException("Cannot save the conflict edges' orientations " +
			"because the conflict edges have not yet been identified");
		}

		// Create a new buffer to hold the orientations
		StringBuffer buf = new StringBuffer();

		// Save each edges' orientation
		for(int e = 0; e < conflictEdges.size(); e++)
		{
			buf.append(conflictEdges.get(e).getOrientation()).append(" ");
		}
		
		writer.println(buf.toString());
	}


	/**
	 * Load the orientations stored in the savedOrientations member
	 * variable into the conflictEdges.
	 *
	 */
	public void loadConflictOrientations()
	{
		if(savedOrientations == null)
		{
			throw new IllegalStateException("Cannot load the stored orientation " +
			"because no orientation has been saved");
		}

		loadConflictOrientations(savedOrientations);
	}


	/**
	 * Load the orientations stored in the savedOrientations member
	 * variable into the conflictEdges.
	 *
	 */
	public void loadConflictOrientations(int[] orientations)
	{
		if(orientations.length != conflictEdges.size())
		{
			throw new IllegalStateException("There are a different number of " +
			"orientations and conflict edges");
		}

		// Load each edges' orientation
		for(int e = 0; e < orientations.length; e++)
		{
			conflictEdges.get(e).setOrientation(orientations[e]);
		}
		
		graphStateChanged();
	}
	
	
	/**
	 * Load conflict orientations from file.  Assumes the orientations are
	 * in the space-separated format generated by writeConflictOrientations
	 * @param file
	 * @throws IOException
	 */
	public void loadConflictOrientations(String file) throws IOException
	{
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = reader.readLine();
		String[] strParts = line.split(" ");
		int[] orient = new int[strParts.length];
		
		for(int o = 0; o < orient.length; o++)
		{
			orient[o] = Integer.parseInt(strParts[o]);
		}
		
		loadConflictOrientations(orient);
	}


	/**
	 * @param orientation1
	 * @param orientation2
	 * @return the number of edge orientation disagreements between the two
	 * orientations
	 */
	public int compareOrientations(int[] orientation1, int[] orientation2)
	{
		if(orientation1.length != orientation2.length)
		{
			throw new IllegalStateException("There are a different number of " +
			"edges in the orientations");
		}

		int dif = 0;
		// Count the differences
		for(int e = 0; e < orientation1.length; e++)
		{
			if(orientation1[e] != orientation2[e])
			{
				dif++;
			}
		}

		return dif;
	}

	/**
	 * Print the edges in each path along with the length and max path weight
	 * @param writer
	 */
	public void writePathWeights(PrintWriter writer)
	{
		for(Path p: paths)
		{
			writer.println(p + "\t" + p.getNumEdges() + "\t" + p.maxWeight());
		}
	}


	public void printPaths(String file) throws IOException
	{
		PrintWriter writer = new PrintWriter(new FileWriter(file));
		writer.println("Path\tIs satisfied?\tPath weight");
		
		for(Path p : paths)
		{
			writer.println(p + "\t" + p.isConnected() + "\t" + p.maxWeight());
		}
		writer.close();
	}
	
	/**
	 * Write the path connectedness and weight to one file and the
	 * edge orientations to another
	 * @param paths the path output file
	 * @param edges the edge output file
	 * @throws IOException
	 */
	public void printPathsEdges(String paths, String edges) throws IOException
	{
		printPaths(paths);
		writePathEdges(edges);
		
		// Deprecate the legacy edge output format, which does not output
		// the edge weight and uses less standard notation for directed
		// and undirected edges
//		PrintWriter writer = new PrintWriter(new FileWriter(edges));
//		graph.writeEdges(writer);
//		writer.close();
	}
	
	/**
	 * Write all edges that are on a source-target path.  It also
	 * specifies if the edge was originally undirected or directed (protein-protein or
	 * protein-DNA), if it is presently oriented, and its weight.  Finds paths
	 * if they have not already been found.  Only uses satisfied paths.
	 * @param outFile
	 */
	public void writePathEdges(String outFile)
	{
		if(paths == null)
		{
			findPaths();
		}
		
		try
		{
			HashSet<Edge> pathEdges = new HashSet<Edge>();
			
			// Iterate through all satisfied paths and add the edges on the path
			for(Path curPath : getSatisfiedPaths())
			{
				for(Edge curEdge : curPath.getEdges())
				{
					pathEdges.add(curEdge);
				}
			}
			
			PrintWriter writer = new PrintWriter(new FileWriter(outFile));
			writer.println("Source\tType\tTarget\tOriented\tWeight");
			
			for(DirEdge dEdge : graph.getDirEdges())
			{
				if(pathEdges.contains(dEdge))
				{
					String src = dEdge.getSource().getName();
					String targ = dEdge.getTarget().getName();
					
					writer.println(src + "\tpd\t" + targ + "\t" + dEdge.isOriented() + "\t" + dEdge.getWeight());
				}
			}
			
			for(UndirEdge uEdge : graph.getUndirEdges())
			{
				if(pathEdges.contains(uEdge))
				{
					String src = uEdge.getSource().getName();
					String targ = uEdge.getTarget().getName();
					
					writer.println(src + "\tpp\t" + targ + "\t" + uEdge.isOriented() + "\t" + uEdge.getWeight());
				}
			}
			writer.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
}
