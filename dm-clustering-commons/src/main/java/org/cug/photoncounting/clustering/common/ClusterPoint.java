package org.cug.photoncounting.clustering.common;

public interface ClusterPoint<P> {

    int getClusterId();

    void setClusterId(int clusterId);

    P getPoint();
}
