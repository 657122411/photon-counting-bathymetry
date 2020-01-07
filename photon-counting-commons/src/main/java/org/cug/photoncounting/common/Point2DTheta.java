package org.cug.photoncounting.common;

/**
 * 点 + theta + 加权和 + 信号点/噪点flag
 */
public class Point2DTheta extends Point2D {

    private final Point2D point;
    private int theta;
    private double wP;
    private int flag = 0;

    public Point2D getPoint() {
        return point;
    }

    public int getTheta() {
        return theta;
    }

    public void setTheta(int theta) {
        this.theta = theta;
    }

    public double getwP() {
        return wP;
    }

    public void setwP(double wP) {
        this.wP = wP;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public Point2DTheta(Point2D point, int theta, double wP) {
        super(point.getX(), point.getY());
        this.point = point;
        this.theta = theta;
        this.wP = wP;
    }

    @Override
    public String toString() {
        return point.getX() + "," + point.getY() + "," + flag;
    }

}
