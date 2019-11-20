package org.cug.photoncounting.common;

import java.io.File;

/**
 * 聚类 接口
 * @param <P>
 */
public interface Clustering<P> {

    void clustering();

    void setInputFiles(File... files);

    int getClusteredCount();

    ClusteringResult<P> getClusteringResult();
}
