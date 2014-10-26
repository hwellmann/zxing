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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.aztec.AztecDetectorResult;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.PerspectiveTransform;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

/**
 * Detects an Aztec Code in a bit matrix, based on the connected components of this bit matrix.
 * <p>
 * First of all, the bull's eye is detected by its topological properties. We are looking for a
 * group of concentric rings with alternating colour around a black centre. Any line intersecting the black component in
 * the centre of the bull's eye
 * 
 * @author hwellmann
 * 
 */
public class AztecDetector {

    private static Logger log = LoggerFactory.getLogger(AztecDetector.class);

    private static final int ROT[][] = {
        { 0, 1, 3, 2 },
        { 1, 2, 0, 3 },
        { 2, 3, 1, 0 },
        { 3, 0, 2, 1 }
    };
    
    /** Module size in the normalized matrix. */
    private static final int M = 4;

    /** The bit matrix this detector is working on. */
    private BitMatrix matrix;

    /** Finds connected components in the given bit matrix. */
    private ConnectedComponentFinder ccf;

    /** Connected component of the bull's eye outermost white square. */
    private ConnectedComponent whiteSquare;

    /** Label of whiteSquare. */
    private int whiteSquareLabel;

    /** Inverse perspective transform, mapping square matrix to original matrix. */
    private PerspectiveTransform inverseTransform;

    /** Number of Aztec code layers. */
    private int numLayers;
    
    /** Number of data words. */
    private int numDataWords;

    /** Actual code matrix width (number of modules). */
    private int matrixSize;

    /** Number of <em>additional</em> reference grid lines, counting from the centre. */
    private int numReferenceLines;

    private int topLineIndex;

    private Envelope env;

    private float[] outerCorners = new float[4 * 2];
    
    private boolean compact;

    private Quadrilateral q;

    public AztecDetector(ConnectedComponentFinder ccf) {
        this.ccf = ccf;
        this.matrix = ccf.getBitMatrix();
        env = new Envelope();
        env.minX = 0;
        env.minY = 0;
        env.maxX = matrix.getWidth() - 1;
        env.maxY = matrix.getHeight() - 1;
    }

    public boolean detect() {
        boolean found = false;
        PriorityQueue<ConnectedComponent> queue = new PriorityQueue<ConnectedComponent>(ccf
            .getComponentMap().values());
        while (!queue.isEmpty() && !found) {
            ConnectedComponent component = queue.poll();
            log.debug("checking component {}", component);
            found = isBlackCentre(matrix, component);
        }
        return found;
    }

    public AztecDetectorResult getDetectorResult() {
        BitMatrix bits = normalizeMatrix(1, 0);
        ResultPoint[] points = new ResultPoint[4];
        for (int i = 0; i < 4; i++) {
            points[i] = new ResultPoint(outerCorners[2 * i], outerCorners[2 * i + 1]);
        }
        return new AztecDetectorResult(bits, points, compact, numDataWords, numLayers);
    }

    public boolean isBlackCentre(BitMatrix matrix, ConnectedComponent component) {
        if (!component.isBlack()) {
            return false;
        }
        List<Integer> labels = new ArrayList<>();
        Envelope env = component.getEnvelope();
        int centreLabel = component.getLabel();
        int y = (env.minY + env.maxY) / 2;
        int currentLabel = 0;
        int centreIndex = -1;
        for (int x = 0; x < matrix.getWidth(); x++) {
            int label = ccf.getLabel(x, y);
            if (label != currentLabel) {
                if (label == centreLabel) {
                    centreIndex = labels.size();
                }
                labels.add(label);
                currentLabel = label;
            }
        }

        Set<Integer> rings = new HashSet<Integer>();
        for (int i = 1; i <= 6; i++) {
            if (centreIndex - i < 0) {
                break;
            }
            if (centreIndex + i >= labels.size()) {
                break;
            }
            if (!labels.get(centreIndex - i).equals(labels.get(centreIndex + i))) {
                break;
            }
            rings.add(labels.get(centreIndex + i));
        }
        int numRings = rings.size();
        if (numRings < 4) {
            return false;
        }
        compact = (numRings < 6);

        if (log.isDebugEnabled()) {
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i <= numRings; i++) {
                buffer.append(labels.get(centreIndex + i));
                buffer.append(' ');
            }
            log.debug("Found black centre and surrounding rings with labels {}", labels);
        }

