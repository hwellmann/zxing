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
 * group of r concentric rings with alternating colour around a black centre. Given a point in this
 * black centre module, any ray emanating from this point in any direction must intersect the same r
 * connected components.
 * <p>
 * Thus, we look at all black components, sorted by number of pixels, and test the four rays in
 * east, west, south and north directions to find the bull's eye.
 * <p>
 * The fifth ring surrounding this centre (or the third ring, for compact codes) is the outermost
 * white square contained in the finder pattern. Using a quadrilateral finder, we determine the four
 * corners of this connected component and then compute a perspective transform mapping these
 * corners to a perfect square in a resampled matrix where each module has a width of M units.
 * <p>
 * Next, we use the resampled matrix to find the orientation markers next to the corners of the
 * bull's eye, and to decode the mode message located between these markers.
 * <p>
 * For compact codes, we now directly transform the resampled matrix to a normalized one (with just
 * one bit per module) and proceed to decode the bits.
 * <p>
 * For non-compact codes, this is not always sufficient, since projection errors accumulate with the
 * distance from the centre. For codes with additional reference grid lines, we use these lines to
 * adjust the perspective transformation. Drawing a horizontal line through the centre, we expect
 * this line to traverse an alternating sequence of black and white modules. If the intersections of
 * this line do not produce the expected pattern, we slightly vary the slope of this line until we
 * get the expected pattern for at least 16 modules in either direction. After repeating the same
 * process for a vertical line through the centre, we now know the actual positions of four modules
 * on the central reference lines at a distance of 16 modules. We use the positions of these modules
 * to recalculate the perspective transform.
 * <p>
 * This process is repeated for every set of reference lines located at a larger distance (e.g. 32
 * modules form the centre).
 * <p>
 * Finally, we apply the optimized transformation and normalize the matrix.
 * 
 * @author Harald Wellmann
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

    /** Module size in the resampled matrix. */
    public static final int M = 6;

    /** The bit matrix this detector is working on. */
    private BitMatrix matrix;

    /** Finds connected components in the given bit matrix. */
    private ConnectedComponentFinder ccf;

    /** Connected component of the outermost white square ring of the bull's eye. */
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

    /**
     * Finds the bull's eye.
     * 
     * @return true if a bull's eye was found
     */
    public boolean findBullsEye() {
        boolean found = false;
        PriorityQueue<ConnectedComponent> queue = new PriorityQueue<ConnectedComponent>(ccf
            .getComponentMap().values());
        while (!queue.isEmpty() && !found) {
            ConnectedComponent component = queue.poll();
            log.debug("checking component {}", component);
            found = isBlackCentre(component);
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

    /**
     * Checks if the given component satisfies the topological criteria of the black module at the
     * centre of the bull's eye.
     * 
     * @param component
     *            connected component
     * @return true if this component is the centre of the bull's eye
     */
    public boolean isBlackCentre(ConnectedComponent component) {
        if (!component.isBlack()) {
            return false;
        }

        Envelope env = component.getEnvelope();
        
        int centreLabel = component.getLabel();
        int y = (env.minY + env.maxY) / 2;
        int x = (env.minX + env.maxX) / 2;

        List<Integer> east = findRings(x, y, 1, 0);
        List<Integer> west = findRings(x, y, -1, 0);

        int numRings = countCommonRings(east, west);
        if (numRings < 4) {
            return false;
        }
        
        numRings = Math.min(6, numRings);
        if (! checkDistinct(east, numRings)) {
            return false;
        }

        List<Integer> south = findRings(x, y, 0, 1);
        numRings = Math.min(numRings, countCommonRings(east, south));
        if (numRings < 4) {
            return false;
        }

        List<Integer> north = findRings(x, y, 0, -1);
        numRings = Math.min(numRings, countCommonRings(east, north));
        if (numRings < 4) {
            return false;
        }

        compact = (numRings < 6);

        if (log.isDebugEnabled()) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(centreLabel);
            buffer.append(' ');
            for (int i = 0; i < numRings; i++) {
                buffer.append(east.get(i));
                buffer.append(' ');
            }
            log.debug("Found black centre and surrounding rings with labels {}", buffer);
        }

        int offset = compact ? 2 : 4;
        whiteSquareLabel = east.get(offset);

        whiteSquare = ccf.getComponentMap().get(whiteSquareLabel);
        log.debug("outer white square = {}", whiteSquare);

        return true;
    }

    /**
     * @param rings
     * @param numRings
     * @return
     */
    private boolean checkDistinct(List<Integer> rings, int numRings) {
        HashSet<Integer> distinct = new HashSet<Integer>();
        for (int i = 0; i < numRings; i++) {
            distinct.add(rings.get(i));
        }
        return distinct.size() == numRings;
    }

    /**
     * Collects the labels of connected components intersecting the ray from (x0, y0) with direction
     * (dx, dy).
     * 
     * @param x0
     *            x of centre
     * @param y0
     *            y of centre
     * @param dx
     *            x of direction vector
     * @param dy
     *            y of direction vector
     * @return
     */
    private List<Integer> findRings(int x0, int y0, int dx, int dy) {
        List<Integer> rings = new ArrayList<>();
        int currentLabel = ccf.getLabel(x0, y0);
        int label = currentLabel;

        int x = x0 + dx;
        int y = y0 + dy;

        while (env.contains(x, y)) {
            label = ccf.getLabel(x, y);
            if (label != currentLabel) {
                rings.add(label);
                currentLabel = label;
            }
            x += dx;
            y += dy;
        }
        return rings;
    }

    /**
     * Given two lists of labels of potential rings, determines the size of the largest common
     * subsequence, starting from 0.
     * 
     * @param left
     *            left list
     * @param right
     *            right list
     * @return largest j such that left[i] = right[i] for all i < j
     */
    private int countCommonRings(List<Integer> left, List<Integer> right) {
        int c = 0;
        while (c < left.size() && c < right.size()) {
            if (!left.get(c).equals(right.get(c))) {
                break;
            }
            c++;
        }
        return c;
    }

    /**
     * Computes the inverse perspective transform mapping a square matrix to the original matrix.
     * 
     * @return perspective transform
     * @throws NotFoundException
     *             if the bull's eye, the mode message or the reference lines cannot be found
     */
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

    /**
     * Compute the initial perspective transform based on the outermost white square ring of the
     * bull's eye.
     * 
     * @return perspective transform
     */
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

    /**
     * Decodes the mode message to determine the number of layers and the number of data words.
     * 
     * @throws NotFoundException
     *             if the orientation markers cannot be found
     */
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
            int pos = 2 * r - 1;
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
                int sideBits = ((side >> 2) & (0x1f << 5)) + ((side >> 1) & 0x1F);
                log.debug(Integer.toBinaryString(sideBits));
                parameterData += sideBits;
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
     * Corrects the parameter bits using the Reed-Solomon algorithm.
     * 
     * @param parameterData
     *            parameter bits
     * @param compact
     *            true if this is a compact Aztec code
     * @throws NotFoundException
     *             if the array contains too many errors
     */
    private int getCorrectedParameterData(long parameterData, boolean compact)
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
     * Given the four lines of the mode message surrounding the bull's eye, we evaluate the
     * orientation markers to find the index of the line that should be on top.
     * <p>
     * TODO also handle reflection, not just rotation
     * 
     * @param values
     *            array of integers corresponding the the four mode lines in counter-clockwise
     *            direction. The left-most corner of the top line belongs to this line, the
     *            right-most corner belongs to the next line, and so on.
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

    /**
     * Samples values of t such that the module colour changes at t * (dx, dy).
     * 
     * @param dx
     *            x of direction
     * @param dy
     *            y of direction
     * @return parameter values of colour changes
     */
    private List<Integer> sampleChanges(float dx, float dy) {
        List<Integer> changes = new ArrayList<>(64);

        float[] point = new float[2];
        boolean currentBit = true;
        for (int t = 0; t < matrixSize * (M / 2 + 1); t++) {
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
        return changes;
    }

    /**
     * Optimizes the given inverse perspective transform by locating the actual reference grid
     * modules at distance d along the four cardinal directions. The optimized transform maps the
     * centres of these modules to their ideal locations.
     * 
     * @param inverseTransform
     *            inverse transform computed in previous steps
     * @param distance
     *            distance of reference modules
     * @return optimized transform
     * @throws NotFoundException
     */
    public PerspectiveTransform optimizeTransform(PerspectiveTransform inverseTransform,
        int distance) throws NotFoundException {
        this.inverseTransform = inverseTransform;
        Envelope env = new Envelope();
        env.minX = 0;
        env.minY = 0;
        env.maxX = matrix.getWidth() - 1;
        env.maxY = matrix.getHeight() - 1;

        // Coordinates of four points on the reference grid lines, located to the north, east,
        // west and south, forming a diamond.
        float[] news = new float[8];

        // east
        float[] ref = findReferencePoint(1, 0, distance);
        news[2] = ref[0];
        news[3] = ref[1];

        // west
        ref = findReferencePoint(-1, 0, distance);
        news[6] = ref[0];
        news[7] = ref[1];

        // south
        ref = findReferencePoint(0, 1, distance);
        news[4] = ref[0];
        news[5] = ref[1];

        // north
        ref = findReferencePoint(0, -1, distance);
        news[0] = ref[0];
        news[1] = ref[1];

        // transform back to original coordinates
        printPoints(news);
        inverseTransform.transformPoints(news);
        printPoints(news);

        // Now compute a new transform that maps the ideal coordinates in the default orientation
        // to the actual coordinates. The rot() function performs a rotation according to
        // the actual position of the rotation marks.
        int q = distance * M;
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

    /**
     * Normalizes the bit matrix such that each module is a given number of pixels wide and the
     * entire matrix has an outer white border of a given width.
     * 
     * @param cellWidth
     *            width of a module
     * @param borderWidth
     *            width of outer border
     * @return normalized matrix
     */
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

    /**
     * Finds a reference grid module at the given distance (measured as number of modules), in the
     * direction indicated by the vector v = (dx, dy). To compensate for rounding and projection
     * errors, we traverse the line indicated by the vector, taking samples at distances smaller
     * than the module size, and count the number of colour changes.
     * 
     * @param dx
     *            x of direction
     * @param dy
     *            y of direction
     * @param distance
     *            distance of reference point
     * @return transformed coordinates of the center of the found module
     * @throws NotFoundException
     */
    private float[] findReferencePoint(float dx, float dy, int distance)
        throws NotFoundException {
        float[] ref = new float[2];

        log.debug("trying dx = {}, dy = {}", dx, dy);
        List<Integer> changesPos = sampleChanges(dx, dy);
        log.debug("changesPos = {}", changesPos.size());
        log.debug("{}", changesPos);
        if (changesPos.size() < distance + 1) {
            throw NotFoundException.getNotFoundInstance();
        }

        int t1 = changesPos.get(distance - 1);
        int t2 = changesPos.get(distance);

        // t1*v and t2*v are points on two opposite sides of the found module
        // To approximate the centre, we take the intermediate point p0 = (x0, y0).
        float t = (t1 + t2) / 2;

        float x0 = t * dx;
        float y0 = t * dy;

        // Take a vector v1 = (dx1, dy1) orthogonal to v
        float dx1 = -dy;
        float dy1 = dx;

        // Traverse v1 in positive and negative direction from p0 until q = p0 + u*v1 changes
        // colour. Let u1 and u2 be the parameters where the colour change occurs.
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

        // Take the intermediate point
        float u = (u1 + u2) / 2;

        // This approximates the centre of the module in transformed coordinates
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

    private void printPoints(float[] news) {
        for (int i = 0; i < 8; i++) {
            log.debug(String.format("news[%d] = %f", i, news[i]));
        }
    }

    /**
     * Gets the inverse perspective transform.
     * 
     * @return the inverse transform
     */
    public PerspectiveTransform getInverseTransform() {
        return inverseTransform;
    }

    /**
     * Gets the number of layers of this Aztec code.
     * 
     * @return the number of layers
     */
    public int getNumLayers() {
        return numLayers;
    }

    /**
     * Gets the matrix size of this code (number of modules per direction).
     * 
     * @return the matrix size
     */
    public int getMatrixSize() {
        return matrixSize;
    }
}
