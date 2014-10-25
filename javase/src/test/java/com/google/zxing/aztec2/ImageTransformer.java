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

import com.google.zxing.common.PerspectiveTransform;

/**
 * @author hwellmann
 * 
 */
public class ImageTransformer {

    private PerspectiveTransform transform;
    private int width;

    public ImageTransformer(PerspectiveTransform transform, int width) {
        this.transform = transform;
        this.width = width;
    }

    public BufferedImage transform(BufferedImage image) {
        Envelope env = new Envelope();
        env.minX = 0;
        env.minY = 0;
        env.maxX = image.getWidth() - 1;
        env.maxY = image.getHeight() - 1;
        BufferedImage result = new BufferedImage(width, width, BufferedImage.TYPE_INT_RGB);
        float offset = width / 2.0f;
        float[] p = new float[2];
        for (int j = 0; j < width; j++) {
            float y = j - offset;
            for (int i = 0; i < width; i++) {
                float x = i - offset;
                p[0] = x;
                p[1] = y;
                transform.transformPoints(p);
                int tx = Math.round(p[0]);
                int ty = Math.round(p[1]);
                if (env.contains(tx, ty)) {
                    int rgb = image.getRGB(tx, ty);
                    result.setRGB(i, j, rgb);
                }
                else {
                    result.setRGB(i, j, 0xFFFFFF);
                }
            }
        }
        return result;
    }
}
