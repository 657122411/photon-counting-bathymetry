package org.cug.photoncounting.clustering.common;

import java.io.File;

public interface Clustering<P> {

    void clustering();

    void setInputFiles(File... files);

    int getClusteredCount();

    ClusteringResult<P> getClusteringResult();
}
