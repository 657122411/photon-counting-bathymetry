package org.cug.photoncounting.clustering.kmeans.common;

import org.cug.photoncounting.clustering.common.CenterPoint;
import org.cug.photoncounting.clustering.common.Point2D;

import java.util.List;
import java.util.TreeSet;

public interface InitialCenterPointsSelectionPolicy {

    TreeSet<CenterPoint> select(int k, List<Point2D> points);
}
