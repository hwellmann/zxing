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
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.aztec2.ConnectedComponentFinder;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.ImageReader;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

public class ConnectedComponentFinderTest {

    private static Logger log = LoggerFactory.getLogger(AztecDetectorTest.class);

    private ConnectedComponentFinder ccf;

    @Test
    public void findComponents() throws IOException {
        TextBitMatrixReader matrixReader = new TextBitMatrixReader();
        BitMatrix matrix = matrixReader.read("src/test/resources/aztec/bullsEye.txt");

        ConnectedComponentFinder ccf = new ConnectedComponentFinder(matrix, false);
        ccf.findConnectedComponents();

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                int label = ccf.getLabel(x, y);
                System.out.print(label);
            }
            System.out.println();
        }
    }

    @Test
    public void findAztecComponents() throws IOException, NotFoundException, FormatException {
        File file = new File("../core/src/test/resources/blackbox/aztec-2/03.png");
        BufferedImage image = ImageReader.readImage(file.toURI());
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BitMatrix matrix = binarizer.getBlackMatrix();

        MatrixToImageWriter.writeToPath(matrix, "PNG", Paths.get("target/bits.png"));

        ccf = new ConnectedComponentFinder(matrix, false);
        log.debug("start");
        ccf.findConnectedComponents();
        log.debug("done");

        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                int label = ccf.getLabel(x, y);
                System.out.print(String.format("%02d", label));
            }
            System.out.println();
        }
    }
}
