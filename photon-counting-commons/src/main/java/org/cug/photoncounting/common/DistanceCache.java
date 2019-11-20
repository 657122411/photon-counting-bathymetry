package org.cug.photoncounting.common;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import org.cug.photoncounting.common.utils.MetricUtils;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class DistanceCache {

    private final Cache<Set<Point2D>, Double> distanceCache;

    private double epsA;
    private double epsB;
    private final Cache<Set<Point2D>, Double> ellipseDistCache;


    public DistanceCache(int cacheSize) {
        Preconditions.checkArgument(cacheSize > 0, "Cache size SHOULD be: cacheSize > 0!");
        distanceCache = CacheBuilder.newBuilder().maximumSize(cacheSize).build();
        ellipseDistCache = null;
    }

    public DistanceCache(int cacheSize, double epsA, double epsB) {
        Preconditions.checkArgument(cacheSize > 0, "Cache size SHOULD be: cacheSize > 0!");
        distanceCache = CacheBuilder.newBuilder().maximumSize(cacheSize).build();
        ellipseDistCache = CacheBuilder.newBuilder().maximumSize(cacheSize).build();
        this.epsA = epsA;
        this.epsB = epsB;
    }

    public double computeDistance(final Point2D p1, final Point2D p2) {
        Set<Point2D> set = Sets.newHashSet(p1, p2);
        Double distance = 0.0;
        try {
            distance = distanceCache.get(set, new Callable<Double>() {
                @Override
                public Double call() throws Exception {
                    return MetricUtils.euclideanDistance(p1, p2);
                }
            });
        } catch (ExecutionException e) {
            Throwables.propagate(e);
        }
        return distance;
    }


    //计算两点连线在椭圆上的截断距离
    public double computeEllipseDist(final Point2D p1, final Point2D p2) {
        Set<Point2D> set = Sets.newHashSet(p1, p2);
        Double distance = 0.0;
        try {
            distance = ellipseDistCache.get(set, new Callable<Double>() {
                @Override
                public Double call() throws Exception {
                    return MetricUtils.ellipseDistance(p1, p2, epsA, epsB);
                }
            });
        } catch (ExecutionException e) {
            Throwables.propagate(e);
        }
        return distance;
    }
}
