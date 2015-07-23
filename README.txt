README
Version 1.0.0

************************************

Use of this code implies you have accepted the terms of the license in license.txt.
The code requires Java 5.0 or above.

A properties file is used to specify which orientation algorithm to run, which
parameters to use, and which data to run.  See sample.props for an example and 
sampleEdges.txt, sampleSources.txt, and sampleTargets.txt for examples of
the formatting required for the data files.  A separate Java program (described
below) is included to preprocess BioGRID PPI such that they are in the required
format.  The character '_' should not appear in any gene names in any files because
it is a reserved special character.

The usage of the orientation algorithms is:
EOMain <properties_file>

The file specified by the edge.output.file property will contain all edges in the
network and their orientation.  The source always appears in the first column and
the target is in the third.  The second column is --- or --> depending on whether
the edge is undirected or directed in the original network.

The file specified by the path.output.file property lists all paths found
in the original unoriented network, their weights, and whether they are
satisfied after the orientation.

The MIN-k-SAT-based orientation algorithm relies on the lp_solve Mixed Integer Linear
Programming solver, which is not distributed with our Java code and can be downloaded at
http://sourceforge.net/projects/lpsolve/. Instructions for installing lp_solve and using
it with the Java code via the JNI API can be found at
http://lpsolve.sourceforge.net/5.5/Java/README.html.  The lp_solve JAR will need to be
included in the path when using the orientation algorithms.  If the lp_solve installation
is unsuccessful, it is possible to add the lp_solve wrapper source code directly instead
of using the JAR.  This enables you to manually specify the location of the lp_solve
libraries.  In LpSolve.java, you can replace line 275 with two calls
to System.load(), one with the absolute path to liblpsolve55.so and the other to liblpsolve55j.so.
If you only wish to run the other orientation algorithms without installing lp_solve, you may
safely comment out or delete all references to lp_solve in EOMain.java and EdgeOrientAlg.java.

The MAX-k-CSP-based algorithm uses toulbar2, which is described at
http://carlit.toulouse.inra.fr/cgi-bin/awki.cgi/ToolBarIntro and available for download at
https://mulcyber.toulouse.inra.fr/frs/?group_id=29. There is no JNI wrapper for toulbar2,
thus our orientation algorithm will generate a weighted CSP instance in the XML XCSP format,
use toulbar2 to solve the CSP, and read the CSP solution to the orient the network.
This requires running EOMain twice.

************************************

BioGRID data can be converted into the file format required by the orientation
algorithms via the standalone Java file PPI.java.

The usage is as follows:
PPI <BioGRID_file> <experiment_types_file> <intermediate_output_file> <final_output_file>

<BioGrid_file>: the file BIOGRID-ORGANISM-<species>-2.*.*.tab.txt from the zip file
   BIOGRID-ORGANISM-2.*.*.tab.zip, which can be downloaded from http://www.thebiogrid.org/downloads.php.
   For example, BIOGRID-ORGANISM-Saccharomyces_cerevisiae-2.0.51.tab.txt
<experiment_types_file>: a file that assigns weights to evidence types.  See the example file BioGRID_exp_types.txt
   for the correct format
<intermediate_output_file>: a temporary file that will show how many occurrences of each experimental type
   were found for each interaction
<final_output_file>: the name of the file that will be used as input for the orientation algorithms

There is a small block of commented out code that can be uncommented to ensure that the PPI used
are exclusively between two proteins from the organism of interest based on taxonomy id (e.g. 4932 for yeast).

It may be desirable to filter some edges below a certain confidence threshold before using them as
input for the orientation algorithms.