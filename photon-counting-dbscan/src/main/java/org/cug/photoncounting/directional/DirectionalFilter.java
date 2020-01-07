package org.cug.photoncounting.directional;

import com.google.common.collect.Lists;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cug.photoncounting.common.Point2D;
import org.cug.photoncounting.common.Point2DTheta;
import org.cug.photoncounting.common.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.exp;

/**
 * 方向自适应的光子计数激光雷达滤波方法
 *
 * @author Administrator
 */
public class DirectionalFilter {

    private static final Log LOG = LogFactory.getLog(org.cug.photoncounting.directional.DirectionalFilter.class);
    private final List<Point2D> allPoints = Lists.newArrayList();
    private final List<Point2DTheta> outList = Lists.newArrayList();
    /**
     * 搜索椭圆长轴
     */
    private static double epsA;
    /**
     * 搜索椭圆短轴
     */
    private static double epsB;
    /**
     * 扫描帧长度
     */
    private static double distD;

    private DirectionalFilter(double epsA, double epsB, double distD) {
        DirectionalFilter.epsA = epsA;
        DirectionalFilter.epsB = epsB;
        DirectionalFilter.distD = distD;
    }

    /**
     * 计算每个点的密度值
     */
    private void calcuDensity() {
        LOG.info("---start calcuDensity---");

        for (Point2D p1 : allPoints) {
            //该点周围的帧内点
            List<Point2D> distDPoints = Lists.newArrayList();
            for (Point2D p2 : allPoints) {
                if (Math.abs(p1.getX() - p2.getX()) <= distD && !p2.equals(p1)) {
                    distDPoints.add(p2);
                }
            }

            //角度变化
            Map<Integer, Double> thetaMap = new HashMap<>();
            for (int i = 0; i < 12; i++) {
                int theta = 15 * i;

                double Wp = 0;
                //统计权值
                for (Point2D p2 : distDPoints) {
                    double dis = calcuDis(p1, p2, theta);
                    if (dis <= 1) {
                        Wp += calcuWp(p1, p2, theta);
                    }
                }
                thetaMap.put(theta, Wp);
            }

            //取加权和最大的角度
            AtomicInteger maxKey = new AtomicInteger();
            thetaMap.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(maxEntry -> {
                maxKey.set(maxEntry.getKey());
            });

            LOG.debug(p1.toString() + maxKey.intValue() + " " + thetaMap.get(maxKey.intValue()));
            outList.add(new Point2DTheta(p1, maxKey.intValue(), thetaMap.get(maxKey.intValue())));
        }
        LOG.info("---end calcuDensity---");
    }

    /**
     * 判断是否再椭圆搜索邻域内
     *
     * @param p1    中心点
     * @param p2    带判断点
     * @param theta 椭圆旋转角
     * @return dis值
     */
    private static double calcuDis(Point2D p1, Point2D p2, double theta) {
        //角度转弧度
        double radians = Math.toRadians(theta);
        double tR = Math.cos(radians) * (p1.getX() - p2.getX()) + Math.sin(radians) * (p1.getY() - p2.getY());
        double hR = Math.sin(radians) * (p1.getX() - p2.getX()) - Math.cos(radians) * (p1.getY() - p2.getY());

        return Math.pow(tR, 2) / Math.pow(epsA, 2) + Math.pow(hR, 2) / Math.pow(epsB, 2);
    }

    /**
     * 计算在椭圆搜索域的点权重值
     *
     * @param p1    中心点
     * @param p2    待判断点
     * @param theta 椭圆旋转角
     * @return 该点权重
     */
    private static double calcuWp(Point2D p1, Point2D p2, double theta) {
        //角度转弧度
        double radians = Math.toRadians(theta);
        double tR = Math.cos(radians) * (p1.getX() - p2.getX()) + Math.sin(radians) * (p1.getY() - p2.getY());
        double hR = Math.sin(radians) * (p1.getX() - p2.getX()) - Math.cos(radians) * (p1.getY() - p2.getY());
        double wT = 1 - (Math.abs(tR) / epsA);
        //由于光子计数激光雷达测量的高程测量误差呈现高斯分布，因此Wh采用高斯形的权重，kt计算方式kt = Widthplus* c
        //暂用epsB代替
        double wH = exp(-Math.pow(hR, 2) / epsB);

        return Math.pow(wT, 2) / Math.pow(epsA, 2) + Math.pow(wH, 2) / Math.pow(epsB, 2);
    }

