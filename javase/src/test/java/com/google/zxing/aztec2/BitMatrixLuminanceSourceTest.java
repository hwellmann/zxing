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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.google.zxing.NotFoundException;
import com.google.zxing.aztec2.BitMatrixLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;


/**
 * @author hwellmann
 *
 */
public class BitMatrixLuminanceSourceTest {

    
    @Test
    public void roundTrip() throws NotFoundException {
        BitMatrix bitMatrix = new BitMatrix(5, 4);
        bitMatrix.set(2, 0);
        bitMatrix.set(4, 1);
        bitMatrix.set(1, 2);
        bitMatrix.set(0, 3);
        
        BitMatrixLuminanceSource luminanceSource = new BitMatrixLuminanceSource(bitMatrix);
        HybridBinarizer binarizer = new HybridBinarizer(luminanceSource);
        BitMatrix blackMatrix = binarizer.getBlackMatrix();
        for (int y = 0; y < bitMatrix.getHeight(); y++) {
            for (int x = 0; x < bitMatrix.getWidth(); x++) {
                assertThat(blackMatrix.get(x, y), is(bitMatrix.get(x, y)));
            }
        }
    }
}
