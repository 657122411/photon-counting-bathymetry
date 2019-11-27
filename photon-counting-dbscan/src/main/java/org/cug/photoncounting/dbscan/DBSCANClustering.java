package org.cug.photoncounting.dbscan;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cug.photoncounting.common.*;
import org.cug.photoncounting.common.utils.ClusteringUtils;
import org.cug.photoncounting.common.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.*;


public class DBSCANClustering extends Clustering2D {

    private static final Log LOG = LogFactory.getLog(DBSCANClustering.class);
    private double eps;
    private int minPts;
    private final EpsEstimator epsEstimator;
    private final Map<Point2D, Set<Point2D>> corePointWithNeighbours = Maps.newHashMap();
    private final Set<Point2D> outliers = Sets.newHashSet();
    /**
     * 使一个线程等待其他线程各自执行完毕后再执行。
     * 是通过一个计数器来实现的，计数器的初始值是线程的数量。每当一个线程执行完毕后，计数器的值就-1，当计数器的值为0时，表示所有线程都执行完毕，然后在闭锁上等待的线程就可以恢复工作了。
     */
    private final CountDownLatch latch;
    private final ExecutorService executorService;
    private final BlockingQueue<Point2D> taskQueue;
    private volatile boolean completed = false;
    private int clusterCount;

    public DBSCANClustering(int minPts, int parallism) {
        super(parallism);
        Preconditions.checkArgument(minPts > 0, "Required: minPts > 0!");
        this.minPts = minPts;
        epsEstimator = new EpsEstimator(minPts, parallism);
        latch = new CountDownLatch(parallism);
        executorService = Executors.newCachedThreadPool(new NamedThreadFactory("CORE"));
        taskQueue = new LinkedBlockingQueue<Point2D>();
        LOG.info("Config: minPts=" + minPts + ", parallism=" + parallism);
    }

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
            } catch (InterruptedException e) {
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
                    //确定p1周围需要连接的核心点后，话要把和这些周围一圈直接密度可达的点加入集合set
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

    private Set<Point2D> joinConnectedCorePoints(Set<Point2D> connectedPoints, Set<Point2D> leftCorePoints) {
        Set<Point2D> set = Sets.newHashSet();
        for (Point2D p1 : connectedPoints) {
            set.addAll(joinConnectedCorePoints(p1, leftCorePoints));
        }
        return set;
    }

    private Set<Point2D> joinConnectedCorePoints(Point2D p1, Set<Point2D> leftCorePoints) {
        Set<Point2D> set = Sets.newHashSet();
        for (Point2D p2 : leftCorePoints) {
            double distance = epsEstimator.getDistanceCache().computeDistance(p1, p2);
            if (distance <= eps) {
                // join 2 core points to the same cluster
                set.add(p2);
            }
        }
        // remove connected points
        leftCorePoints.removeAll(set);
        return set;
    }

    public void setMinPts(int minPts) {
        this.minPts = minPts;
    }

    public void setEps(double eps) {
        this.eps = eps;
    }


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
                                // collect a point belonging to the point p1
                                if (distance <= eps) {
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

    public EpsEstimator getEpsEstimator() {
        return epsEstimator;
    }

    public Set<Point2D> getOutliers() {
        return outliers;
    }


    /**
     * 数据量大需要适当调整JVM参数
     * @param args args
     */
    public static void main(String[] args) {
        // generate sorted k-distances sequences
//		int minPts = 4;
//		double eps = 0.0025094814205335555;
//		double eps = 0.004417483559674606;
//		double eps = 0.006147849217403014;

//      int minPts = 8;
//		double eps = 0.004900098978598581;
//      double eps = 0.009566439044911;
//		double eps = 0.013621050253196359;

        int minPts = 8;
        double eps = 2;

        DBSCANClustering c = new DBSCANClustering(minPts, 8);
        c.setInputFiles(new File(FileUtils.getDbscanDataRootDir(), "DensityFilteringInput.txt"));
        c.getEpsEstimator().setOutputKDsitance(false);
        c.generateSortedKDistances();

        // execute clustering procedure
        c.setEps(eps);
        c.setMinPts(4);
        c.clustering();

        try {
            FileOutputStream bos = new FileOutputStream(new File(FileUtils.getDbscanDataRootDir(), "DBScanOutput.txt"));
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
