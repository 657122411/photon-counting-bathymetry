package org.cug.photoncounting.fit;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.util.Arrays;

public class CurveTest {
    public static double getY(double x) {
        return Math.pow(x, 2) * 5 + x * 6 + 7;
    }

    public static void main(String[] args) {
        WeightedObservedPoints points = new WeightedObservedPoints();
        for (double x = 0; x < 10000; x++) {
            points.add(x, getY(x));
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
        double[] result = fitter.fit(points.toList());
        System.out.println(Arrays.toString(result));
    }
}
