package org.cug.photoncounting.clustering.common.utils;

import com.google.common.collect.Multiset;
import org.cug.photoncounting.clustering.common.Point2D;

public class MetricUtils {

    public static double euclideanDistance(Point2D p1, Point2D p2) {
        double sum = 0.0;
        double diffX = p1.getX() - p2.getX();
        double diffY = p1.getY() - p2.getY();
        sum += diffX * diffX + diffY * diffY;
        return Math.sqrt(sum);
    }

    public static Point2D meanCentroid(Multiset<Point2D> points) {
        double sumX = 0.0;
        double sumY = 0.0;
        for (Point2D p : points) {
            sumX += p.getX();
            sumY += p.getY();
        }
        int count = points.size();
        return new Point2D(sumX / count, sumY / count);
    }

}
