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

import com.google.zxing.LuminanceSource;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.BitMatrix;

/**
 * @author hwellmann
 * 
 */
public class BitMatrixLuminanceSource extends LuminanceSource {

    private BitMatrix bitMatrix;

    /**
     * 
     */
    public BitMatrixLuminanceSource(BitMatrix bitMatrix) {
        super(bitMatrix.getWidth(), bitMatrix.getHeight());
        this.bitMatrix = bitMatrix;
    }

    @Override
    public byte[] getRow(int y, byte[] row) {
        byte[] result = new byte[getWidth()];
        BitArray bitArray = bitMatrix.getRow(y, null);
        for (int i = 0; i < getWidth(); i++) {
            if (bitArray.get(i)) {
                result[i] = 0;
            }
            else {
                result[i] = (byte) 0xFF;
            }
        }
        return result;
    }

    @Override
    public byte[] getMatrix() {
        byte[] result = new byte[getWidth() * getHeight()];
        for (int y = 0; y < getHeight(); y++) {
            for (int x = 0; x < getWidth(); x++) {
                int i = y * getWidth() + x;
                if (bitMatrix.get(x, y)) {
                    result[i] = 0;
                }
                else {
                    result[i] = (byte) 0xFF;
                }
            }
        }
        return result;
    }

}
