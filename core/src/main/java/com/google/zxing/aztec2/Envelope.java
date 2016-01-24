/*
 * Copyright 2014 ZXing authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.zxing.aztec2;

/**
 * Envelope or bounding rectangle of a given subset of a bit matrix.
 * 
 * @author hwellmann
 * 
 */
public class Envelope {

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = 0;
    int maxY = 0;

    /**
     * Creates an empty envelope. {@link #contains(int, int)} returns {@code false} for any pair of
     * coordinates.
     */
    public Envelope() {
    }

    /**
     * Creates an envelope for the given quadilateral. The envelope is always a rectangle, unlike
     * the quadrilateral.
     *
     * @param q
     *            quadrilateral
     */
    public Envelope(Quadrilateral q) {
        expand(q.nwx, q.nwy);
        expand(q.nex, q.ney);
        expand(q.swx, q.swy);
        expand(q.sex, q.sey);
    }

    /**
     * Creates an envelope spanned by the given north-west and south-east corners.
     *
     * @param minX
     *            north-west x coordinate
     * @param minY
     *            north-west y coordinate
     * @param maxX
     *            south-east x coordinate
     * @param maxY
     *            south-east y coordinate
     */
    public Envelope(int minX, int minY, int maxX, int maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    /**
     * Expand the envelope to include the given pixel.
     *
     * @param x
     *            column index
     * @param y
     *            row index
     */
    public void expand(int x, int y) {
        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
        if (x > maxX) {
            maxX = x;
        }
        if (y > maxY) {
            maxY = y;
        }
    }

    /**
     * Checks if the given pixel is contained in the envelope.
     *
     * @param x
     *            column index
     * @param y
     *            row index
     * @return true if the pixel is contained in the envelope
     */
    public boolean contains(int x, int y) {
        return minX <= x && x <= maxX && minY <= y && y <= maxY;
    }

    @Override
    public String toString() {
        return String.format("[(%d %d), (%d %d)]", minX, minY, maxX, maxY);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getWidth() {
        return maxX - minX + 1;
    }

    public int getHeight() {
        return maxY - minY + 1;
    }
}
