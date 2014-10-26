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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QuadrilateralFinder {
    
    private static Logger log = LoggerFactory.getLogger(QuadrilateralFinder.class.getSimpleName());

    
    private ConnectedComponentFinder ccf;
    private Envelope wsEnv;
    private Quadrilateral q;
    private int envDim;

    public QuadrilateralFinder(ConnectedComponentFinder ccf) {
        this.ccf = ccf;
    }
    
    public Quadrilateral findQuadrilateral(int label) {
        ConnectedComponent component = ccf.getComponentMap().get(label);
        wsEnv = component.getEnvelope();
        int envWidth = wsEnv.maxX - wsEnv.minX;
        int envHeight = wsEnv.maxY - wsEnv.minY;
        envDim = Math.max(envWidth, envHeight);
        q = new Quadrilateral();
        findTopLeftCorner(label);
        findTopRightCorner(label);
        findBottomLeftCorner(label);
        findBottomRightCorner(label);
        return q;
    }
    
    private void findTopLeftCorner(int label) {
        for (int j = wsEnv.minY; j < wsEnv.minY + envDim; j++) {
            int y = j;
            for (int x = wsEnv.minX; x < wsEnv.minX + envDim && y >= wsEnv.minY; x++, y--) {
                if (wsEnv.contains(x, y) && ccf.getLabel(x, y) == label) {
                    log.debug("top left = {} {}", x, y);
                    q.nwx = x;
                    q.nwy = y;
                    return;
                }
            }
        }
    }

    private void findTopRightCorner(int label) {
        for (int j = wsEnv.minY; j < wsEnv.minY + envDim; j++) {
            int y = j;
            for (int x = wsEnv.minX + envDim; x >= wsEnv.minX && y >= wsEnv.minY; x--, y--) {
                if (wsEnv.contains(x, y) && ccf.getLabel(x, y) == label) {
                    log.debug("top right = {} {}", x, y);
                    q.nex = x;
                    q.ney = y;
                    return;
                }
            }
        }
    }

    private void findBottomLeftCorner(int label) {
        for (int j = wsEnv.minY + envDim; j >= wsEnv.minY; j--) {
            int y = j;
            for (int x = wsEnv.minX; x < wsEnv.minX + envDim && y < wsEnv.minY + envDim; x++, y++) {
                if (wsEnv.contains(x, y) && ccf.getLabel(x, y) == label) {
                    log.debug("bottom left = {} {}", x, y);
                    q.swx = x;
                    q.swy = y;
                    return;
                }
            }
        }
    }

    private void findBottomRightCorner(int label) {
        for (int j = wsEnv.minY + envDim; j >= wsEnv.minY; j--) {
            int y = j;
            for (int x = wsEnv.minX + envDim; x >= wsEnv.minX && y < wsEnv.minY + envDim; x--, y++) {
                if (wsEnv.contains(x, y) && ccf.getLabel(x, y) == label) {
                    log.debug("bottom right = {} {}", x, y);
                    q.sex = x;
                    q.sey = y;
                    return;
                }
            }
        }
    }
}
