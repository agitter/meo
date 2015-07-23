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

package preprocess;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PPI {

	public static void main(String[] args)
	{
		if(args.length < 4)
		{
			throw new IllegalArgumentException("Not enough parameters\n" +
				"Usage: PPI <BioGRID_file> <experiment_types_file> <intermediate_output_file> <final_output_file>");
		}

		String bioGridFile = args[0];
		String exprTypeFile = args[1];
		String intermedFile = args[2];
		String outFile = args[3];

		prepBioGrid(bioGridFile, exprTypeFile, intermedFile);
		ppiEvidenceToEda(intermedFile, "", outFile, exprTypeFile);
	}

	/**
	 * Takes a file of PPI from BioGRID and writes a file containing
	 * one line per unique interaction, which tells how many independent (as
	 * determined by PMID) sources report that interaction.  This is
	 * broken down by the type of experiment performed.
	 * Ignores self interactions and directionality of an interaction.
	 * @param bgFile
	 * @param exprType A list of types of experiments performed to detect
	 * the interaction.  Each type should be written as [expr type]\t[class]\t[confidence].
	 * If the class is "ignore" that type of interaction will be discarded.  The confidence
	 * is used by ppiEvidenceToEda, but not this method and should be between 0 and 1.
	 * @param outFile
	 * @see ppiEvidenceToEda
	 */
	public static void prepBioGrid(String bgFile, String exprFile, String outFile)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(exprFile));
			// Read in the experiment types and create a map for the types
			HashMap<String,String> exprTypes = new HashMap<String,String>();
			String line;
			while((line = reader.readLine()) != null)
			{
				// Don't use parts[2], the confidence
				String[] parts = line.split("\t");
				exprTypes.put(parts[0].trim().toUpperCase(), parts[1].trim().toUpperCase());
			}
			reader.close();

			// Read in the interactions and store them in a map
			HashMap<String, PPIInfo> ints = new HashMap<String, PPIInfo>();
			reader = new BufferedReader(new FileReader(bgFile));

			// First ignore the header
			while((line = reader.readLine()) != null &&
					!line.startsWith("INTERACTOR_A\tINTERACTOR_B\tOFFICIAL_SYMBOL_A\tOFFICIAL_SYMBOL_B"));

			while((line = reader.readLine()) != null)
			{
				String[] parts = line.split("\t");

				// Only continue if this is a valid interaction
				if(isValidInteraction(parts, exprTypes))
				{
					String type = exprTypes.get(parts[6].toUpperCase());
					String pmid = parts[8];

					// Order the proteins
					String prots = sortInt(parts[0], parts[1]);

					// See if this interaction is in the map
					PPIInfo info;
					if(ints.containsKey(prots))
					{
						info = ints.get(prots);
					}
					else
					{
						info = new PPIInfo(prots);
						ints.put(prots, info);
					}

					// Add this new fact as additional evidence
					info.addEvidence(type, pmid);
				}
			}
			reader.close();


			// Write the interactions along with their types of evidence
			PrintWriter writer = new PrintWriter(new FileWriter(outFile));

			// Determine the order in which to write the experiment evidence types
			HashSet<String> typeSet = new HashSet<String>(exprTypes.values());
			typeSet.remove("IGNORE");
			ArrayList<String> typeOrd = new ArrayList<String>(typeSet);
			Collections.sort(typeOrd);

			// Write a heading
			writer.print("Protein1\tProtein2");
			for(String type : typeOrd)
			{
				writer.print("\t# " + type);
			}
			writer.println();

			for(Map.Entry<String, PPIInfo> entry : ints.entrySet())
			{
				PPIInfo info = entry.getValue();
				info.setTypeOrder(typeOrd);
				writer.println(info);
			}
			writer.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

	}

	/**
	 * Checks that the interaction is not a self-loop and is not based on one of the
	 * ignored types of experiments.
	 * Uncomment the condition below to check if the interaction
	 * is between two yeast proteins (taxonomy id 4932)
	 * @param intParts
	 * @param exprTypes
	 * @return
	 */
	private static boolean isValidInteraction(String[] intParts, HashMap<String,String> exprTypes)
	{
		// Same protein is bait and prey
		if(intParts[0].equalsIgnoreCase(intParts[1]))
		{
			return false;
		}

		// One of the proteins is not a yeast protein
//		if(!intParts[9].equals("4932") || !intParts[10].equals("4932"))
//		{
//			return false;
//		}

		// Print any experimental types that are not known
		if(!exprTypes.containsKey(intParts[6].toUpperCase()))
		{
			System.err.println("Unknown experimental type: " + intParts[6]);
			return false;
		}

		// Return false if this experimental type is ignored
		if(exprTypes.get(intParts[6].toUpperCase()).equalsIgnoreCase("IGNORE"))
		{
			return false;
		}

		return true;
	}

	/**
	 * Sorts protein 1 and protein 2 so that the lexicographically lesser
	 * one is ordered first.  Returns the pair as a single tab-delimited String.
	 * @param p1
	 * @param p2
	 * @return
	 */
	private static String sortInt(String p1, String p2)
	{
		if(p1.compareToIgnoreCase(p2) <= 0)
		{
			return p1 + "\t" + p2;
		}
		else
		{
			return p2 + "\t" + p1;
		}
	}

	/**
	 * Takes a PPI evidence file (such as the one created by prepBioGrid) that contains a
	 * heading line and the number of HTP and LTP experiments support an interaction.
	 * Maps this evidence to a confidence score between 0 and 1.
	 * @param evidenceFile
	 * @param existingEdaFile If a file is given (a valid file that is not ""), appends
	 * the PPI interactions to the existing file
	 * @param edaFile The output file
	 * @parm exprType A list of types of experiments performed to detect
	 * the interaction.  Each type should be written as [expr type]\t[class]\t[confidence].
	 * The confidence should be between 0 and 1.
	 */
	public static void ppiEvidenceToEda(String evidenceFile, String existingEdaFile, String edaFile, String exprType)
	{
		try
		{
			// Read in the confidence scores for the experiment types
			// If a type is listed multiple times, the last confidence score is taken
			BufferedReader typeReader = new BufferedReader(new FileReader(exprType));
			String line;
			HashMap<String, Double> typeMap = new HashMap<String, Double>();
			while((line = typeReader.readLine()) != null)
			{
				// parts[1] is the type, parts[2] is the confidence
				String[] parts = line.split("\t");
				double conf = Double.parseDouble(parts[2]);
				if(conf < 0 || conf > 1)
				{
					throw new IllegalArgumentException("Confidence of " + parts[1] + " is " + parts[2]);
				}
				typeMap.put(parts[1].toUpperCase(), Double.valueOf(conf));
			}
			typeReader.close();

			PrintWriter writer = new PrintWriter(new FileWriter(edaFile));

			// See if there is an existing EDA file to which to append these interactions
			File existing = new File(existingEdaFile);
			if(existing.exists())
			{
				BufferedReader existReader = new BufferedReader(new FileReader(existing));
				while((line = existReader.readLine()) != null)
				{
					writer.println(line);
				}
				existReader.close();
			}
			// Only need a heading if writing a true .eda file
//			else
//			{
//				writer.println("EdgeProbs (class=java.lang.Double)");
//			}

			// Loop through all PPI, calculate the confidence score, and
			// write it to the eda file
			BufferedReader reader = new BufferedReader(new FileReader(evidenceFile));

			// Use the header to determine which confidence scores belong to each column
			String[] header = reader.readLine().split("\t");
			double[] confs = new double[header.length];
			confs[0] = Double.NaN;
			confs[1] = Double.NaN;
			// The first two columns are the proteins, skip them
			for(int h = 2; h < header.length; h++)
			{
				if(header[h].startsWith("# "))
				{
					header[h] = header[h].substring(2);
				}
				header[h] = header[h].trim().toUpperCase();
				if(!typeMap.containsKey(header[h]))
				{
					throw new IllegalArgumentException("No confidence score given for " + header[h]);
				}

				// Store 1 - the confidence
				confs[h] = 1 - typeMap.get(header[h]);
			}

			// Calculate the score for each interaction
			while((line = reader.readLine()) != null)
			{
				String[] parts = line.split("\t");
				double prod = 1;

				// parts[0] and [1] are the proteins
				for(int p = 2; p < parts.length; p++)
				{
					int count = Integer.parseInt(parts[p]);
					prod *= Math.pow(confs[p], count);
				}

				writer.println(parts[0] + " (pp) " + parts[1] + " = " + (1-prod));
			}

			writer.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
}

class PPIInfo
{
	private String prots;
	private HashMap<String, HashSet<String>> evidenceMap;
	private ArrayList<String> typeOrder;

	public PPIInfo(String protiens)
	{
		prots = protiens;
		evidenceMap = new HashMap<String, HashSet<String>>();
		typeOrder = new ArrayList<String>();
	}

	public void addEvidence(String type, String pmid)
	{
		type = type.toUpperCase();

		HashSet<String> evidence;
		if(evidenceMap.containsKey(type))
		{
			evidence = evidenceMap.get(type);
		}
		else
		{
			evidence = new HashSet<String>();
			evidenceMap.put(type, evidence);
			if(!typeOrder.contains(type))
			{
				typeOrder.add(type);
			}
		}

		// Add the PMID as a new instance of supporting evidence
		// of this type
		evidence.add(pmid);
	}

	public void setTypeOrder(ArrayList<String> order)
	{
		if(order.containsAll(evidenceMap.keySet()))
		{
			typeOrder = order;
		}
		else
		{
			System.err.println("PPIInfo cannot set type order.  Not all types are ordered.");
		}
	}

	public String toString()
	{
		StringBuffer buf = new StringBuffer(prots);

		for(String type : typeOrder)
		{
			buf.append("\t");

			if(evidenceMap.containsKey(type))
			{
				// The size is the number of unique PMIDs supporting this
				// interaction with this type of evidence
				buf.append(evidenceMap.get(type).size());
			}
			else
			{
				buf.append(0);
			}
		}

		return buf.toString();
	}
}
