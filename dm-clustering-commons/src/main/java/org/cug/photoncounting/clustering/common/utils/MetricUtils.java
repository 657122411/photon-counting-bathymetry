package org.cug.photoncounting.clustering.common.utils;

import com.google.common.collect.Multiset;
import org.cug.photoncounting.clustering.common.Point2D;

public class MetricUtils {

    /**
     * 计算欧式距离
     *
     * @param p1 点1
     * @param p2 点2
     * @return double
     */
    public static double euclideanDistance(Point2D p1, Point2D p2) {
        double sum = 0.0;
        double diffX = p1.getX() - p2.getX();
        double diffY = p1.getY() - p2.getY();
        sum += diffX * diffX + diffY * diffY;
        return Math.sqrt(sum);
    }

    /**
     * 计算两点在椭圆上的截断距离
     *
     * @param p1   点1
     * @param p2   点2
     * @param epsA 椭圆长轴
     * @param epsB 椭圆短轴
     * @return 截断距离
     */
    public static double ellipseDistance(Point2D p1, Point2D p2, double epsA, double epsB) {
        //根据两点夹角确定实际eps距离
        double theta = Math.atan((p2.getY() - p1.getY()) / (p2.getX() - p1.getX()));
        double ellipseDist2 = (epsA * epsA * epsB * epsB) / (epsB * epsB +
                epsA * epsA * Math.tan(theta) * Math.tan(theta)) +
                (epsA * epsA * epsB * epsB * Math.tan(theta) * Math.tan(theta)) / (epsB * epsB
                        + epsA * epsA * Math.tan(theta) * Math.tan(theta));

        return Math.sqrt(ellipseDist2);
    }

    /**
     * kmeans计算平均质心
     *
     * @param points 点s
     * @return 质心点
     */
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
