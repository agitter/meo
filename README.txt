Version 1.1.1

************************************

Use of this code implies you have accepted the terms of the license in license.txt.
If you use the code for a publication, please cite:
Discovering pathways by orienting edges in protein interaction networks.
Anthony Gitter, Judith Klein-Seetharaman, Anupam Gupta, and Ziv Bar-Joseph.
Nucl. Acids Res. (2011) 39 (4): e22 doi:10.1093/nar/gkq1207
http://nar.oxfordjournals.org/content/39/4/e22.full

The code requires Java 5.0 or above.  See FAQ.md for frequently asked questions
and their answers.

A properties file is used to specify which orientation algorithm to run, which
parameters to use, and which data to run.  See sample.props for an example and 
sampleEdges.txt, sampleSources.txt, and sampleTargets.txt for examples of
the formatting required for the data files.  A separate Java program (described
below) is included to preprocess BioGRID PPI such that they are in the required
format.  The character '_' should not appear in any gene names in any files because
it is a reserved special character.

The usage of the orientation algorithms from the JAR file is:
java -jar EOMain.jar <properties_file>

The file specified by the edge.output.file property will contain all edges in the
network that are on satisfied paths, which excludes edges that only appear on paths
in which the source and target are disconnected due to one or more of the edge
orientations.  The edges are annotated as pp for edges that were undirected in the
original network and pd for edges that were originally directed.  The oriented column
specifies whether the edge orientation was fixed, and the weight is the edge weight
in the input network.

The file specified by the path.output.file property lists all paths found
in the original unoriented network, their weights, and whether they are
satisfied after the orientation.  By filtering the unsatisfied paths and sorting the
satisfied paths, it is possible to select an arbitrary number of top paths.  Varying
the number of top paths provides a way to tune the size of the oriented network.  The
highest-scoring paths represent the highest-confidence source-target connections.

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

Due to user feedback, the lp_solve dependency was removed in version 1.1.0 of the network
orientation code, which disables the MIN-k-SAT orientation.  The lp_solve links still exist
but have been commented out of EdgeOrientAlg.java, EOMain.java, and Path.java.  These can be
restored to re-enable the MIN-k-SAT orientation.

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