    /**
     * 读取源文件数据点信息
     *
     * @param files 源文件
     */
    private void getAllPoints(File... files) {
        FileUtils.read2DPointsFromFiles(allPoints, "[\t,;\\s]+", files);
    }


    /**
     * 粗去噪
     *
     * @param threshold 权重和限制阈值
     */
    private void roughFilter(double threshold) {
        LOG.info("---start roughFilter---");
        int count = 0;
        //计算得到每一个点的密度值之后，使用一个给定的阈值 T，判断每一个点是否属于噪点:
        for (Point2DTheta p : outList) {
            //信号点
            if (p.getwP() < threshold) {
                p.setFlag(-1);
                count++;
            }
        }
        LOG.info("count:" + count);
        LOG.info("---end roughFilter---");
    }


    /**
     * 精去噪：距离点云密集中心越远密度计算结果越低．因此任意噪声点的密度计算结果会低于空间上临近地面点的密度值．
     *
     * @param circle    搜索半径
     * @param distD     一帧移动距离
     * @param threshold 限制阈值
     */
    public void meticulousFilter(double circle, double distD, double threshold) {
        LOG.info("---start meticulousFilter---");
        int count = 0;

        for (Point2DTheta centerPoint : outList) {
            //取粗去噪后的结果做精去噪
            if (centerPoint.getFlag() != -1) {
                List<Point2DTheta> circleList = Lists.newArrayList();
                //取邻域圆内点
                for (Point2DTheta pointInCircle : outList) {
                    if (pointInCircle.getFlag() != -1
                            && inSameD(centerPoint, pointInCircle, distD)
                            && inCircle(centerPoint, pointInCircle, circle)) {
                        circleList.add(pointInCircle);
                    }
                }
                //取邻域内最大权值
                double maxW = 0.0;
                for (Point2DTheta point : circleList) {
                    if (point.getwP() > maxW) {
                        maxW = point.getwP();
                    }
                }
                //去噪
                if ((maxW - centerPoint.getwP()) > threshold) {
                    centerPoint.setFlag(-1);
                    count++;
                }
            }
        }
        LOG.info("count:" + count);
        LOG.info("---end meticulousFilter---");
    }


    /**
     * 判断点近似为同一帧内采集到的数据
     *
     * @param centerPoint   点1
     * @param pointInCircle 点2
     * @param distD         一帧移动距离
     * @return boolean
     */
    private boolean inSameD(Point2DTheta centerPoint, Point2DTheta pointInCircle, double distD) {
        double distance = Math.abs(centerPoint.getX() - pointInCircle.getX());
        return distance < distD;
    }

    /**
     * 判断点2是否在点1邻域圆内
     *
     * @param centerPoint   点1
     * @param pointInCircle 点2
     * @param circle        邻域半径
     * @return boolean
     */
    private boolean inCircle(Point2DTheta centerPoint, Point2DTheta pointInCircle, double circle) {
        double distance2 = Math.pow((centerPoint.getX() - pointInCircle.getX()), 2) + Math.pow((centerPoint.getY() - pointInCircle.getY()), 2);
        double radius2 = Math.pow(circle, 2);
        return distance2 <= radius2;
    }

    /**
     * 输出数据
     */
    private void outputData() {
        LOG.info("---start outputData---");
        PrintStream out = System.out;
        try {
            FileOutputStream bos = new FileOutputStream(new File(FileUtils.getDbscanDataRootDir(), "DirectionalOutput.txt"));
            System.setOut(new PrintStream(bos));
        } catch (FileNotFoundException e) {
            LOG.error(e.getMessage());
        }

        for (Point2DTheta p : outList) {
            System.out.println(p.toString());
        }

        LOG.info("---end outputData---");
    }

    public static void main(String[] args) {
        DirectionalFilter d = new DirectionalFilter(5, 0.5, 3);
        d.getAllPoints(new File(FileUtils.getDbscanDataRootDir(), "DirectionalInput.txt"));
        d.calcuDensity();
        d.roughFilter(60);
        d.meticulousFilter(5, 3, 1000);
        d.outputData();
    }
}
