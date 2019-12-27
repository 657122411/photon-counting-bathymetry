package org.cug.photoncounting.densityfiltering;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cug.photoncounting.common.*;
import org.cug.photoncounting.common.utils.ClusteringUtils;
import org.cug.photoncounting.common.utils.FileUtils;
import sun.plugin2.jvm.CircularByteBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 密度滤波实现（椭圆搜索邻域DBSCAN）
 * ---{预处理：求两两点间距离}---
 * (1) 首先将数据集D中的所有对象标记为未处理状态
 * (2) for（数据集D中每个对象p） do
 * (3)    if （p已经归入某个簇或标记为噪声） then
 * (4)         continue;
 * (5)    else
 * (6)         检查对象p的Eps邻域 NEps(p) ；
 * (7)         if (NEps(p)包含的对象数小于MinPts) then
 * (8)                  标记对象p为边界点或噪声点；
 * (9)         else
 * (10)                 标记对象p为核心点，并建立新簇C, 并将p邻域内所有点加入C
 * (11)                 for (NEps(p)中所有尚未被处理的对象q)  do
 * (12)                       检查其Eps邻域NEps(q)，若NEps(q)包含至少MinPts个对象，则将NEps(q)中未归入任何一个簇的对象加入C；
 * (13)                 end for
 * (14)        end if
 * (15)    end if
 * (16) end for
 *
 * @author TJH
 */
public class DensityFiltering extends Clustering2D {

    private static final Log LOG = LogFactory.getLog(DensityFiltering.class);
    /**
     * 椭圆长轴
     */
    private double epsA;
    /**
     * 椭圆半轴
     */
    private double epsB;
    /**
     * 最小包含点数
     */
    private int minPts;
    private final ABEpsEstimator epsEstimator;
    /**
     * 核心点及其边界点
     */
    private final Map<Point2D, Set<Point2D>> corePointWithNeighbours = Maps.newHashMap();
    /**
     * 离群点
     */
    private final Set<Point2D> outliers = Sets.newHashSet();
    /**
     * 使一个线程等待其他线程各自执行完毕后再执行。通过一个计数器来实现，初始值是线程的数量，
     * 每当一个线程执行完毕后，计数器的值就-1，当计数器的值为0时，表示所有线程都执行完毕，然后在闭锁上等待的线程就可以恢复工作了。
     */
    private final CountDownLatch latch;
    private final ExecutorService executorService;
    private final BlockingQueue<Point2D> taskQueue;
    private volatile boolean completed = false;
    private int clusterCount;

    public DensityFiltering(int minPts, int parallism, double epsA, double epsB) {
        super(parallism);
        Preconditions.checkArgument(minPts > 0, "Required: minPts > 0!");
        this.minPts = minPts;
        epsEstimator = new ABEpsEstimator(minPts, parallism, epsA, epsB);
        latch = new CountDownLatch(parallism);
        executorService = Executors.newCachedThreadPool(new NamedThreadFactory("CORE"));
        taskQueue = new LinkedBlockingQueue<Point2D>();
        LOG.info("Config: minPts=" + minPts + ", parallism=" + parallism);
    }

    /**
     * 统计两两点距离，computeKDistance对每个点记录距离其第k近的距离，estimateEps将collection按k-dist小到大排序输出
     */
    public void generateSortedKDistances() {
        Preconditions.checkArgument(inputFiles != null, "inputFiles == null");
        epsEstimator.computeKDistance(inputFiles).estimateEps();
    }

