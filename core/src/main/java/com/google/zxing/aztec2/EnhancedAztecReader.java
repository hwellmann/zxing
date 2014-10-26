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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.aztec.AztecDetectorResult;
import com.google.zxing.aztec.decoder.Decoder;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;

/**
 * @author hwellmann
 * 
 */
public class EnhancedAztecReader implements Reader {

    private static Logger log = LoggerFactory.getLogger(EnhancedAztecReader.class.getSimpleName());

    @Override
    public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException,
        FormatException {
        return decode(image, null);
    }

    @Override
    public Result decode(BinaryBitmap image, Map<DecodeHintType, ?> hints)
        throws NotFoundException, ChecksumException, FormatException {
        try {
            return decodeUnsafe(image, hints);
        }
        catch (NotFoundException | ChecksumException | FormatException exc) {
            throw exc;
        }
        catch (Exception exc) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            exc.printStackTrace(ps);
            ps.close();
            Result result = new Result(baos.toString(), null, null, BarcodeFormat.AZTEC);
            return result;
        }
    }

    public Result decodeUnsafe(BinaryBitmap image, Map<DecodeHintType, ?> hints)
        throws NotFoundException, ChecksumException, FormatException {
        BitMatrix matrix = image.getBlackMatrix();
        ConnectedComponentFinder ccf = new ConnectedComponentFinder(matrix, true);
        log.info("find components");
        ccf.findConnectedComponents();

        AztecDetector detector = new AztecDetector(ccf);
        log.info("detect");
        boolean found = detector.detect();
        if (!found) {
            throw NotFoundException.getNotFoundInstance();
        }

        log.info("computeTransform");
        detector.computeTransform();

        AztecDetectorResult detectorResult = detector.getDetectorResult();

        log.info("decode");
        DecoderResult decoderResult = new Decoder().decode(detectorResult);
        log.info("done");

        Result result = new Result(decoderResult.getText(), decoderResult.getRawBytes(),
            detectorResult.getPoints(), BarcodeFormat.AZTEC);

        if (hints != null) {
            ResultPointCallback rpcb = (ResultPointCallback) hints
                .get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
            if (rpcb != null) {
                for (ResultPoint point : detectorResult.getPoints()) {
                    rpcb.foundPossibleResultPoint(point);
                    log.info("result point: {}", point);
                }
            }
        }

        return result;
    }

    @Override
    public void reset() {
        // do nothing
    }
}
