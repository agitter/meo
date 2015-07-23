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

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;

import lpsolve.LpSolve;
import lpsolve.LpSolveException;


/**
 * A series of edges that connect a source and target when
 * all edges are locally optimally oriented
 *
 */


public class Path {

	/** Paths with optimal weight below this threshold are considered 
	 * weak and are ignored */
	protected static final double WEIGHT_THRESHOLD = 0.0;

	/** The Graph that contains this Path */
	Graph graph;
	
	/** The edges from source to target in order */
	protected Edge[] edges;
	/** Stores the direction (aka orientation) each edge needs to take in order
	 * for the target to be reachable from the source */
	protected int[] edgeDirs;
	protected Vertex[] vertices;
	/** The number of edges */
	protected int length;
	
	// These variables are all calculated once upon Path creation and cached.
	// There are used by the various comparators
	protected double cachedMaxWeight;
	protected double cachedMaxEdgeWeight;
	protected double cachedAvgEdgeWeight;
	protected double cachedMinEdgeWeight;
	/** Must be updated after an edge orientation algorithm is run */
	protected int cachedMaxEdgeUses;
	/** Must be updated after an edge orientation algorithm is run */
	protected double cachedAvgEdgeUses;
	/** Must be updated after an edge orientation algorithm is run */
	protected int cachedMinEdgeUses;
	protected int cachedMaxDegree;
	protected double cachedAvgDegree;
	protected int cachedMinDegree;
	

	public Path(Graph graph, List<Edge> eStack, List<Vertex> vStack)
	{
		this.graph = graph;
		
		length = eStack.size();
		edges = new Edge[length];
		edgeDirs = new int[length];
		vertices = new Vertex[length + 1];

		// Store the vertices in order from source to target
		vertices = vStack.toArray(vertices);

		// Store the edges in order from source to target and store the
		// direction they must be used in in order to connect the
		// source to the target
		for(int e = 0; e < length; e++)
		{
			// Abuse the stack data structure and access its elements as a list
			edges[e] = eStack.get(e);
			edgeDirs[e] = edges[e].findDirection(vertices[e]);

			// Associate the path with the edge so the edge can
			// track conflicting paths.  The vertex at index e
			// is the source of the edge.
			edges[e].assocPath(this, vertices[e]);

		}

		// Calculate and initialize cached path statistics
		// including max weight, edge uses, and vertex degree
		updateCachedStats();
	}
	
	/**
	 * Calculate and initialize cached path statistics
	 * including max weight, edge uses, and vertex degree
	 *
	 */
	public void updateCachedStats()
	{
		// Calculate and cache the optimal weight.  Changes in edge
		// direction don't affect the optimal weight.
		cachedMaxWeight = calcMaxWeight();
		
		updateEdgeWeightStats();		
		updateEdgeUses();
		updateDegreeStats();
	}
	
	/**
	 * Returns the path's maximum weight, the weight it would have if all
	 * edges along the path are oriented correctly.  This method should only
	 * be called by the constructor.
	 * @return
	 */
	protected double calcMaxWeight()
	{
		double w = 1;

		for(Edge e: edges)
		{
			w *= e.getWeight();
		}

		for(Vertex v: vertices)
		{
			w *= v.getWeight();
		}

		// The final vertex also has a target weight
		w *= vertices[length].getTargetWeight();

		return w;
	}
	
