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

import java.util.Map;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.aztec.AztecReader;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;


/**
 * @author hwellmann
 *
 */
public class EnhancedAztecReader implements Reader {

    @Override
    public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException,
        FormatException {
        return decode(image, null);
    }

    @Override
    public Result decode(BinaryBitmap image, Map<DecodeHintType, ?> hints)
        throws NotFoundException, ChecksumException, FormatException {
        BitMatrix matrix = image.getBlackMatrix();
        ConnectedComponentFinder ccf = new ConnectedComponentFinder(matrix);
        ccf.findConnectedComponents();

        AztecDetector detector = new AztecDetector(ccf);
        boolean found = detector.detect();
        if (!found) {
            throw NotFoundException.getNotFoundInstance();
        }

        detector.computeTransform();
        BitMatrix nm = detector.normalizeMatrix(2, 4);
        
        LuminanceSource normalizedSource = new BitMatrixLuminanceSource(nm);
        BinaryBitmap normalizedBitmap = new BinaryBitmap(new HybridBinarizer(normalizedSource));

        AztecReader aztecReader = new AztecReader();
        Result result = aztecReader.decode(normalizedBitmap, hints);
        
        return result;
    }

    @Override
    public void reset() {
        // do nothing
        
    }

}
