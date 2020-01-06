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

/**
 * 方向自适应的光子计数激光雷达滤波方法
 *
 * @author Administrator
 */
public class DirectionalFilter {

    private static final Log LOG = LogFactory.getLog(org.cug.photoncounting.directional.DirectionalFilter.class);
    private final List<Point2D> allPoints = Lists.newArrayList();
    private final List<Point2DTheta> outList = Lists.newArrayList();
    public static double epsA;
    public static double epsB;
    private static double distD;

    public DirectionalFilter(double epsA, double epsB, double distD) {
        this.epsA = epsA;
        this.epsB = epsB;
        this.distD = distD;
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
                        Wp += dis;
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

    }


    /**
     * 计算dis 值
     *
     * @param p1
     * @param p2
     * @return
     */
    private static double calcuDis(Point2D p1, Point2D p2, double theta) {
        //角度转弧度
        double radians = Math.toRadians(theta);
        double tR = Math.cos(radians) * (p1.getX() - p2.getX()) + Math.sin(radians) * (p1.getY() - p2.getY());
        double hR = Math.sin(radians) * (p1.getX() - p2.getX()) - Math.cos(radians) * (p1.getY() - p2.getY());
        double wT = 1 - Math.abs(tR) / epsA;
        //由于光子计数激光雷达测量的高程 测量误差呈现高斯分布，因此 Wh 采用高斯形的权重， kt 计算方式如下: kt = Widthplus* c
        //暂用epsB代替
        double wH = Math.exp(-Math.pow(hR, 2) / epsB);

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
     * 粗去噪:阈值 T，
     */
    private void roughFilter(double T) {
        LOG.info("---start roughFilter---");
        //输出
        PrintStream out = System.out;
        try {
            FileOutputStream bos = new FileOutputStream(new File(FileUtils.getDbscanDataRootDir(), "DirectionalOutput.txt"));
            System.setOut(new PrintStream(bos));
        } catch (FileNotFoundException e) {
            LOG.error(e.getMessage());
        }

        for (Point2DTheta p : outList) {
            //信号点
            if (p.getwP() <= T) {
                System.out.println(p.getX() + " " + p.getY() + " 0");
            } else {
                System.out.println(p.getX() + " " + p.getY() + " -1");
            }
        }

    }

    public static void main(String[] args) {
        DirectionalFilter d = new DirectionalFilter(5, 2, 2);
        d.getAllPoints(new File(FileUtils.getDbscanDataRootDir(), "DirectionalInput.txt"));
        d.calcuDensity();
        d.roughFilter(60);
    }
}
