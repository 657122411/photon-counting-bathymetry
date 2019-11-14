package org.cug.photoncounting.clustering.common;

/**
 * 聚类点方法 接口
 * @param <P>
 */
public interface ClusterPoint<P> {

    int getClusterId();

    void setClusterId(int clusterId);

    P getPoint();
}
