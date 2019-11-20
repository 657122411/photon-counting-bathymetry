package org.cug.photoncounting.common;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

public abstract class Clustering2D extends AbstractClustering<Point2D> {

    protected final Map<Integer, Set<ClusterPoint<Point2D>>> clusteredPoints = Maps.newTreeMap();

    public Clustering2D() {
        this(1);
    }

    public Clustering2D(int parallism) {
        super(parallism);
        Preconditions.checkArgument(parallism > 0, "Required: parallism > 0!");
        clusteringResult.setClusteredPoints(clusteredPoints);
    }
}
