package org.cug.photoncounting.clustering.kmeans;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.cug.photoncounting.clustering.common.ClusterPoint2D;
import org.cug.photoncounting.clustering.common.utils.FileUtils;
import org.cug.photoncounting.clustering.tool.common.ClusteringXYChart;
import org.cug.photoncounting.clustering.tool.utils.ChartUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.VerticalAlignment;
import org.jfree.util.ShapeUtilities;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class KMeansClusteringXYChart extends JFrame implements ClusteringXYChart {

    private static final long serialVersionUID = 1L;
    private String chartTitle;
    private File clusterPointFile;
    private File centroidPointFile;
    private final Map<Integer, Set<ClusterPoint2D>> clusterPoints = Maps.newHashMap();
    private final Map<Integer, Set<ClusterPoint2D>> centroidPoints = Maps.newHashMap();
    private final List<Color> colorSpace = Lists.newArrayList();
    private int centroidPointClusterId;
    private XYSeries centroidSeries;

    public KMeansClusteringXYChart(String chartTitle) throws HeadlessException {
        super();
        this.chartTitle = chartTitle;
    }

    private XYSeriesCollection buildXYDataset() {
        FileUtils.read2DPointsFromFile(clusterPoints, "[\t,;\\s]+", clusterPointFile);
        FileUtils.read2DPointsFromFile(centroidPoints, "[\t,;\\s]+", centroidPointFile);
        return ChartUtils.createXYSeriesCollection(clusterPoints);
    }

    private Set<ClusterPoint2D> getCentroidPoints() {
        Set<ClusterPoint2D> set = Sets.newHashSet();
        for (Set<ClusterPoint2D> values : centroidPoints.values()) {
            set.addAll(values);
        }
        return set;
    }

    @Override
    public void drawXYChart() {
        // create XY dataset from points file
        final XYSeriesCollection xyDataset = buildXYDataset();
        centroidSeries = new XYSeries(centroidPointClusterId);
        xyDataset.addSeries(centroidSeries);

        // create chart & configure XY plot
        JFreeChart jfreechart = ChartFactory.createScatterPlot(null, "X", "Y", xyDataset, PlotOrientation.VERTICAL, true, true, false);
        TextTitle title = new TextTitle(chartTitle, new Font("Lucida Sans Unicode", Font.BOLD, 14),
                Color.DARK_GRAY, RectangleEdge.TOP, HorizontalAlignment.CENTER,
                VerticalAlignment.TOP, RectangleInsets.ZERO_INSETS);
        jfreechart.setTitle(title);

        XYPlot xyPlot = (XYPlot) jfreechart.getPlot();

        // render clustered series
        final XYItemRenderer renderer = xyPlot.getRenderer();
        Map<Integer, Color> colors = ChartUtils.generateXYColors(clusterPoints.keySet(), colorSpace);
        Iterator<Entry<Integer, Color>> iter = colors.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Integer, Color> entry = iter.next();
            renderer.setSeriesPaint(entry.getKey(), entry.getValue());
        }

        // set centroid point display styles
        renderer.setSeriesPaint(centroidPointClusterId, Color.BLACK);
        renderer.setSeriesShape(centroidPointClusterId, ShapeUtilities.createRegularCross(50, 15));

        xyPlot.setDomainCrosshairVisible(true);
        xyPlot.setRangeCrosshairVisible(true);

        NumberAxis domain = (NumberAxis) xyPlot.getDomainAxis();
        domain.setVerticalTickLabels(true);

        final ChartPanel chartPanel = new ChartPanel(jfreechart);
        this.add(chartPanel, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1, false));

        ChartUtils.createToggledButtons(panel, centroidSeries, getCentroidPoints(), "Display Centroids", "Hide Centroids");
        this.add(panel, BorderLayout.SOUTH);
    }

    public void setCentroidPointClusterId(int centroidPointClusterId) {
        this.centroidPointClusterId = centroidPointClusterId;
    }

    public void setCentroidPointFile(File centroidPointFile) {
        this.centroidPointFile = centroidPointFile;
    }

    @Override
    public void setclusterPointFile(File clusterPointFile) {
        this.clusterPointFile = clusterPointFile;
    }

    static enum ClusterType {
        K_MEANS,
        BISECTING_K_MEANS,
        K_MEDOIDS,
        K_MEANS_PLUS_PLUS
    }

    static class Arg {
        final int k;
        final String chartTitle;
        final File centroidPointFile;
        final File clusterPointFile;
        int maxIterations;

        public Arg(int k, String chartTitle, File centroidPointFile, File clusterPointFile) {
            super();
            this.k = k;
            this.chartTitle = chartTitle;
            this.centroidPointFile = centroidPointFile;
            this.clusterPointFile = clusterPointFile;
        }

        public Arg(int k, int maxIterations, String chartTitle, File centroidPointFile, File clusterPointFile) {
            this(k, chartTitle, centroidPointFile, clusterPointFile);
            this.maxIterations = maxIterations;
        }


    }

    public static void main(String args[]) {
        int k = 10;
        int maxIterations = 1000;
        File dir = FileUtils.getKmeansDataRootDir();

        final Map<ClusterType, Arg> configs = Maps.newHashMap();
        configs.put(ClusterType.K_MEANS, new Arg(k, "K-means [k=" + k + "]",
                new File(dir, "kmeans_" + k + "_center_points.txt"),
                new File(dir, "kmeans_" + k + "_cluster_points.txt")));
        configs.put(ClusterType.BISECTING_K_MEANS, new Arg(k, "Bisecting K-means [k=" + k + "]",
                new File(dir, "bisecting_kmeans_" + k + "_center_points.txt"),
                new File(dir, "bisecting_kmeans_" + k + "_cluster_points.txt")));
        configs.put(ClusterType.K_MEDOIDS, new Arg(k, "K-medoids [k=" + k + ", maxIterations=" + maxIterations + "]",
                new File(dir, "kmedoids_" + k + "_" + maxIterations + "_center_points.txt"),
                new File(dir, "kmedoids_" + k + "_" + maxIterations + "_cluster_points.txt")));
        configs.put(ClusterType.K_MEANS_PLUS_PLUS, new Arg(k, "K-means++ [k=" + k + "]",
                new File(dir, "kmeans++_" + k + "_center_points.txt"),
                new File(dir, "kmeans++_" + k + "_cluster_points.txt")));

        final ClusterType which = ClusterType.K_MEANS;
        final Arg arg = configs.get(which);

        final KMeansClusteringXYChart chart = new KMeansClusteringXYChart(arg.chartTitle);
        chart.setclusterPointFile(arg.clusterPointFile);
        chart.setCentroidPointFile(arg.centroidPointFile);
        chart.setCentroidPointClusterId(9999);
        ChartUtils.generateXYChart(chart);
    }

}
