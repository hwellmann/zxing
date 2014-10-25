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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.zxing.common.BitMatrix;

public class TextBitMatrixReader {

    public BitMatrix read(String fileName) throws IOException {
        FileInputStream is = new FileInputStream(fileName);
        InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(isr);
        String line = reader.readLine();
        String[] parts = line.split("\\s+");
        int width = Integer.parseInt(parts[0]);
        int height = Integer.parseInt(parts[1]);
        BitMatrix matrix = new BitMatrix(width, height);
        for (int i = 0; i < height; i++) {
            line = reader.readLine();
            for (int j = 0; j < width; j++) {
                if (line.charAt(j) == 'X') {
                    matrix.set(j, i);
                }
            }
        }
        reader.close();
        return matrix;
    }
}
