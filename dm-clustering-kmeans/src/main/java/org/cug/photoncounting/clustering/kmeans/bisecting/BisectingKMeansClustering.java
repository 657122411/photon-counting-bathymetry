package org.cug.photoncounting.clustering.kmeans.bisecting;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cug.photoncounting.clustering.common.*;
import org.cug.photoncounting.clustering.common.utils.ClusteringUtils;
import org.cug.photoncounting.clustering.common.utils.FileUtils;
import org.cug.photoncounting.clustering.common.utils.MetricUtils;
import org.cug.photoncounting.clustering.kmeans.KMeansClustering;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class BisectingKMeansClustering extends Clustering2D {

    private static final Log LOG = LogFactory.getLog(BisectingKMeansClustering.class);
    private final int k;
    private final int m; // times of bisecting trials
    private final float maxMovingPointRate;
    private final Set<CenterPoint> centroidSet = Sets.newTreeSet();

    public BisectingKMeansClustering(int k, float maxMovingPointRate, int m) {
        this(k, m, maxMovingPointRate, 1);
    }

    public BisectingKMeansClustering(int k, int m, float maxMovingPointRate, int parallism) {
        super(parallism);
        this.k = k;
        this.m = m;
        this.maxMovingPointRate = maxMovingPointRate;
    }

    @Override
    public void clustering() {
        // parse sample files
        final List<Point2D> allPoints = Lists.newArrayList();
        FileUtils.read2DPointsFromFiles(allPoints, "[\t,;\\s]+", inputFiles);

        final int bisectingK = 2;
        int bisectingIterations = 0;
        int maxInterations = 20;
        List<Point2D> points = allPoints;
        final Map<CenterPoint, Set<ClusterPoint<Point2D>>> clusteringPoints = Maps.newConcurrentMap();
        while (clusteringPoints.size() <= k) {
            LOG.info("Start bisecting iterations: #" + (++bisectingIterations) + ", bisectingK=" + bisectingK + ", maxMovingPointRate=" + maxMovingPointRate +
                    ", maxInterations=" + maxInterations + ", parallism=" + parallism);

            // for k=bisectingK, execute k-means clustering

            // bisecting trials
            KMeansClustering bestBisectingKmeans = null;
            double minTotalSSE = Double.MAX_VALUE;
            for (int i = 0; i < m; i++) {
                final KMeansClustering kmeans = new KMeansClustering(bisectingK, maxMovingPointRate, maxInterations, parallism);
                kmeans.initialize(points);
                // the clustering result should have 2 clusters
                kmeans.clustering();
                double currentTotalSSE = computeTotalSSE(kmeans.getCenterPointSet(), kmeans.getClusteringResult());
                if (bestBisectingKmeans == null) {
                    bestBisectingKmeans = kmeans;
                    minTotalSSE = currentTotalSSE;
                } else {
                    if (currentTotalSSE < minTotalSSE) {
                        bestBisectingKmeans = kmeans;
                        minTotalSSE = currentTotalSSE;
                    }
                }
                LOG.info("Bisecting trial <<" + i + ">> : minTotalSSE=" + minTotalSSE + ", currentTotalSSE=" + currentTotalSSE);
            }
            LOG.info("Best biscting: minTotalSSE=" + minTotalSSE);

            // merge cluster points for choosing cluster bisected again
            int id = generateNewClusterId(clusteringPoints.keySet());
            Set<CenterPoint> bisectedCentroids = bestBisectingKmeans.getCenterPointSet();
            merge(clusteringPoints, id, bisectedCentroids, bestBisectingKmeans.getClusteringResult().getClusteredPoints());

            if (clusteringPoints.size() == k) {
                break;
            }

            // compute cluster to be bisected
            ClusterInfo cluster = chooseClusterToBisect(clusteringPoints);
            // remove centroid from collected clusters map
            clusteringPoints.remove(cluster.centroidToBisect);
            LOG.info("Cluster to be bisected: " + cluster);

            points = Lists.newArrayList();
            for (ClusterPoint<Point2D> cp : cluster.clusterPointsToBisect) {
                points.add(cp.getPoint());
            }

            LOG.info("Finish bisecting iterations: #" + bisectingIterations + ", clusterSize=" + clusteringPoints.size());
        }

        // finally transform to result format
        Iterator<Entry<CenterPoint, Set<ClusterPoint<Point2D>>>> iter = clusteringPoints.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<CenterPoint, Set<ClusterPoint<Point2D>>> entry = iter.next();
            clusteredPoints.put(entry.getKey().getId(), entry.getValue());
            centroidSet.add(entry.getKey());
        }
    }

    private void merge(final Map<CenterPoint, Set<ClusterPoint<Point2D>>> clusteringPoints,
                       int id, Set<CenterPoint> bisectedCentroids,
                       Map<Integer, Set<ClusterPoint<Point2D>>> bisectedClusterPoints) {
        int startId = id;
        for (CenterPoint centroid : bisectedCentroids) {
            Set<ClusterPoint<Point2D>> set = bisectedClusterPoints.get(centroid.getId());
            centroid.setId(startId);
            // here, we don't update cluster id for ClusterPoint object in set,
            // we should do it until iterate the set for choosing cluster to be bisected
            clusteringPoints.put(centroid, set);
            startId++;
        }
    }

    private int generateNewClusterId(Set<CenterPoint> keptCentroids) {
        int id = -1;
        for (CenterPoint centroid : keptCentroids) {
            if (centroid.getId() > id) {
                id = centroid.getId();
            }
        }
        return id + 1;
    }

    private ClusterInfo chooseClusterToBisect(Map<CenterPoint, Set<ClusterPoint<Point2D>>> clusteringPoints) {
        double maxSSE = 0.0;
        int clusterIdWithMaxSSE = -1;
        CenterPoint centroidToBisect = null;
        Set<ClusterPoint<Point2D>> clusterToBisect = null;
        Iterator<Entry<CenterPoint, Set<ClusterPoint<Point2D>>>> iter = clusteringPoints.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<CenterPoint, Set<ClusterPoint<Point2D>>> entry = iter.next();
            CenterPoint centroid = entry.getKey();
            Set<ClusterPoint<Point2D>> cpSet = entry.getValue();
            double sse = computeSSE(centroid, cpSet);
            if (sse > maxSSE) {
                maxSSE = sse;
                clusterIdWithMaxSSE = centroid.getId();
                centroidToBisect = centroid;
                clusterToBisect = cpSet;
            }
        }
        return new ClusterInfo(clusterIdWithMaxSSE, centroidToBisect, clusterToBisect, maxSSE);
    }

    private double computeTotalSSE(Set<CenterPoint> centroids, ClusteringResult<Point2D> clusteringResult) {
        double sse = 0.0;
        for (CenterPoint center : centroids) {
            int clusterId = center.getId();
            for (ClusterPoint<Point2D> p : clusteringResult.getClusteredPoints().get(clusterId)) {
                double distance = MetricUtils.euclideanDistance(p.getPoint(), center);
                sse += distance * distance;
            }
        }
        return sse;
    }

    private double computeSSE(CenterPoint centroid, Set<ClusterPoint<Point2D>> cpSet) {
        double sse = 0.0;
        for (ClusterPoint<Point2D> cp : cpSet) {
            // update cluster id for ClusterPoint object
            cp.setClusterId(centroid.getId());
            double distance = MetricUtils.euclideanDistance(cp.getPoint(), centroid);
            sse += distance * distance;
        }
        return sse;
    }

    private class ClusterInfo {

        private final int id;
        private final CenterPoint centroidToBisect;
        private final Set<ClusterPoint<Point2D>> clusterPointsToBisect;
        private final double maxSSE;

        public ClusterInfo(int id, CenterPoint centroidToBisect, Set<ClusterPoint<Point2D>> clusterPointsToBisect, double maxSSE) {
            super();
            this.id = id;
            this.centroidToBisect = centroidToBisect;
            this.clusterPointsToBisect = clusterPointsToBisect;
            this.maxSSE = maxSSE;
        }

        @Override
        public String toString() {
            return "ClusterInfo[id=" + id + ", points=" + clusterPointsToBisect.size() + ", maxSSE=" + maxSSE + "]";
        }
    }

    public Set<CenterPoint> getCentroidSet() {
        return centroidSet;
    }

    public static void main(String[] args) {
        int k = 10;
        int m = 25;
        float maxMovingPointRate = 0.01f;
        int parallism = 5;
        BisectingKMeansClustering bisecting = new BisectingKMeansClustering(k, m, maxMovingPointRate, parallism);
        File dir = FileUtils.getKmeansDataRootDir();
        bisecting.setInputFiles(new File(dir, "points.txt"));
        bisecting.clustering();

        System.out.println("== Clustered points ==");
        ClusteringResult<Point2D> result = bisecting.getClusteringResult();
        ClusteringUtils.print2DClusterPoints(result.getClusteredPoints());

        // print centroids
        System.out.println("== Centroid points ==");
        for (CenterPoint p : bisecting.getCentroidSet()) {
            System.out.println(p.getX() + "," + p.getY() + "," + p.getId());
        }
    }

}
