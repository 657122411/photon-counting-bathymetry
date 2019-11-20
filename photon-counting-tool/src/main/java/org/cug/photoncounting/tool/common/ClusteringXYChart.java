package org.cug.photoncounting.tool.common;

import java.io.File;


public interface ClusteringXYChart {

    /**
     * Draw XY chart from the given cluster point set.
     */
    void drawXYChart();

    /**
     * after clustering, we should write generated cluster points to file <code>clusterPointFile</code>,
     * and set the cluster point file to display on the XY chart.
     *
     * @param clusterPointFile
     */
    void setclusterPointFile(File clusterPointFile);

}
