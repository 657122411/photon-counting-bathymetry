package org.cug.photoncounting.common.utils;

import org.cug.photoncounting.common.ClusterPoint;
import org.cug.photoncounting.common.Point2D;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ClusteringUtils {

    /**
     * 输出点聚类后信息 x y type
     * @param clusterPoints 已聚类后的点
     */
    public static void print2DClusterPoints(Map<Integer, Set<ClusterPoint<Point2D>>> clusterPoints) {
        Iterator<Entry<Integer, Set<ClusterPoint<Point2D>>>> iter = clusterPoints.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Integer, Set<ClusterPoint<Point2D>>> entry = iter.next();
            int clusterId = entry.getKey();
            for (ClusterPoint<Point2D> cp : entry.getValue()) {
                System.out.println(cp.getPoint().getX() + "," + cp.getPoint().getY() + "," + clusterId);
            }
        }
    }
}
