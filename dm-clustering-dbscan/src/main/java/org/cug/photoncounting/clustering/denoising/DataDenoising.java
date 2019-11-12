package org.cug.photoncounting.clustering.denoising;

import com.google.common.collect.Lists;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cug.photoncounting.clustering.common.Point2D;
import org.cug.photoncounting.clustering.common.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author TJH
 */
public class DataDenoising {

    private static final Log LOG = LogFactory.getLog(DataDenoising.class);
    private final List<Point2D> allPoints = Lists.newArrayList();
    private static double minX, minY, maxX, maxY;

    /**
     * 读取源文件数据点信息
     *
     * @param files 源文件
     */
    private void getAllPoints(File... files) {
        FileUtils.read2DPointsFromFiles(allPoints, "[\t,;\\s]+", files);
    }

    /**
     * 获取纵轴横轴分布范围
     */
    private void getRange() {
        List<Point2D> copy = new ArrayList<>();
        for (int i = 0; i < allPoints.size(); i++) {
            copy.add((Point2D) allPoints.get(i).clone());
        }
        copy.sort((o1, o2) -> o1.getY().compareTo(o2.getY()));
        minY = copy.get(0).getY();
        maxY = copy.get(copy.size() - 1).getY();
        copy.sort((o1, o2) -> o1.getX().compareTo(o2.getX()));
        minX = copy.get(0).getX();
        maxX = copy.get(copy.size() - 1).getX();
    }

    /**
     * 密度分布直方图粗去噪
     *
     * @param threshold 有效信号概率分布需达到的阈值
     * @param width     统计块宽
     * @param height    统计块高
     */
    private void denoising(double width, double height, double threshold) {
        LOG.info("---start denoising---");

        try {
            FileOutputStream bos = new FileOutputStream(new File(FileUtils.getDbscanDataRootDir(), "output.txt"));
            System.setOut(new PrintStream(bos));
        } catch (FileNotFoundException e) {
            LOG.error(e.getMessage());
        }

        double startX = minX;
        int flag = 0;
        while (startX <= maxX) {

            double endX = startX + width;
            List<Point2D> tempPoints = Lists.newArrayList();
            for (int i = flag; i < allPoints.size(); i++) {
                if (allPoints.get(i).getX() >= startX && allPoints.get(i).getX() <= endX) {
                    tempPoints.add(allPoints.get(i));
                    flag++;
                } else {
                    break;
                }
            }
            Map<Integer, Integer> map = new TreeMap<>();
            for (int i = (int) Math.ceil(minY / height); i <= (int) Math.ceil(maxY / height); i++) {
                map.put(i, 0);
            }

            for (Point2D tempPoint : tempPoints) {
                int index = (int) Math.ceil(tempPoint.getY() / height);
                map.merge(index, 1, Integer::sum);
            }

            //取value最大峰值
            /*AtomicInteger maxKey = new AtomicInteger();
            map.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(maxEntry -> {
                        maxKey.set(maxEntry.getKey());
                    });*/

            AtomicInteger maxKey = new AtomicInteger((int) Math.ceil(minY / height));

            //空中部分做统计分布
            int sum = 0;
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if (entry.getKey() >= maxKey.intValue()) {
                    sum += entry.getValue();
                }
            }
            double minThreshold = threshold * sum;

            //对空中噪声去噪
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if (entry.getKey() > maxKey.intValue() && (double) entry.getValue() < minThreshold) {
                    entry.setValue(0);
                }
            }

            for (Point2D tempPoint : tempPoints) {
                if (map.get((int) Math.ceil(tempPoint.getY() / height)) != 0) {
                    System.out.println(tempPoint.getX() + " " + tempPoint.getY() + " " + 1);
                } else {
                    System.out.println(tempPoint.getX() + " " + tempPoint.getY() + " " + -1);
                }
            }

            tempPoints.clear();
            map.clear();

            startX += width;
        }

        LOG.info("---end denoising---");
    }

    public static void main(String[] args) {
        DataDenoising d = new DataDenoising();
        d.getAllPoints(new File(FileUtils.getDbscanDataRootDir(), "origin.txt"));
        d.getRange();
        d.denoising(0.04, 0.05, 0.01);
    }
}
