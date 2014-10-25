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
 * Represents a connected component of a given colour in a black-and-white matrix. Each connected
 * component has a unique label.
 * 
 * @author hwellmann
 * 
 */
public class ConnectedComponent implements Comparable<ConnectedComponent> {

    private int label;

    private int numPixels;

    private Envelope envelope;

    private boolean black;

    public ConnectedComponent(int label, int numPixels, Envelope envelope, boolean black) {
        this.label = label;
        this.numPixels = numPixels;
        this.envelope = envelope;
        this.black = black;
    }

    /**
     * Gets the label of this component.
     * 
     * @return
     */
    public int getLabel() {
        return label;
    }

    public void setLabel(int label) {
        this.label = label;
    }

    /**
     * Gets the number of pixels contained in this component.
     * 
     * @return
     */
    public int getNumPixels() {
        return numPixels;
    }

    public void setNumPixels(int numPixels) {
        this.numPixels = numPixels;
    }

    /**
     * Gets the envelope of this component.
     * 
     * @return
     */
    public Envelope getEnvelope() {
        return envelope;
    }

    public void setEnvelope(Envelope envelope) {
        this.envelope = envelope;
    }

    /**
     * Returns true if this component is black.
     * 
     * @return
     */
    public boolean isBlack() {
        return black;
    }

    public void setBlack(boolean black) {
        this.black = black;
    }

    @Override
    public String toString() {
        String colour = black ? "B" : "W";
        return String.format("%d -> %s %d %s", label, colour, numPixels, envelope);
    }

    /**
     * Compares connected components by number of pixels.
     */
    @Override
    public int compareTo(ConnectedComponent o) {
        return numPixels - o.numPixels;
    }
}