        int offset = compact ? 3 : 5;
        whiteSquareLabel = labels.get(centreIndex + offset);

        whiteSquare = ccf.getComponentMap().get(whiteSquareLabel);
        log.debug("outer white square = {}", whiteSquare);

        return true;
    }

    public PerspectiveTransform computeTransform() throws NotFoundException {
        findCorners();
        computeInitialTransform();
        decodeModeMessage();
        for (int i = 1; i <= numReferenceLines; i++) {
            inverseTransform = optimizeTransform(inverseTransform, 16 * i);
        }
        float q = 0.5f * M * matrixSize;
        outerCorners = new float[] { -q, -q, q, -q, q, q, -q, q };
        inverseTransform.transformPoints(outerCorners);
        return inverseTransform;
    }

    /**
     * Finds the corners of the outermost white square of the bull's eye.
     * 
     * @throws NotFoundException
     */
    public void findCorners() throws NotFoundException {
        if (whiteSquare == null) {
            throw NotFoundException.getNotFoundInstance();
        }
        QuadrilateralFinder finder = new QuadrilateralFinder(ccf);
        q = finder.findQuadrilateral(whiteSquareLabel);
    }

    public PerspectiveTransform computeInitialTransform() {

        int d = 0;
        int s;
        if (compact) {
            s = 7 * M / 2;
        }
        else {
            s = 11 * M / 2;
        }

        inverseTransform =
            PerspectiveTransform.quadrilateralToQuadrilateral(
                -s + d, -s + d, s + d, -s + d, -s + d, s + d, s + d, s + d,
                q.nwx, q.nwy, q.nex, q.ney, q.swx, q.swy, q.sex, q.sey);
        return inverseTransform;

    }

    public void decodeModeMessage() throws NotFoundException {
        int r = compact ? 5 : 7;
        int q = r * M;
        float[] c = new float[] { -q, -q, q, -q, q, q, -q, q };
        float[] d = new float[] { M, 0, 0, M, -M, 0, 0, -M };
        float[] line = new float[2 * 2 * r];
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            float x = c[2 * i];
            float y = c[2 * i + 1];
            float dx = d[2 * i];
            float dy = d[2 * i + 1];
            for (int j = 0; j < 2 * r; j++) {
                line[2 * j] = x;
                line[2 * j + 1] = y;
                x += dx;
                y += dy;
            }
            inverseTransform.transformPoints(line);
            int value = 0;
            int pos = 2 * r -1;
            for (int j = 0; j < 2 * r; j++, pos--) {
                int tx = Math.round(line[2 * j]);
                int ty = Math.round(line[2 * j + 1]);
                boolean bit = getBitSafely(tx, ty);
                if (bit) {
                    value |= (1 << pos);
                }
            }
            values[i] = value;
        }

        topLineIndex = findTopLine(values);
        log.debug("topLineIndex = {}", topLineIndex);
        
        long parameterData = 0;
        for (int i = 0; i < 4; i++) {
            int side = values[(topLineIndex + i) % 4];
            if (compact) {
                // Each side of the form ..XXXXXXX. where Xs are parameter data
                parameterData <<= 7;
                parameterData += (side >> 1) & 0x7F;
            }
            else {
                // Each side of the form ..XXXXX.XXXXX. where Xs are parameter data
                parameterData <<= 10;
                parameterData += ((side >> 2) & (0x1f << 5)) + ((side >> 1) & 0x1F);
            }
        }
        
        int correctedData = getCorrectedParameterData(parameterData, compact);
        if (compact) {
            // 8 bits: 2 bits layers and 6 bits data blocks
            numLayers = (correctedData >> 6) + 1;
            numDataWords = (correctedData & 0x3F) + 1;
            matrixSize = 11 + numLayers * 4;
            numReferenceLines = 0;
        }
        else {
            // 16 bits: 5 bits layers and 11 bits data blocks
            numLayers = (correctedData >> 11) + 1;
            numDataWords = (correctedData & 0x7FF) + 1;
            /* Net code matrix width (number of modules), not counting reference grid lines. */
            int baseMatrixSize = 14 + numLayers * 4;

            numReferenceLines = (baseMatrixSize / 2 - 1) / 15;
            matrixSize = baseMatrixSize + 1 + 2 * numReferenceLines;
        }
        
        log.debug("numLayers = {}, numDataBlocks = {}", numLayers, numDataWords);

    }

    /**
     * Corrects the parameter bits using Reed-Solomon algorithm.
     * 
     * @param parameterData
     *            parameter bits
     * @param compact
     *            true if this is a compact Aztec code
     * @throws NotFoundException
     *             if the array contains too many errors
     */
    private static int getCorrectedParameterData(long parameterData, boolean compact)
        throws NotFoundException {
        int numCodewords;
        int numDataCodewords;

        if (compact) {
            numCodewords = 7;
            numDataCodewords = 2;
        }
        else {
            numCodewords = 10;
            numDataCodewords = 4;
        }

        int numECCodewords = numCodewords - numDataCodewords;
        int[] parameterWords = new int[numCodewords];
        for (int i = numCodewords - 1; i >= 0; --i) {
            parameterWords[i] = (int) parameterData & 0xF;
            parameterData >>= 4;
        }
        try {
            ReedSolomonDecoder rsDecoder = new ReedSolomonDecoder(GenericGF.AZTEC_PARAM);
            rsDecoder.decode(parameterWords, numECCodewords);
        }
        catch (ReedSolomonException ignored) {
            throw NotFoundException.getNotFoundInstance();
        }
        // Toss the error correction. Just return the data as an integer
        int result = 0;
        for (int i = 0; i < numDataCodewords; i++) {
            result = (result << 4) + parameterWords[i];
        }
        return result;
    }    
    
    
    private boolean getBitSafely(int x, int y) throws NotFoundException {
        try {
            return matrix.get(x, y);
        }
        catch (ArrayIndexOutOfBoundsException exc) {
            throw NotFoundException.getNotFoundInstance();
        }
    }

    /**
     * @param values
     * @return
     * @throws NotFoundException
     */
    private int findTopLine(int[] lineValues) throws NotFoundException {
        int index = 0;
        int bits;
        for (int lineValue : lineValues) {
            if (compact) {
                bits = (lineValue & (3 << 8)) >> 7 | (lineValue & 1);                
            }
            else {
                bits = (lineValue & (3 << 12)) >> 11 | (lineValue & 1);
            }
            if (bits == 7) {
                return (index + 3) % 4;
            }
            index++;
        }
        throw NotFoundException.getNotFoundInstance();
    }

    public void sampleChanges(List<Integer> changes, float dx, float dy) {
        changes.clear();

        float[] point = new float[2];
        boolean currentBit = true;
        for (int t = 0; t < matrixSize * (M/2+1); t++) {
            point[0] = t * dx;
            point[1] = t * dy;
            inverseTransform.transformPoints(point);
            int tx = Math.round(point[0]);
            int ty = Math.round(point[1]);

            if (env.contains(tx, ty)) {
                boolean bit = matrix.get(tx, ty);
                if (bit != currentBit) {
                    log.trace(String.format("%d -> %d %d", t, tx, ty));
                    currentBit = bit;
                    changes.add(t);
                }
            }
        }
    }

    public PerspectiveTransform optimizeTransform(PerspectiveTransform inverseTransform,
        int expectedChanges) throws NotFoundException {
        this.inverseTransform = inverseTransform;
        Envelope env = new Envelope();
        env.minX = 0;
        env.minY = 0;
        env.maxX = matrix.getWidth() - 1;
        env.maxY = matrix.getHeight() - 1;

        float[] news = new float[8];

        // east
        float[] ref = findReferencePoint(1, 0, expectedChanges);
        news[2] = ref[0];
        news[3] = ref[1];

        // west
        ref = findReferencePoint(-1, 0, expectedChanges);
        news[6] = ref[0];
        news[7] = ref[1];

        // south
        ref = findReferencePoint(0, 1, expectedChanges);
        news[4] = ref[0];
        news[5] = ref[1];

        // north
        ref = findReferencePoint(0, -1, expectedChanges);
        news[0] = ref[0];
        news[1] = ref[1];

        printPoints(news);
        inverseTransform.transformPoints(news);
        printPoints(news);

        int q = expectedChanges * M;
        int d = 0;
        PerspectiveTransform optimizedTransform = PerspectiveTransform
            .quadrilateralToQuadrilateral(
                0 + d, -q + d, q + d, 0 + d, -q + d, 0 + d, 0 + d, q + d,
                news[rot(0)], news[rot(1)], news[rot(2)], news[rot(3)], news[rot(4)], news[rot(5)],
                news[rot(6)], news[rot(7)]);

        this.inverseTransform = optimizedTransform;
        this.topLineIndex = 0;
        return optimizedTransform;
    }

    public BitMatrix normalizeMatrix(int cellWidth, int borderWidth) {
        int width = matrixSize * cellWidth + 2 * borderWidth;
        int x0 = borderWidth;
        int y0 = borderWidth;
        BitMatrix normalized = new BitMatrix(width);
        int m = matrixSize / 2;
        int y = y0;
        for (int j = -m; j <= m; j++, y += cellWidth) {
            int x = x0;
            for (int i = -m; i <= m; i++, x += cellWidth) {
                float[] p = new float[2];
                p[0] = M * i;
                p[1] = M * j;
                inverseTransform.transformPoints(p);
                int tx = Math.round(p[0]);
                int ty = Math.round(p[1]);
                if (env.contains(tx, ty) && matrix.get(tx, ty)) {
                    for (int dx = 0; dx < cellWidth; dx++) {
                        for (int dy = 0; dy < cellWidth; dy++) {
                            normalized.set(x + dx, y + dy);
                        }
                    }
                }
            }
        }
        return normalized;
    }

    private float[] findReferencePoint(float dxPos, float dyPos, int expectedChanges)
        throws NotFoundException {
        float[] ref = new float[2];

        List<Integer> changesPos = new ArrayList<>();

        log.debug("trying dx = {}, dy = {}", dxPos, dyPos);
        sampleChanges(changesPos, dxPos, dyPos);
        log.debug("changesPos = {}", changesPos.size());
        log.debug("{}", changesPos);

        sampleChanges(changesPos, dxPos, dyPos);
        log.debug("changesPos = {}", changesPos.size());
        log.debug("{}", changesPos);
        if (changesPos.size() < expectedChanges + 1) {
            throw NotFoundException.getNotFoundInstance();
        }

        int t1 = changesPos.get(expectedChanges - 1);
        int t2 = changesPos.get(expectedChanges);
        float t = (t1 + t2) / 2;

        float x0 = t * dxPos;
        float y0 = t * dyPos;

        float dx1 = -dyPos;
        float dy1 = dxPos;

        float u1 = 0;
        float u2 = 0;
        float[] point = new float[2];
        for (int s = 1;; s++) {
            point[0] = x0 + s * dx1;
            point[1] = y0 + s * dy1;
            inverseTransform.transformPoints(point);
            int tx = Math.round(point[0]);
            int ty = Math.round(point[1]);

            boolean bit = matrix.get(tx, ty);
            if (!bit) {
                u1 = s;
                break;
            }
        }

        for (int s = -1;; s--) {
            point[0] = x0 + s * dx1;
            point[1] = y0 + s * dy1;
            inverseTransform.transformPoints(point);
            int tx = Math.round(point[0]);
            int ty = Math.round(point[1]);

            boolean bit = matrix.get(tx, ty);
            if (!bit) {
                u2 = s;
                break;
            }
        }

        float u = (u1 + u2) / 2;

        ref[0] = x0 + u * dx1;
        ref[1] = y0 + u * dy1;

        log.debug(String.format("%f %f -> %f %f", x0, y0, ref[0], ref[1]));
        return ref;
    }

    /**
     * @param i
     * @return
     */
    private int rot(int i) {
        int j = i / 2;
        int k = i % 2;
        return 2 * ROT[topLineIndex][j] + k;
    }

    /**
     * @param news
     */
    private void printPoints(float[] news) {
        for (int i = 0; i < 8; i++) {
            log.debug(String.format("news[%d] = %f", i, news[i]));
        }
    }

    /**
     * Gets the inverseTransform.
     * 
     * @return the inverseTransform
     */
    public PerspectiveTransform getInverseTransform() {
        return inverseTransform;
    }

    /**
     * Gets the numLayers.
     * 
     * @return the numLayers
     */
    public int getNumLayers() {
        return numLayers;
    }

    /**
     * Gets the matrixSize.
     * 
     * @return the matrixSize
     */
    public int getMatrixSize() {
        return matrixSize;
    }
}
