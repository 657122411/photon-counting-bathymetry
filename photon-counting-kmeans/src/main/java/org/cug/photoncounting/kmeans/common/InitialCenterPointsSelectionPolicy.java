package org.cug.photoncounting.kmeans.common;

import org.cug.photoncounting.common.CenterPoint;
import org.cug.photoncounting.common.Point2D;

import java.util.List;
import java.util.TreeSet;

public interface InitialCenterPointsSelectionPolicy {

    TreeSet<CenterPoint> select(int k, List<Point2D> points);
}