    /**
     * 核心代码：聚类
     */
    @Override
    public void clustering() {
        // recognize core points
        //核心点提取（会将边界点先置入噪点集）
        try {
            for (int i = 0; i < parallism; i++) {
                //线程任务类
                CorePointCalculator calculator = new CorePointCalculator();
                //多线程执行器
                executorService.execute(calculator);
                LOG.info("Core point calculator started: " + calculator);
            }

            Iterator<Point2D> iter = epsEstimator.allPointIterator();
            while (iter.hasNext()) {
                Point2D p = iter.next();
                while (!taskQueue.offer(p)) {
                    Thread.sleep(10);
                }
                LOG.debug("Added to taskQueue: " + p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                completed = true;
                latch.await();
            } catch (InterruptedException ignored) {
            }
            LOG.info("Shutdown executor service: " + executorService);
            executorService.shutdown();
        }
        LOG.info("Point statistics: corePointSize=" + corePointWithNeighbours.keySet().size());

        // join connected core points
        //连接中心点
        LOG.info("Joining connected core points ...");
        final Map<Point2D, Set<Point2D>> clusteringPoints = Maps.newHashMap();
        Set<Point2D> corePoints = Sets.newHashSet(corePointWithNeighbours.keySet());
        while (true) {
            Set<Point2D> set = Sets.newHashSet();
            Iterator<Point2D> iter = corePoints.iterator();
            if (iter.hasNext()) {
                Point2D p = iter.next();
                iter.remove();
                //核心点间距小于eps为同簇，加入到集合connectedPoints
                Set<Point2D> connectedPoints = joinConnectedCorePoints(p, corePoints);
                //暂时移动到se集合中
                set.addAll(connectedPoints);
                while (!connectedPoints.isEmpty()) {
                    //确定p1周围需要连接的核心点后，还要把和这些周围一圈直接密度可达的点加入集合set
                    connectedPoints = joinConnectedCorePoints(connectedPoints, corePoints);
                    set.addAll(connectedPoints);
                }
                //该点对应的直接间接可达核心点加入Map<Point2D, Set<Point2D>>
                clusteringPoints.put(p, set);
            } else {
                break;
            }
        }
        LOG.info("Connected core points computed.");

        // process outliers
        //噪声点集需要去除边界点
        Iterator<Point2D> iter = outliers.iterator();
        while (iter.hasNext()) {
            Point2D np = iter.next();
            //噪点集的点出现在核心点临点集中
            if (corePointWithNeighbours.containsKey(np)) {
                //非噪点
                iter.remove();
            } else {
                for (Set<Point2D> set : corePointWithNeighbours.values()) {
                    if (set.contains(np)) {
                        iter.remove();
                        break;
                    }
                }
            }
        }

        // generate clustering result
        //生成聚类结果，主要将clusteringPoints（点，set）转化为clusteredPoints（id,set）
        Iterator<Entry<Point2D, Set<Point2D>>> coreIter = clusteringPoints.entrySet().iterator();
        int id = 0;
        while (coreIter.hasNext()) {
            Entry<Point2D, Set<Point2D>> core = coreIter.next();
            Set<Point2D> set = Sets.newHashSet();
            //会把自身点放入
            set.add(core.getKey());
            set.addAll(corePointWithNeighbours.get(core.getKey()));
            for (Point2D p : core.getValue()) {
                set.addAll(core.getValue());
                //边界点可能属于多个簇，寻找簇中已知点的所有边界点
                set.addAll(corePointWithNeighbours.get(p));
            }

            Set<ClusterPoint<Point2D>> clusterSet = Sets.newHashSet();
            for (Point2D p : set) {
                //赋值属性id做分簇
                clusterSet.add(new ClusterPoint2D(p, id));
            }
            clusteredPoints.put(id, clusterSet);
            ++id;
        }

        LOG.info("Finished clustering: clusterCount=" + clusterCount + ", outliersCount=" + outliers.size());
    }


    /**
     * 确定p1周围需要连接的核心点后，还要把和这些周围一圈直接密度可达的点加入集合set
     *
     * @param connectedPoints 核心点集
     * @param leftCorePoints  该核心点的边界点集
     * @return 确定的一个簇点集
     */
    private Set<Point2D> joinConnectedCorePoints(Set<Point2D> connectedPoints, Set<Point2D> leftCorePoints) {
        Set<Point2D> set = Sets.newHashSet();
        for (Point2D p1 : connectedPoints) {
            set.addAll(joinConnectedCorePoints(p1, leftCorePoints));
        }
        return set;
    }


    /**
     * 确定p1周围需要连接的核心点后，还要把和这些周围一圈直接密度可达的点加入集合set
     *
     * @param p1             核心点
     * @param leftCorePoints 该核心点的边界点集
     * @return 确定的一个簇点集
     */
    private Set<Point2D> joinConnectedCorePoints(Point2D p1, Set<Point2D> leftCorePoints) {
        Set<Point2D> set = Sets.newHashSet();

        int count = 6;
        HashMap<Integer, Set<Point2D>> circleMap = new HashMap<>(6);

        for (int i = 0; i < count; i++) {
            Set<Point2D> temp = Sets.newHashSet();
            for (Point2D p2 : leftCorePoints) {
                //if (p2.getX() > p1.getX()) {
                    double distance = epsEstimator.getDistanceCache().computeDistance(p1, p2);
                    //根据两点夹角确定实际eps距离
                    double ellipseDist = computeEllipseDistByAngle(p1, p2, i * (180.0 / count));
                    if (distance <= ellipseDist) {
                        // join 2 core points to the same cluster
                        temp.add(p2);
                    }
                //}
            }

            circleMap.put(i, temp);
        }

        //取value最大峰值
        int index = 0, num = 0;
        for (Map.Entry<Integer, Set<Point2D>> entry : circleMap.entrySet()) {
            if (entry.getValue().size() > num) {
                num = entry.getValue().size();
                index = entry.getKey();
            }
        }


        // remove connected points
        leftCorePoints.removeAll(circleMap.get(index));
        return circleMap.get(index);
    }

    private double computeEllipseDistByAngle(final Point2D p1, final Point2D p2, final double angle) {
        Double ellipseDist2 = 0.0;

        double theta = Math.atan((p2.getY() - p1.getY()) / (p2.getX() - p1.getX())) - Math.toRadians(angle);
        ellipseDist2 = (epsA * epsA * epsB * epsB) / (epsB * epsB +
                epsA * epsA * Math.tan(theta) * Math.tan(theta)) +
                (epsA * epsA * epsB * epsB * Math.tan(theta) * Math.tan(theta)) / (epsB * epsB
                        + epsA * epsA * Math.tan(theta) * Math.tan(theta));

        return Math.sqrt(ellipseDist2);
    }

    public void setMinPts(int minPts) {
        this.minPts = minPts;
    }

    public void setEps(double epsA, double epsB) {
        this.epsA = epsA;
        this.epsB = epsB;
    }


    /**
     * 核心点计算线程
     */
    private final class CorePointCalculator extends Thread {

        private final Log LOG = LogFactory.getLog(CorePointCalculator.class);
        private int processedPoints;

        @Override
        public void run() {
            try {
                Thread.sleep(1000);
                while (true) {
                    while (!taskQueue.isEmpty()) {
                        Point2D p1 = taskQueue.poll();
                        ++processedPoints;
                        //计算点p1与另外点距离，如小于阈值eps则将点2放入set
                        Set<Point2D> set = Sets.newHashSet();
                        Iterator<Point2D> iter = epsEstimator.allPointIterator();
                        while (iter.hasNext()) {
                            Point2D p2 = iter.next();
                            if (!p2.equals(p1)) {
                                double distance = epsEstimator.getDistanceCache().computeDistance(p1, p2);

                                //根据两点夹角确定实际eps距离
                                double ellipseDist = epsEstimator.getDistanceCache().computeEllipseDist(p1, p2);

                                // collect a point belonging to the point p1
                                if (distance <= ellipseDist) {
                                    set.add(p2);
                                }
                            }
                        }
                        // decide whether p1 is core point
                        if (set.size() >= minPts) {
                            //若点2的集合大于阈值minpts,则p1为核心点，将p1，所属点集放入hashmap
                            corePointWithNeighbours.put(p1, set);
                            LOG.debug("Decide core point: point" + p1 + ", set=" + set);
                        } else {
                            // here, perhaps a point was wrongly put into outliers set
                            // afterwards we should remedy outliers set
                            //若p1不满足他所属范围点大于阈值，将其置入噪点集
                            if (!outliers.contains(p1)) {
                                outliers.add(p1);
                            }
                        }

                    }

                    Thread.sleep(100);

                    if (taskQueue.isEmpty() && completed) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
                LOG.info("Calculator exit, STAT: [id=" + this + ", processedPoints=" + processedPoints + "]");
            }
        }
    }

    public ABEpsEstimator getEpsEstimator() {
        return epsEstimator;
    }

    public Set<Point2D> getOutliers() {
        return outliers;
    }

    /**
     * 数据量大的情况下需要设置JVM参数调整最大堆大小-Xms??m -Xmx??m
     *
     * @param args args
     */
    public static void main(String[] args) {
        // generate sorted k-distances sequences
        int minPts = 8;
        double epsA = 7.5;
        double epsB = 0.1;

        //获取cpu核心数
        final int availProcessors = Runtime.getRuntime().availableProcessors();
        DensityFiltering c = new DensityFiltering(minPts, availProcessors + 1, epsA, epsB);
        c.setInputFiles(new File(FileUtils.getDbscanDataRootDir(), "DensityFilteringInput.txt"));

        c.getEpsEstimator().setOutputKDsitance(false);
        c.generateSortedKDistances();

        // execute clustering procedure
        c.setEps(epsA, epsB);
        c.setMinPts(4);
        c.clustering();

        try {
            FileOutputStream bos = new FileOutputStream(new File(FileUtils.getDbscanDataRootDir(), "DensityFilteringOutput.txt"));
            System.setOut(new PrintStream(bos));
        } catch (FileNotFoundException e) {
            LOG.error(e.getMessage());
        }

        LOG.info("== Clustered points ==");
        ClusteringResult<Point2D> result = c.getClusteringResult();
        ClusteringUtils.print2DClusterPoints(result.getClusteredPoints());

        // print outliers
        // 噪点集赋簇值为-1
        int outliersClusterId = -1;
        LOG.info("== Outliers ==");
        for (Point2D p : c.getOutliers()) {
            System.out.println(p.getX() + "," + p.getY() + "," + outliersClusterId);
        }
    }

}
