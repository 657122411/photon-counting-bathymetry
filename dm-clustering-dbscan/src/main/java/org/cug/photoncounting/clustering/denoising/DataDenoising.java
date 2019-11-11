package org.cug.photoncounting.clustering.denoising;

import com.google.common.collect.Lists;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cug.photoncounting.clustering.common.Point2D;
import org.cug.photoncounting.clustering.common.utils.FileUtils;

import java.io.File;
import java.util.*;


public class DataDenoising {

    private static final Log LOG = LogFactory.getLog(DataDenoising.class);
    private final List<Point2D> allPoints = Lists.newArrayList();
    private static double minX, minY, maxX, maxY;

    /**
     * 读取源文件数据点信息
     *
     * @param files
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
        Collections.sort(copy, (o1, o2) -> o1.getY().compareTo(o2.getY()));
        minY = copy.get(0).getY();
        maxY = copy.get(copy.size() - 1).getY();
        Collections.sort(copy, (o1, o2) -> o1.getX().compareTo(o2.getX()));
        minX = copy.get(0).getX();
        maxX = copy.get(copy.size() - 1).getX();
    }

    /**
     * 密度分布直方图粗去噪
     *
     * @param width
     * @param height
     */
    private void denoising(double width, double height, double threshold) {
        LOG.info("---start denoising---");

        int indexX = (int) Math.ceil((maxX - minX) / width);
        int indexY = (int) Math.ceil((maxY - minY) / height);

        double startX = allPoints.get(0).getX();
        int flag = 0;
        while(startX <= maxX){

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
            Map<Integer, Integer> map = new HashMap<>();
            for (Point2D tempPoint : tempPoints) {
                int index = (int) Math.ceil(tempPoint.getY() / height);
                map.merge(index, 1, Integer::sum);
            }

            int sum = 0;
            for (Integer value : map.values()) {
                sum += value;
            }
            double minThreshold = threshold * sum;
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if ((double)entry.getValue() <= minThreshold) {
                    entry.setValue(0);
                }
            }

            for (Point2D tempPoint : tempPoints) {
                if(map.get((int) Math.ceil(tempPoint.getY() / height))!=0) {
                    System.out.println(tempPoint.getX()+" "+tempPoint.getY()+" "+1);
                }else{
                    System.out.println(tempPoint.getX()+" "+tempPoint.getY()+" "+0);
                }
            }

            tempPoints.clear();
            map.clear();

            startX+=width;
        }

        LOG.info("---end denoising---");
    }


    public static void main(String[] args) {
        DataDenoising d = new DataDenoising();
        d.getAllPoints(new File(FileUtils.getDbscanDataRootDir(), "origin.txt"));
        d.getRange();
        d.denoising(0.1, 0.1, 0.2);
        System.out.println("1");
    }
}
