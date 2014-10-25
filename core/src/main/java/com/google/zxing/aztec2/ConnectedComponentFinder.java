/*
 * Copyright 2014 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.aztec2;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.common.BitMatrix;

/**
 * Finds connected components in a bit matrix. Each connected component is a maximal set of pixels
 * of the same colour such that any two pixels of the component are connected by a path in the same
 * component. A path is a sequence of adjacent pixels. A pixel is adjacent with its eight matrix
 * neighbours (north-west, north, north-east, east, south-east, south, south-west, west).
 * <p>
 * {@code findConnectedComponents()} must be called before calling {@code getComponentMap()} or
 * {@code getLabel()}.
 *
 * @author hwellmann
 *
 */
public class ConnectedComponentFinder {

    private static Logger log = LoggerFactory.getLogger(ConnectedComponentFinder.class.getSimpleName());

    private int currentLabel = 0;

    private int[] parentMap;
    private int[] labels;

    private BitMatrix matrix;

    private int width;

    private int height;

    private int[] labelCount;

    private Map<Integer, ConnectedComponent> componentMap;
    int[] neighbourLabels = new int[8];

    private int minNeighbour;

    private int neighbourIndex;

    public ConnectedComponentFinder(BitMatrix matrix) {
        this.matrix = matrix;
        this.width = matrix.getWidth();
        this.height = matrix.getHeight();
        this.labels = new int[width * height];
        this.componentMap = new HashMap<>();
        this.parentMap = new int[width * height];
    }

    /**
     * Gets the underlying bit matrix.
     *
     * @return bit matrix
     */
    public BitMatrix getBitMatrix() {
        return matrix;
    }

    /**
     * Gets the component map, mapping component labels to components.
     *
     * @return component map
     */
    public Map<Integer, ConnectedComponent> getComponentMap() {
        assert !componentMap.isEmpty();
        return componentMap;
    }

    /**
     * Finds the connected components in the given bit matrix.
     */
    public void findConnectedComponents() {

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean bit = matrix.get(x, y);
                label(x, y, bit);
            }
        }
        log.info("pass 2");
        labelCount = new int[currentLabel + 1];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int label = updateLabel(x, y);
                expandComponent(x, y, label);
            }
        }
    }

    /**
     * Expands the envelope of the component with the given label by including the given point and
     * increments the pixel count.
     *
     * @param x
     * @param y
     * @param label
     */
    private void expandComponent(int x, int y, int label) {
        ConnectedComponent component = componentMap.get(label);
        Envelope envelope;
        if (component == null) {
            envelope = new Envelope();
            component = new ConnectedComponent(label, 0, envelope, matrix.get(x, y));
            componentMap.put(label, component);
        }
        else {
            envelope = component.getEnvelope();
        }
        envelope.expand(x, y);
        component.setNumPixels(labelCount[label]);
    }

    /**
     * Labels the given pixel. If all neighbours are unlabelled, a new label is assigned to the
     * given pixel. Otherwise, the pixel is labelled with the smallest neighbour label, and this
     * label is marked as parent of all other neighbour labels.
     *
     * @param x
     *            row index
     * @param y
     *            column index
     * @param bit
     *            pixel colour
     */
    private void label(int x, int y, boolean bit) {
        neighbourIndex = 0;
        minNeighbour = Integer.MAX_VALUE;
        checkNeighbour(x - 1, y, bit);
        checkNeighbour(x + 1, y, bit);
        checkNeighbour(x, y - 1, bit);
        checkNeighbour(x, y + 1, bit);

//        checkNeighbour(x - 1, y - 1, bit);
//        checkNeighbour(x + 1, y - 1, bit);
//        checkNeighbour(x - 1, y + 1, bit);
//        checkNeighbour(x + 1, y + 1, bit);

        if (minNeighbour == Integer.MAX_VALUE) {
            currentLabel++;
            setLabel(x, y, currentLabel);
        }
        else {
            setLabel(x, y, minNeighbour);
            for (int i = 0; i < neighbourIndex; i++) {
                int label = neighbourLabels[i];
                if (label != minNeighbour) {
                    parentMap[label] = minNeighbour;
                }
            }
        }
    }

    private void setLabel(int i, int j, int label) {
        labels[j * width + i] = label;
    }

    /**
     * Checks a neighbour of a given pixel. If the neighbour and the given pixel have the same
     * colour, the neighbour's label (if present) is added to the set of neighbour labels.
     *
     * @param neighbourLabels
     *            set of neighbour labels found so far
     * @param i
     *            neighbour row index
     * @param j
     *            neighbour column index
     * @param bit
     *            colour of given pixel
     */
    private Integer checkNeighbour(int i, int j, boolean bit) {
        if (i < 0 || j < 0 || i >= width || j >= height) {
            return null;
        }

        Integer label = getLabel(i, j, bit);
        if (label != null) {
            neighbourLabels[neighbourIndex++]= label;
            if (label < minNeighbour) {
                minNeighbour = label;
            }
        }
        return label;
    }

    /**
     * Returns the label of the given pixel, if its colour matches the given one.
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @param bit
     *            expected colour
     * @return pixel label or null, if pixel is unlabelled or has the opposite colour
     */
    private Integer getLabel(int i, int j, boolean bit) {
        if (matrix.get(i, j) != bit) {
            return null;
        }
        int label= labels[j * width + i];
        if (label == 0) {
            return null;
        }
        else return label;
    }

    /**
     * Gets the label of pixel (i, j).
     *
     * @param i
     *            row index
     * @param j
     *            column index
     * @return label of connected component
     */
    public Integer getLabel(int i, int j) {
        //assert !componentMap.isEmpty();
        return labels[j * width + i];
    }

    /**
     * Updates the label of the given pixel, traversing the parent hierarchy up to the root,
     * replaces the label, if necessary. Also increments the pixel count for the final label.
     * @param x row index
     * @param y column index
     * @return final label of the given pixel
     */
    private int updateLabel(int x, int y) {
        Integer label = getLabel(x, y);
        int parent = parentMap[label];
        if (parent == 0) {
            labelCount[label]++;
            return label;
        }
        while (parent != 0) {
            label = parent;
            parent = parentMap[label];
        }
        labelCount[label]++;
        setLabel(x, y, label);
        return label;
    }

}
