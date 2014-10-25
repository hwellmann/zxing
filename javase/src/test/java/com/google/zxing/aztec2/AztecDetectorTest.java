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

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.aztec.AztecReader;
import com.google.zxing.aztec2.AztecDetector;
import com.google.zxing.aztec2.ConnectedComponentFinder;
import com.google.zxing.aztec2.EnhancedAztecReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.ImageReader;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

/**
 * @author hwellmann
 * 
 */
public class AztecDetectorTest {

    private static Logger log = LoggerFactory.getLogger(AztecDetectorTest.class);

    @Test
    public void decodeTransformedImage() throws Exception {
        File file = new File("src/test/resources/aztec/21tr.png");
        BufferedImage image = ImageReader.readImage(file.toURI());
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BitMatrix matrix = binarizer.getBlackMatrix();

        ConnectedComponentFinder ccf = new ConnectedComponentFinder(matrix, false);
        ccf.findConnectedComponents();

        AztecDetector detector = new AztecDetector(ccf);

        boolean found = detector.detect();
        if (!found) {
            return;
        }
        detector.computeTransform();
        BitMatrix nm = detector.normalizeMatrix(2, 4);
        MatrixToImageWriter.writeToPath(nm, "PNG", new File("target/nm.png").toPath());

        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(nm);
        LuminanceSource normalizedSource = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap normalizedBitmap = new BinaryBitmap(new HybridBinarizer(normalizedSource));

        AztecReader aztecReader = new AztecReader();
        Result result = aztecReader.decode(normalizedBitmap);
        String text = result.getText();
        log.info(text);
    }

    @Test
    public void readAbc() throws Exception {
        File file = new File("../core/src/test/resources/blackbox/aztec-1/abc-37x37.png");

        BufferedImage image = ImageReader.readImage(file.toURI());
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
        EnhancedAztecReader aztecReader = new EnhancedAztecReader();
        Result result = aztecReader.decode(binaryBitmap);
        String actualText = result.getText();
        System.out.println(actualText);
    }
}
