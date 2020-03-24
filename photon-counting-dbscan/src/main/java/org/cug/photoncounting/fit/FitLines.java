package org.cug.photoncounting.fit;

import com.google.common.collect.Lists;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.cug.photoncounting.common.Point2D;
import org.cug.photoncounting.common.utils.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class FitLines {
    private final List<Point2D> allPoints = Lists.newArrayList();

    public static void main(String[] args) {
        FitLines f = new FitLines();
        f.getAllPoints(new File(FileUtils.getDbscanDataRootDir(), "bottom.txt"));
        f.testLeastSquareMethodFromApache();
    }

    private void getAllPoints(File... files) {
        FileUtils.read2DPointsFromFiles(allPoints, "[\t,;\\s]+", files);
    }

    private void testLeastSquareMethodFromApache() {
        final WeightedObservedPoints obs = new WeightedObservedPoints();
        for (Point2D point : allPoints) {
            obs.add(point.getX(), point.getY());
        }

        // Instantiate a third-degree polynomial fitter.
        final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);

        // Retrieve fitted parameters (coefficients of the polynomial function).
        final double[] coeff = fitter.fit(obs.toList());
//        for (double c : coeff) {
//            System.out.println(c);
//        }

        System.out.println(Arrays.toString(coeff));
    }


}
