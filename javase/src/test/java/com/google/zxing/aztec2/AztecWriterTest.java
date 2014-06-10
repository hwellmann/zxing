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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Result;
import com.google.zxing.aztec.AztecReader;
import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;


public class AztecWriterTest {
    
    @Test
    public void writeAztec87() throws Exception {
        AztecWriter writer = new AztecWriter();
        String contents = "This is the encoded message.";
        Map<EncodeHintType,Object> hints = new HashMap<EncodeHintType, Object>();
        hints.put(EncodeHintType.AZTEC_LAYERS, 17);
        BitMatrix matrix = writer.encode(contents, BarcodeFormat.AZTEC, 400, 400, hints);
        
        MatrixToImageWriter.writeToPath(matrix, "PNG", new File("target/aztec87.png").toPath());
        
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        BufferedImageLuminanceSource luminanceSource = new BufferedImageLuminanceSource(image);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
        AztecReader reader = new AztecReader();
        Result result = reader.decode(binaryBitmap);
        System.out.println(result.getText());
    }

}
