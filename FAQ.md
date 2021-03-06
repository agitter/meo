# Maximum Edge Orientation Frequently Asked Questions

### Contents
- [Input network directions](#inputnet)
- [Path ranking metrics](#rankpaths)
- [Runtime](#runtime)
- [Gold standard evaluation](#evaluation)


## <a id='inputnet'>Input network directions</a>
**Q:** Can I provide a partially directed input network?

**A:** Yes, the input network can be undirected or partially directed.  In the input edge list, use ```pp``` to indicate an undirected edge and ```pd``` to indicate a directed edge.


## <a id='rankpaths'>Path ranking metrics</a>
**Q:** What are the path ranking metrics?

**A:** Each of the metrics represents a different way of sorting paths to obtain the k top-ranked paths after orienting the network.  The metrics used in the MEO evaluation were:
- Path weight: the product of all vertex, edge, and target scores along the path
- Edge weight: the edge weights for the edges along the path; compute the maximum, minimum, or average weight over the path's edges
- Edge use: the number of valid paths in which the edge is used (an indirect measure of edge importance); compute the maximum, minimum, or average over the path's edges
- Vertex degree: the degree of the vertices along the path; compute the maximum, minimum, or average over the path's vertices


## <a id='runtime'>Runtime</a>
**Q:** How can I reduce the MEO runtime?

**A:** If you are using the recommended random orientation with local search, you can first try to reduce ```rand.restarts``` in the properties file.  If a single random start and local search still takes too long, you will need to reduce the ```max.path.length``` parameter or the network size.  Do not use the random orientation without the local search because it will perform poorly.  The heuristics implemented in SDREM to speed up the network orientation have not yet been ported to the standalone version of MEO.


## <a id='evaluation'>Gold standard evaluation</a>
**Q:** How can I evaluate my algorithm using the reference pathways used to test MEO?

**A:** The MEO evaluation used signaling pathways from KEGG and Science Signaling.  However, the Science Signaling Database of Cell Signaling graphical interface was archived in June 2015.  Contact Anthony Gitter for an archived version of the pathways used in the MEO evaluation.  The `evaluation` branch contains the file [`src/alg/OrientEval.java`](https://github.com/agitter/meo/blob/evaluation/src/alg/OrientEval.java) that implements the longest matching subpath evaluation.
