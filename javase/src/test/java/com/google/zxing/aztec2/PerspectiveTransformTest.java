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

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;

import org.junit.Test;

import com.google.zxing.aztec2.Envelope;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.ImageReader;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.PerspectiveTransform;

/**
 * @author hwellmann
 * 
 */
public class PerspectiveTransformTest {

    @Test
    public void transform() {
        PerspectiveTransform transform = PerspectiveTransform.quadrilateralToQuadrilateral(155,
            137, 258, 139, 136, 228, 247, 231,
            158, 158, 202, 158, 158, 202, 202, 202);
        // 200, 200, 240, 200, 200, 240, 240, 240);

        float[] points = new float[] {
            155, 137, 258, 139, 136, 228, 247, 231,
            110, 92, 320, 92, 63, 278, 307, 290,
        };
        transform.transformPoints(points);
        for (float p : points) {
            System.out.println(p + " ");
        }
        System.out.println();
    }

    @Test
    public void transformBitMatrix() throws Exception {
        File file = new File("../core/src/test/resources/blackbox/aztec-2/03.png");
        BufferedImage image = ImageReader.readImage(file.toURI());
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BitMatrix matrix = binarizer.getBlackMatrix();
        Envelope env = new Envelope();
        env.minX = 0;
        env.minY = 0;
        env.maxX = matrix.getWidth() - 1;
        env.maxY = matrix.getHeight() - 1;

        int dim = 360;
        BitMatrix transformedMatrix = new BitMatrix(dim);

        PerspectiveTransform inverseTransform =
            PerspectiveTransform.quadrilateralToQuadrilateral(
                158, 158, 202, 158, 158, 202, 202, 202,
                155, 137, 258, 139, 136, 228, 247, 231);

        float[] point = new float[2];

        float y = 0.5f;
        for (int j = 0; j < dim; j++) {
            float x = 0.5f;
            for (int i = 0; i < dim; i++) {
                point[0] = x;
                point[1] = y;
                inverseTransform.transformPoints(point);

                int tx = Math.round(point[0]);
                int ty = Math.round(point[1]);

                // System.out.println(String.format("%f %f -> %d %d", x, y, tx, ty));

                if (env.contains(tx, ty) && matrix.get(tx, ty)) {
                    transformedMatrix.set(i, j);
                }
                x += 1;
            }
            y += 1;
        }
        MatrixToImageWriter.writeToPath(transformedMatrix, "PNG",
            Paths.get("target/transformed.png"));

    }

}
