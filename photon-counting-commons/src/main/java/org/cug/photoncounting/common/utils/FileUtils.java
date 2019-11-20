package org.cug.photoncounting.common.utils;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import org.cug.photoncounting.common.ClusterPoint2D;
import org.cug.photoncounting.common.Point2D;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileUtils {


    /**
     * 获取dbscan数据根目录
     * @return file
     */
    public static File getDbscanDataRootDir() {
        return new File(System.getProperty("user.dir") + File.separatorChar + "photon-counting-dbscan" +
                File.separatorChar + "src" + File.separatorChar + "main" + File.separatorChar + "data");
    }

    /**
     * 获取kmeans数据根目录
     * @return file
     */
    public static File getKmeansDataRootDir() {
        return new File(System.getProperty("user.dir") + File.separatorChar + "photon-counting-kmeans" +
                File.separatorChar + "src" + File.separatorChar + "main" + File.separatorChar + "data");
    }

    /**
     * Read lines from files, and parse line to create {@link Point2D} types' objects.
     *
     * @param points 存入地
     * @param delimiterRegex 读取时分隔符
     * @param files 读取文件
     */
    public static void read2DPointsFromFiles(final List<Point2D> points, String delimiterRegex, File... files) {
        BufferedReader reader = null;
        for (File file : files) {
            try {
                reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
                String point = null;
                while ((point = reader.readLine()) != null) {
                    String[] a = point.split(delimiterRegex);
                    if (a.length == 2) {
                        Point2D kp = new Point2D(Double.parseDouble(a[0]), Double.parseDouble(a[1]));
                        if (!points.contains(kp)) {
                            points.add(kp);
                        }
                    }
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            } finally {
                FileUtils.closeQuietly(reader);
            }
        }
    }

    /**
     * 从文件中读取已聚类的点信息
     * @param points 非噪声点存放地
     * @param noisePoints 噪点存放
     * @param delimiterRegex 读取分隔符
     * @param pointFile 源文件
     */
    public static void read2DClusterPointsFromFile(final Map<Integer, Set<ClusterPoint2D>> points,
                                                   final Set<ClusterPoint2D> noisePoints, String delimiterRegex, File pointFile) {
        BufferedReader reader = null;
        try {
            // collect clustered points
            reader = new BufferedReader(new FileReader(pointFile.getAbsoluteFile()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] a = line.split("[,;\t\\s]+");
                    if (a.length == 3) {
                        int clusterId = Integer.parseInt(a[2]);
                        ClusterPoint2D clusterPoint =
                                new ClusterPoint2D(Double.parseDouble(a[0]), Double.parseDouble(a[1]), clusterId);
                        // collect noise points
                        if (clusterId == -1) {
                            noisePoints.add(clusterPoint);
                            continue;
                        }
                        Set<ClusterPoint2D> set = points.get(clusterId);
                        if (set == null) {
                            set = Sets.newHashSet();
                            points.put(clusterId, set);
                        }
                        set.add(clusterPoint);
                    }
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            FileUtils.closeQuietly(reader);
        }
    }

    public static void read2DPointsFromFile(final Map<Integer, Set<ClusterPoint2D>> points, String delimiterRegex, File pointFile) {
        BufferedReader reader = null;
        try {
            // collect clustered points
            reader = new BufferedReader(new FileReader(pointFile.getAbsoluteFile()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] a = line.split("[,;\t\\s]+");
                    if (a.length == 3) {
                        int clusterId = Integer.parseInt(a[2]);
                        ClusterPoint2D clusterPoint =
                                new ClusterPoint2D(Double.parseDouble(a[0]), Double.parseDouble(a[1]), clusterId);
                        Set<ClusterPoint2D> set = points.get(clusterId);
                        if (set == null) {
                            set = Sets.newHashSet();
                            points.put(clusterId, set);
                        }
                        set.add(clusterPoint);
                    }
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            FileUtils.closeQuietly(reader);
        }
    }

    /**
     * finally语句中的close方法也可能会抛出IOException异常
     * @param closeables 接口
     */
    public static void closeQuietly(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                try {
                    closeable.close();
                } catch (Exception e) {
                }
            }
        }
    }
}
