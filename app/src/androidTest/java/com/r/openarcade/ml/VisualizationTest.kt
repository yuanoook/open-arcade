/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package com.r.openarcade.ml

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.r.openarcade.VisualizationUtils
import com.r.openarcade.data.Device

/**
 * This test is used to visually verify detection results by the models.
 * You can put a breakpoint at the end of the method, debug this method, than use the
 * "View Bitmap" feature of the debugger to check the visualized detection result.
 */
@RunWith(AndroidJUnit4::class)
class VisualizationTest {

    companion object {
        private const val TEST_INPUT_IMAGE = "image2.jpg"
    }

    private lateinit var appContext: Context
    private lateinit var inputBitmap: Bitmap

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        inputBitmap = EvaluationUtils.loadBitmapAssetByName(TEST_INPUT_IMAGE)
    }
}