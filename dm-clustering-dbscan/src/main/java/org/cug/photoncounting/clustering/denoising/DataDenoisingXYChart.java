package org.cug.photoncounting.clustering.denoising;

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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataDenoisingXYChart extends JFrame implements ClusteringXYChart {

    private static final long serialVersionUID = 1L;
    private String chartTitle;
    private File clusterPointFile;
    private Map<Integer, Set<ClusterPoint2D>> clusterPoints = Maps.newHashMap();
    private final List<Color> colorSpace = Lists.newArrayList();
    private final Set<ClusterPoint2D> outliers = Sets.newHashSet();
    private int outlierClusterId;
    private XYSeries outlierXYSeries;

    public DataDenoisingXYChart(String chartTitle) throws HeadlessException {
        super();
        this.chartTitle = chartTitle;
    }

    private XYSeriesCollection buildXYDataset() {
        FileUtils.read2DClusterPointsFromFile(clusterPoints, outliers, "[\t,;\\s]+", clusterPointFile);
        return ChartUtils.createXYSeriesCollection(clusterPoints);
    }

    @Override
    public void drawXYChart() {
        // create xy dataset from points file
        final XYSeriesCollection xyDataset = buildXYDataset();
        outlierXYSeries = new XYSeries(outlierClusterId);
        xyDataset.addSeries(outlierXYSeries);

        // create chart & configure xy plot
        JFreeChart jfreechart = ChartFactory.createScatterPlot(null, "X", "Y", xyDataset, PlotOrientation.VERTICAL, true, true, false);
        TextTitle title = new TextTitle(chartTitle, new Font("Lucida Sans Unicode", Font.BOLD, 14),
                Color.DARK_GRAY, RectangleEdge.TOP, HorizontalAlignment.CENTER,
                VerticalAlignment.TOP, RectangleInsets.ZERO_INSETS);
        jfreechart.setTitle(title);

        XYPlot xyPlot = (XYPlot) jfreechart.getPlot();
        xyPlot.setDomainCrosshairVisible(true);
        xyPlot.setRangeCrosshairVisible(true);

        // render clustered series
        final XYItemRenderer renderer = xyPlot.getRenderer();
        Map<Integer, Color> colors = ChartUtils.generateXYColors(clusterPoints.keySet(), colorSpace);
        Iterator<Map.Entry<Integer, Color>> iter = colors.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Color> entry = iter.next();
            renderer.setSeriesPaint(entry.getKey(), entry.getValue());
        }

        // render outlier series
        renderer.setSeriesPaint(outlierClusterId, Color.RED);

        NumberAxis domain = (NumberAxis) xyPlot.getDomainAxis();
        domain.setVerticalTickLabels(true);

        final ChartPanel chartPanel = new ChartPanel(jfreechart);
        this.add(chartPanel, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1, false));

        // display/hide outliers
        ChartUtils.createToggledButtons(panel, outlierXYSeries, outliers, "Display Outliers", "Hide Outliers");
        this.add(panel, BorderLayout.SOUTH);
    }

    public void setOutlierClusterId(int outlierCluterId) {
        this.outlierClusterId = outlierCluterId;
    }

    @Override
    public void setclusterPointFile(File clusterPointFile) {
        this.clusterPointFile = clusterPointFile;
    }

    private static File getClusterPointFile(String[] args, String dir) {
        if (args.length > 0) {
            return new File(args[0]);
        }
        return new File(new File(dir), "DataDenoisingOutput.txt");
    }

    public static void main(String args[]) {

        double width = 0.1;
        double height = 0.1;

        String chartTitle = "Denoising [width=" + width + ", height=" + height + "]";
        String dir = FileUtils.getDbscanDataRootDir().getAbsolutePath();
        File clusterPointFile = getClusterPointFile(args, dir);

        final org.cug.photoncounting.clustering.denoising.DataDenoisingXYChart chart =
                new org.cug.photoncounting.clustering.denoising.DataDenoisingXYChart(chartTitle);
        chart.setclusterPointFile(clusterPointFile);
        chart.setOutlierClusterId(9999);
        ChartUtils.generateXYChart(chart);
    }

}