	/**
	 * Update the cached max edge uses, average edge uses,
	 * and min edge uses values.  Needs to be called
	 * after the edge orientation in the Graph is changed.
	 * 
	 * Uses numConsistentPaths for each Edge in the path to
	 * obtain the number of uses.
	 * 
	 * Considers all paths, not just paths that are connected from
	 * source to target.
	 */
	public void updateEdgeUses()
	{
		int max = Integer.MIN_VALUE;
		int min = Integer.MAX_VALUE;
		double sum = 0;
		
		for(Edge curEdge : edges)
		{
			int uses = curEdge.numConsistentPaths();
			if(uses > max)
			{
				max = uses;
			}
			if(uses < min)
			{
				min = uses;
			}
			sum += uses;
		}
		
		cachedMaxEdgeUses = max;
		cachedMinEdgeUses = min;
		cachedAvgEdgeUses = sum / getNumEdges();
	}

	
	/**
	 * Calculate and store the max, average, and minimum edge weight
	 * of edges along the path
	 *
	 */
	public void updateEdgeWeightStats()
	{
		double max = Double.MIN_VALUE;
		double min = Double.MAX_VALUE;
		double sum = 0;
		
		for(Edge curEdge : edges)
		{
			double weight = curEdge.getWeight();
			if(weight > max)
			{
				max = weight;
			}
			if(weight < min)
			{
				min = weight;
			}
			sum += weight;
		}
		
		cachedMaxEdgeWeight = max;
		cachedMinEdgeWeight = min;
		cachedAvgEdgeWeight = sum / getNumEdges();
	}
	
	/**
	 * Calculate and store the max, average, and minimum vertex degree
	 * of vertices along the path
	 *
	 */
	public void updateDegreeStats()
	{
		int max = Integer.MIN_VALUE;
		int min = Integer.MAX_VALUE;
		double sum = 0;
		
		for(Vertex curVert : vertices)
		{
			// Cache degrees that have already been calculated
			// and reuse the cached values if possible
			int uses = graph.getDegree(curVert, false, true);
			if(uses > max)
			{
				max = uses;
			}
			if(uses < min)
			{
				min = uses;
			}
			sum += uses;
		}
		
		cachedMaxDegree = max;
		cachedMinDegree = min;
		cachedAvgDegree = sum / getNumVertices();
	}
	
	
	public Vertex getTarget()
	{
		return vertices[length];
	}
	
	public Vertex[] getVertices()
	{
		return vertices;
	}
	
	/**
	 * 
	 * @return the number of edges in the path
	 */
	public int getNumEdges()
	{
		return length;
	}
	
	/**
	 * 
	 * @return the number of vertices in the path
	 */
	public int getNumVertices()
	{
		return length + 1;
	}
	
	/**
	 * A path's weight is the product of the weights of all vertices and edges
	 * along it as well as the target vertex's weight if all edges are
	 * correctly directed.  If any edges is directed in the wrong direction,
	 * the path's weight is 0.
	 * @return
	 */
	public double weight()
	{
		double w = 1;

		for(int e = 0; e < length; e++)
		{
			int orient = edges[e].getOrientation();
			// Count this edge's weight if it is undirected or if
			// it is directed in the direction the path wants to use it in
			if(orient == Edge.UNORIENTED || orient == edgeDirs[e])
			{
				w *= edges[e].getWeight();
			}
			// If the edge is directed in the opposite direction
			// the source and target are not directed the path weight is 0
			else
			{
				return 0;
			}
		}

		for(Vertex v: vertices)
		{
			w *= v.getWeight();
		}

		// The final vertex also has a target weight
		w *= vertices[length].getTargetWeight();

		return w;
	}

