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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

//import lpsolve.LpSolveException;

/**
 * Contains the main method for edge orientation.  Runs the orientation
 * algorithm as specified in the properties file.
 *
 */


public class EOMain
{
	public static void main(String[] args)
	{		
		try
		{			
			Properties defaults = new Properties();
			setDefaults(defaults);
			
			Properties props = new Properties(defaults);
			
			if(args.length < 1)
			{
				System.err.println("Not enough parameters\n" +
					"Usage: EOMain <properties_file>\n" + 
					"Using default properties");
			}
			else
			{
				// Load the properties from the specified file
				FileInputStream propsIn = new FileInputStream(args[0]);
				props.load(propsIn);
				propsIn.close();
			}
			
			// Create a new Graph and populate it with the edges, sources
			// and targets
			Graph graph = new Graph();
			DataLoader.readEdgesFromEda(graph, props.getProperty("edges.file"));
			DataLoader.readSources(graph, props.getProperty("sources.file"));
			DataLoader.readTargets(graph, props.getProperty("targets.file"));
			
			// Create a new orientation algorithm object
			// and find paths of length up to the specified max
			EdgeOrientAlg orient = new EdgeOrientAlg(graph);
			
			int maxLength = Integer.parseInt(props.getProperty("max.path.length"));
			orient.findPaths(maxLength);
			// All algorithms must know which edges are in conflict and which are
			// used in the same direction by all paths
			orient.findConflicts();
			
			// Store whether local search is desired
			String search = props.getProperty("local.search");
			boolean useSearch = true;
			if(search.equalsIgnoreCase("No"))
			{
				useSearch = false;
			}
			else if(!search.equalsIgnoreCase("Yes"))
			{
				invalidProp(props, "local.search");
			}
			
			// Determine which orientation algorithm to run
			String alg = props.getProperty("alg");
			if(alg.equalsIgnoreCase("Rand") || alg.equalsIgnoreCase("Random"))
			{
				// Random orientation was selected
				
				// Determine how many random restarts to run
				int restarts = Integer.parseInt(props.getProperty("rand.restarts"));
				
				// Determine if local search should be used or not
				if(useSearch)
				{
					orient.randPlusSearchSln(restarts);
				}
				else
				{
					orient.randSln(restarts);
				}
			}
			// Remove references to LpSolve
			/*
			else if(alg.equalsIgnoreCase("MINSAT") || alg.equalsIgnoreCase("MIN-SAT"))
			{
				// MIN-k-SAT was chosen
				try
				{
					orient.minSatSln();
				}
				catch(LpSolveException e)
				{
					e.printStackTrace();
				}
				
				if(useSearch)
				{
					orient.localSearchSln();
				}
			}
			*/
			else if(alg.equalsIgnoreCase("MAXCSP") || alg.equalsIgnoreCase("MAX-CSP"))
			{
				// MAX-k-CSP was chosen
				
				// See if we are generating an instance or loading and scoring
				// a result
				String phase = props.getProperty("csp.phase");
				if(phase.equalsIgnoreCase("Gen") || phase.equalsIgnoreCase("Generate"))
				{
					orient.genToulbar2(props.getProperty("csp.gen.file"));
				}
				else if(phase.equalsIgnoreCase("Score"))
				{
					orient.scoreToulbar2(props.getProperty("csp.sol.file"));
					
					if(useSearch)
					{
						orient.localSearchSln();
					}
				}
				else
				{
					invalidProp(props, "csp.phase");
				}
			}
			else
			{
				invalidProp(props, "alg");
			}

			
			// All edges have now been oriented
			// Write the edge orientations and
			// the paths' information
			orient.printPathsEdges(props.getProperty("path.output.file"),
					props.getProperty("edge.output.file"));
		}
		catch(IOException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Set the default properties
	 * @param defaults
	 */
	public static void setDefaults(Properties defaults)
	{
		defaults.setProperty("edges.file", "../sampleEdges.txt");
		defaults.setProperty("sources.file", "../sampleSources.txt");
		defaults.setProperty("targets.file", "../sampleTargets.txt");
		defaults.setProperty("edge.output.file", "../sampleEdge.out");
		defaults.setProperty("path.output.file", "../samplePath.out");
		defaults.setProperty("max.path.length", "5");
		defaults.setProperty("local.search", "Yes");
		defaults.setProperty("alg", "Random");
		defaults.setProperty("rand.restarts", "20");
		defaults.setProperty("csp.phase", "Gen");
		defaults.setProperty("csp.gen.file", "../wcsp.xml");
		defaults.setProperty("csp.sol.file", "../toulbar2Solution.txt");
	}
	
	/**
	 * Throw an exception when this property has an unaccpetable value
	 * @param props
	 * @param property
	 * @throws IllegalArgumentException
	 */
	public static void invalidProp(Properties props, String property)
		throws IllegalArgumentException
	{
		throw new IllegalArgumentException(props.getProperty(property) +
				" is not a valid value " +
				"for the " + property + " property");
	}
}
