package org.cug.photoncounting.common;

/**
 * 点 x + y
 */
public class Point2D implements Cloneable {

    protected final Double x;
    protected final Double y;

    public Point2D(Double x, Double y) {
        super();
        this.x = x;
        this.y = y;
    }

    @Override
    public int hashCode() {
        return 31 * x.hashCode() + 31 * y.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        Point2D other = (Point2D) obj;
        return this.x.doubleValue() == other.x.doubleValue() && this.y.doubleValue() == other.y.doubleValue();
    }

    public Double getX() {
        return x;
    }

    public Double getY() {
        return y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    @Override
    public Object clone() {
        Point2D o = null;
        try {
            o = (Point2D) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return o;
    }

}