	/**
	 * 
	 * @return true if there are conflicts on any of the edges in this path
	 */
	public boolean hasConflicts()
	{
		for(Edge e : edges)
		{
			if(e.countConflicts() > 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 * @return true if all edges along the path are oriented in the direction
	 * the path wants
	 */
	public boolean isConnected()
	{
		for(int e = 0; e < length; e++)
		{
			int orient = edges[e].getOrientation();
			// Check if edge e is unoriented or oriented correctly.
			// Either way the path is not broken.
			if(!(orient == Edge.UNORIENTED || orient == edgeDirs[e]))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A path is feasible if all of its edges are unfixed or if the only
	 * fixed edges are fixed in the orientation in which the path wishes
	 * to use them.
	 * @return false if one or more of the edges along the path are fixed
	 * in the wrong orientation.
	 */
	public boolean isFeasible()
	{
		for(int e = 0; e < length; e++)
		{
			// Check if edge e is fixed in the wrong direction
			if(edges[e].isFixed() && edges[e].getOrientation() != edgeDirs[e])
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * 
	 * @return true if this path's optimal weight is below the
	 * preset weight threshold
	 */
	public boolean isBelowThreshold()
	{
		return maxWeight() < WEIGHT_THRESHOLD;
	}
	
	
	/**
	 * Unconditionally removes this path from all
	 * edge associations.  Use only when the path is
	 * about to go out of scope.
	 *
	 */
	public void deletePath()
	{
		for(Edge e : edges)
		{
			e.removePath(this);
		}
	}
	
	
	/**
	 * Returns true if the paths are the same length and
	 * every Vertex along both paths has the same name and
	 * appears in the same position.  Note that different
	 * Vertex ids are allowed as long as the names are the same.
	 * @param otherPath
	 * @return
	 */
	public boolean pathMatches(Path otherPath)
	{
		if(length != otherPath.getNumEdges())
		{
			return false;
		}
		
		// We now know the lengths are the same
		Vertex[] otherVertices = otherPath.getVertices();
		for(int l = 0; l <= length; l++)
		{
			if(!vertices[l].sameName(otherVertices[l]))
			{
				return false;
			}
		}
		
		return true;
	}
	
	

	/**
	 * Add a constraint to the lp_solve linear program for every conflict
	 * edge in the path
	 * @param lpSolver
	 * @param pathVar the index of the path variable in the linear program
	 * @param edgeOffset gives the index of the first edge variable
	 * in the linear program (because the path variables come first)
	 * @throws LPSolveException
	 */
	public void addLpConstraints(LpSolve lpSolver, int pathVar, int edgeOffset)
	throws LpSolveException
	{
		for(int e = 0; e < length; e++)
		{
			// Check if this edge is a conflict edge
			// If this edge has conflicts, it must be an
			// UndirEdge
			if(edges[e].countConflicts() > 0)
			{
				// Make sure this edge has a valid id
				int id = ((UndirEdge) edges[e]).getId();
				if(id < 0)
				{
					throw new IllegalStateException("Edge " + e + " has illegal " +
							"id " + id);
				}

				// colno tells which linear program variables this constraint
				// applies to
				int[] colno = {pathVar, edgeOffset + id};
				// row gives the values for the constraint
				double[] row = new double[2];
				// The path variable always has the value 1
				row[0] = 1;

				// Add the correct conflict depending on the direction the
				// path wants to use this edge in
				if(edgeDirs[e] == Edge.FORWARD)
				{
					// Add the constraint:
					// 1 * pathVar + 1 * edgeVar >= 1
					row[1] = 1;
					lpSolver.addConstraintex(2, row, colno, LpSolve.GE, 1);
				}
				else if(edgeDirs[e] == Edge.BACKWARD)
				{
					// Add the constraint:
					// 1 * pathVar - 1 * edgeVar >= 0
					row[1] = -1;
					lpSolver.addConstraintex(2, row, colno, LpSolve.GE, 0);
				}
			}

		} // end loop through all edges in path
	}
	
	
	/**
	 * Write a relation in the XCSP 2.1 format.  The relation will have
	 * the default cost of the path weight, which is the cost that
	 * will be assigned if the path is broken by one or more of the
	 * edge orienations.  Only one tuple is needed for the 0 cost
	 * orientation that connects the path.  If a variable is 1 that
	 * means it is oriented forward.
	 * 
	 * @param writer
	 * @param pathIndex
	 */
	public void writeToulbar2Relation(PrintWriter writer, int pathIndex)
	{
		// The arity is the number of conflict edges (variables) in the path
		int arity = 0;
		
		// Stores the values the tuple must take to satisfy the path
		StringBuffer tupleBuf = new StringBuffer();
		
		for(int e = 0; e < length; e++)
		{
			// Check if this edge is a conflict edge
			// If this edge has conflicts, it must be an
			// UndirEdge
			if(edges[e].countConflicts() > 0)
			{
				// Make sure this edge has a valid id
				int edgeIndex = ((UndirEdge) edges[e]).getId();
				if(edgeIndex < 0)
				{
					throw new IllegalStateException("Edge " + e + " has illegal " +
							"id " + edgeIndex);
				}
				
				// The first value is not preceeded by a space
				if(arity >= 1)
				{
					tupleBuf.append(" ");
				}
				
				arity++;

				// Add the correct variable value in the tuple depending
				// on the direction the
				// path wants to use this edge in
				if(edgeDirs[e] == Edge.FORWARD)
				{
					tupleBuf.append("1");
				}
				else if(edgeDirs[e] == Edge.BACKWARD)
				{
					tupleBuf.append("0");
				}
				else
				{
					throw new IllegalStateException("A path cannot have the desired edge " +
					"orientation stored as unoriented");
				}
			}
		} // end loop through all edges in path
		
		if(arity < 1)
		{
			throw new IllegalStateException("Path " + toString() + " is not a conflict path");
		}
		
		// Construct the relation tag
		StringBuffer buf = new StringBuffer("<relation name=\"R");
		buf.append(pathIndex);
		
		buf.append("\" arity=\"");
		buf.append(arity);
		
		buf.append("\" nbTuples=\"1\" semantics=\"soft\" defaultCost=\"");
		buf.append(Math.round(cachedMaxWeight * 1000));
		
		// The tuple for the correct orientation has 0 cost
		buf.append("\">0:");
		buf.append(tupleBuf);
		buf.append("</relation>");
		
		writer.println(buf.toString());
		writer.flush();
	}

	
	
	/**
	 * Write a constraint in the XCSP 2.1 format.  Because there is a 1-to-1
	 * relationship between relations and constraints in this formulation,
	 * the constraint simply points to the relation and specifies which
	 * edges are used.
	 * 
	 * @param writer
	 * @param pathIndex
	 */
	public void writeToulbar2Constraint(PrintWriter writer, int pathIndex)
	{
		// The arity is the number of conflict edges (variables) in the path
		int arity = 0;
		
		// Stores the conflict edges used by the path
		StringBuffer edgeBuf = new StringBuffer();
		
		for(int e = 0; e < length; e++)
		{
			// Check if this edge is a conflict edge
			// If this edge has conflicts, it must be an
			// UndirEdge
			if(edges[e].countConflicts() > 0)
			{
				// Make sure this edge has a valid id
				int edgeIndex = ((UndirEdge) edges[e]).getId();
				if(edgeIndex < 0)
				{
					throw new IllegalStateException("Edge " + e + " has illegal " +
							"id " + edgeIndex);
				}
				
				// The first edge is not preceeded by a space
				if(arity >= 1)
				{
					edgeBuf.append(" ");
				}
				
				arity++;

				// Add the conflict edge's id
				edgeBuf.append("E");
				edgeBuf.append(edgeIndex);
			}
		} // end loop through all edges in path
		
		if(arity < 1)
		{
			throw new IllegalStateException("Path " + toString() + " is not a conflict path");
		}
		
		// Construct the constraint tag
		StringBuffer buf = new StringBuffer("<constraint name=\"C");
		buf.append(pathIndex);
		
		buf.append("\" arity=\"");
		buf.append(arity);
		
		// The scope is the list of edge variables in the path
		buf.append("\" scope=\"");
		buf.append(edgeBuf);

		buf.append("\" reference=\"R");
		buf.append(pathIndex);
		buf.append("\"/>");
		
		writer.println(buf.toString());
		writer.flush();
	}
	
	
	
	
	
	

	public String toString()
	{
		String out = "";
		for(int v = 0; v < vertices.length - 1; v++)
		{
			out += vertices[v] + ":";
		}
		out += vertices[vertices.length - 1];
		return out;
	}
	
	/**
	 * Print tab-separated cached statistics in the following order:
	 * <br>
	 * max path weight
	 * max edge weight
	 * average edge weight
	 * min edge weight
	 * max edge uses
	 * average edge uses
	 * min edge uses
	 * max vertex degree
	 * average vertex degree
	 * min vertex degree
	 * @return
	 */
	public String printStats()
	{
		StringBuffer buf = new StringBuffer();
		buf.append(cachedMaxWeight).append("\t");
		
		buf.append(cachedMaxEdgeWeight).append("\t");
		buf.append(cachedAvgEdgeWeight).append("\t");
		buf.append(cachedMinEdgeWeight).append("\t");

		buf.append(cachedMaxEdgeUses).append("\t");
		buf.append(cachedAvgEdgeUses).append("\t");
		buf.append(cachedMinEdgeUses).append("\t");
		
		buf.append(cachedMaxDegree).append("\t");
		buf.append(cachedAvgDegree).append("\t");
		buf.append(cachedMinDegree);
		
		return buf.toString();
	}

	
	/**
	 * Returns the path's cached maximum weight, the weight it would have if all
	 * edges along the path are oriented correctly.
	 * @return
	 */
	public double maxWeight()
	{
		return cachedMaxWeight;
	}
	
	/**
	 * 
	 * @return the maximum edge weight of all edges along the path
	 */
	public double maxEdgeWeight()
	{
		return cachedMaxEdgeWeight;
	}
	
	/**
	 * 
	 * @return the average edge weight of all edges along the path
	 */
	public double avgEdgeWeight()
	{
		return cachedAvgEdgeWeight;
	}
	
	/**
	 * 
	 * @return the minimum edge weight of all edges along the path
	 */
	public double minEdgeWeight()
	{
		return cachedMinEdgeWeight;
	}
	
	/**
	 * 
	 * @return the maximum number of times an edge is used in a path
	 * in the direction it is oriented in this path
	 */
	public int maxEdgeUses()
	{
		return cachedMaxEdgeUses;
	}
	
	/**
	 * 
	 * @return the average number of times an edge is used in a path
	 * in the direction it is oriented in this path
	 */
	public double avgEdgeUses()
	{
		return cachedAvgEdgeUses;
	}
	
	/**
	 * 
	 * @return the minimum number of times an edge is used in a path
	 * in the direction it is oriented in this path
	 */
	public int minEdgeUses()
	{
		return cachedMinEdgeUses;
	}
	
	/**
	 * 
	 * @return the maximum vertex degree of all vertices along the path
	 */
	public int maxDegree()
	{
		return cachedMaxDegree;
	}
	
	/**
	 * 
	 * @return the average vertex degree of all vertices along the path
	 */
	public double avgDegree()
	{
		return cachedAvgDegree;
	}
	
	/**
	 * 
	 * @return the minimum vertex degree of all vertices along the path
	 */
	public int minDegree()
	{
		return cachedMinDegree;
	}
	
	
	/**
	 * Get the Comparator that compares based on the cached value specified
	 * by type.  Make sure the cached statistics have been updated before
	 * using a Comparator.
	 * @param type
	 * @return
	 */
	public static Comparator<Path> getComparator(String type)
	{
		if(type.equalsIgnoreCase("MaxWeight") || type.equalsIgnoreCase("PathWeight"))
		{
			return new PathWeightComp();
		}
		else if(type.equalsIgnoreCase("MaxEdgeWeight"))
		{
			return new MaxEdgeWeightComp();
		}
		else if(type.equalsIgnoreCase("AvgEdgeWeight"))
		{
			return new AvgEdgeWeightComp();
		}
		else if(type.equalsIgnoreCase("MinEdgeWeight"))
		{
			return new MinEdgeWeightComp();
		}
		else if(type.equalsIgnoreCase("MaxUses"))
		{
			return new MaxUsesComp();
		}
		else if(type.equalsIgnoreCase("AvgUses"))
		{
			return new AvgUsesComp();
		}
		else if(type.equalsIgnoreCase("MinUses"))
		{
			return new MinUsesComp();
		}
		else if(type.equalsIgnoreCase("MaxDegree"))
		{
			return new MaxDegreeComp();
		}
		else if(type.equalsIgnoreCase("AvgDegree"))
		{
			return new AvgDegreeComp();
		}
		else if(type.equalsIgnoreCase("MinDegree"))
		{
			return new MinDegreeComp();
		}
		else
		{
			throw new IllegalArgumentException(type + " is not a valid Path Comparator");
		}
	}
	
	/**
	 * Compare paths by max path weight.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class PathWeightComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			return Double.compare(p1.maxWeight(), p2.maxWeight());
		}
	}
	
	/**
	 * Compare paths by max edge weight.  Uses max path weight to break ties.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class MaxEdgeWeightComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.maxEdgeWeight() == p2.maxEdgeWeight())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return Double.compare(p1.maxEdgeWeight(), p2.maxEdgeWeight());
			}
		}
	}
	
	/**
	 * Compare paths by average edge weight.  Uses max path weight to break ties.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class AvgEdgeWeightComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.avgEdgeWeight() == p2.avgEdgeWeight())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return Double.compare(p1.avgEdgeWeight(), p2.avgEdgeWeight());
			}
		}
	}
	
	/**
	 * Compare paths by min edge weight.  Uses max path weight to break ties.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class MinEdgeWeightComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.minEdgeWeight() == p2.minEdgeWeight())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return Double.compare(p1.minEdgeWeight(), p2.minEdgeWeight());
			}
		}
	}
	
	/**
	 * Compare paths by the max number of times an edge is used in all paths
	 * in the direction this path uses it.  Uses max path weight to break ties.
	 * Make sure the cached uses values of the paths are up to date before using.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class MaxUsesComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.maxEdgeUses() == p2.maxEdgeUses())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return p1.maxEdgeUses() - p2.maxEdgeUses();
			}
		}
	}
	
	/**
	 * Compare paths by the average number of times an edge is used in all paths
	 * in the direction this path uses it.  Uses max path weight to break ties.
	 * Make sure the cached uses values of the paths are up to date before using.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class AvgUsesComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.avgEdgeUses() == p2.avgEdgeUses())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return Double.compare(p1.avgEdgeUses(), p2.avgEdgeUses());
			}
		}
	}
	
	/**
	 * Compare paths by the min number of times an edge is used in all paths
	 * in the direction this path uses it.  Uses max path weight to break ties.
	 * Make sure the cached uses values of the paths are up to date before using.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class MinUsesComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.minEdgeUses() == p2.minEdgeUses())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return p1.minEdgeUses() - p2.minEdgeUses();
			}
		}
	}
	
	
	/**
	 * Compare paths by max vertex degree.  Uses max path weight to break ties.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class MaxDegreeComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.maxDegree() == p2.maxDegree())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return p1.maxDegree() - p2.maxDegree();
			}
		}
	}
	
	/**
	 * Compare paths by average vertex degree.  Uses max path weight to break ties.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class AvgDegreeComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.avgDegree() == p2.avgDegree())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return Double.compare(p1.avgDegree(), p2.avgDegree());
			}
		}
	}
	
	/**
	 * Compare paths by min vertex degree.  Uses max path weight to break ties.
	 * Note: this comparator imposes orderings that are inconsistent with equals.
	 */
	static class MinDegreeComp implements Comparator<Path>
	{
		public int compare(Path p1, Path p2)
		{
			if(p1.minDegree() == p2.minDegree())
			{
				return Double.compare(p1.maxWeight(), p2.maxWeight()); 
			}
			else
			{
				return p1.minDegree() - p2.minDegree();
			}
		}
	}
}
