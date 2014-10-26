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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.aztec.AztecReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.ImageReader;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

/**
 * @author hwellmann
 * 
 */
@RunWith(Parameterized.class)
public class ParameterizedAztecDecoderTest {

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "01.png" },
            { "02.png" },
            { "03.png" },
            { "04.png" },
            { "05.png" },

            // too distorted
            // { "06.png" },
            // { "07.png" },

            // 45 degrees
            // { "08.png" },

            // compact codes
            { "09.png" },
            // { "10.png" },
            // { "11.png" },
            { "12.png" },
            // { "13.png" },
            // { "14.png" },
            // { "15.png" },

            { "16.png" },
            { "17.png" },
            { "18.png" },

            // too blurred
            // { "19.png" },

            { "20.png" },
            { "21.png" },
            { "22.png" },
        });
    }

    private String sourceFileName;
    private String textFileName;
    private String testDirName = "../core/src/test/resources/blackbox/aztec-2";

    /**
     * 
     */
    public ParameterizedAztecDecoderTest(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        this.textFileName = sourceFileName.replace(".png", ".txt");
    }

    // @Test
    public void decode() throws Exception {
        File sourceFile = new File(testDirName, sourceFileName);

        BufferedImage image = ImageReader.readImage(sourceFile.toURI());
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BitMatrix matrix = binarizer.getBlackMatrix();

        ConnectedComponentFinder ccf = new ConnectedComponentFinder(matrix, false);
        ccf.findConnectedComponents();

        AztecDetector detector = new AztecDetector(ccf);

        assertThat(detector.detect(), is(true));

        detector.computeTransform();
        BitMatrix nm = detector.normalizeMatrix(2, 4);

        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(nm);
        LuminanceSource normalizedSource = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap normalizedBitmap = new BinaryBitmap(new HybridBinarizer(normalizedSource));

        AztecReader aztecReader = new AztecReader();
        Result result = aztecReader.decode(normalizedBitmap);
        String actualText = result.getText();

        File textFile = new File(testDirName, textFileName);
        byte[] expectedBytes = Files.readAllBytes(textFile.toPath());
        String expectedText = new String(expectedBytes, StandardCharsets.ISO_8859_1);
        assertThat(actualText, is(expectedText));
    }

    @Test
    public void read() throws Exception {
        File sourceFile = new File(testDirName, sourceFileName);

        BufferedImage image = ImageReader.readImage(sourceFile.toURI());
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
        EnhancedAztecReader aztecReader = new EnhancedAztecReader();
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, new ResultPointCallback() {
            
            @Override
            public void foundPossibleResultPoint(ResultPoint point) {
                // TODO Auto-generated method stub
                
            }
        });
        Result result = aztecReader.decode(binaryBitmap, hints);
        String actualText = result.getText();

        File textFile = new File(testDirName, textFileName);
        byte[] expectedBytes = Files.readAllBytes(textFile.toPath());
        String expectedText = new String(expectedBytes, StandardCharsets.ISO_8859_1);
        assertThat(actualText, is(expectedText));
    }

}
