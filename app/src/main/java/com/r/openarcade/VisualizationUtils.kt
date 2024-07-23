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

package com.r.openarcade

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.r.openarcade.data.BodyPart
import com.r.openarcade.data.Person
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

object VisualizationUtils {
    /** Radius of circle used to draw keypoints.  */
    private const val CIRCLE_RADIUS = 3f

    /** Width of line used to connected two keypoints.  */
    private const val LINE_WIDTH = 2f

    /** The text size of the person id that will be displayed when the tracker is available.  */
    private const val PERSON_ID_TEXT_SIZE = 30f

    /** Distance from person id to the nose keypoint.  */
    private const val PERSON_ID_MARGIN = 6f

    /** Pair of keypoints to draw lines between.  */
    private val bodyJoints = listOf(
        Pair(BodyPart.NOSE, BodyPart.LEFT_EYE),
        Pair(BodyPart.NOSE, BodyPart.RIGHT_EYE),
        Pair(BodyPart.LEFT_EYE, BodyPart.LEFT_EAR),
        Pair(BodyPart.RIGHT_EYE, BodyPart.RIGHT_EAR),
        Pair(BodyPart.NOSE, BodyPart.LEFT_SHOULDER),
        Pair(BodyPart.NOSE, BodyPart.RIGHT_SHOULDER),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW),
        Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
        Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
        Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
        Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP),
        Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
        Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
        Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
        Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
        Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
    )

    // Draw line and point indicate body pose
    fun drawBodyKeypoints(
        input: Bitmap,
        persons: List<Person>,
        isTrackerEnabled: Boolean = false
    ): Bitmap {
        if (persons.isEmpty()) return input

        val paintCircle = Paint().apply {
            strokeWidth = CIRCLE_RADIUS
            color = Color.RED
            style = Paint.Style.FILL
        }
        val paintLine = Paint().apply {
            strokeWidth = LINE_WIDTH
            color = Color.RED
            style = Paint.Style.STROKE
        }

        val paintText = Paint().apply {
            textSize = PERSON_ID_TEXT_SIZE
            color = Color.BLUE
            textAlign = Paint.Align.LEFT
        }

        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val originalSizeCanvas = Canvas(output)
        persons.forEach { person ->
            // draw person id if tracker is enable
            if (isTrackerEnabled) {
                person.boundingBox?.let {
                    val personIdX = max(0f, it.left)
                    val personIdY = max(0f, it.top)

                    originalSizeCanvas.drawText(
                        person.id.toString(),
                        personIdX,
                        personIdY - PERSON_ID_MARGIN,
                        paintText
                    )
                    originalSizeCanvas.drawRect(it, paintLine)
                }
            }
            bodyJoints.forEach {
                val pointA = person.keyPoints[it.first.position].coordinate
                val pointB = person.keyPoints[it.second.position].coordinate
                originalSizeCanvas.drawLine(pointA.x, pointA.y, pointB.x, pointB.y, paintLine)
            }

            person.keyPoints.forEach { point ->
                originalSizeCanvas.drawCircle(
                    point.coordinate.x,
                    point.coordinate.y,
                    CIRCLE_RADIUS,
                    paintCircle
                )
            }
        }
        return output
    }

    fun calculateHeadRotation(person: Person): Pair<Float, Float> {
        val horizontalRotation = calculateHeadRotationHorizontal(person)
        val verticalRotation = calculateHeadRotationVertical(person)
        return Pair(horizontalRotation, verticalRotation)
    }

    fun drawHeadRotationLineOnBitmap(headRotation: Pair<Float, Float>, input: Bitmap): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        // Create a paint object for the lines
        val paint = Paint().apply {
            color = 0xFF00FF00.toInt() // Green color in ARGB format
            strokeWidth = 5f // Line thickness
        }

        // Calculate the x-coordinate for the vertical line based on horizontal head rotation
        val x = output.width * headRotation.first

        // Draw the vertical line on the bitmap
        canvas.drawLine(x, 0f, x, output.height.toFloat(), paint)

        // Calculate the y-coordinate for the horizontal line based on vertical head rotation
        val y = output.height * headRotation.second

        // Draw the horizontal line on the bitmap
        canvas.drawLine(0f, y, output.width.toFloat(), y, paint)

        return output
    }

    private fun calculateHeadRotationHorizontal(person: Person): Float {
        val nose = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.NOSE }
        val leftEar = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_EAR }
        val rightEar = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_EAR }

        if (nose == null || leftEar == null || rightEar == null) {
            return 0.5f
        }

        val noseX = nose.coordinate.x
        val leftEarX = leftEar.coordinate.x
        val rightEarX = rightEar.coordinate.x

        return when {
            noseX <= rightEarX -> 0f
            noseX >= leftEarX -> 1f
            else -> betweenZeroAndOne((noseX - rightEarX) / (rightEarX - leftEarX), 1.5f)
        }
    }

    private fun calculateHeadRotationVertical(person: Person): Float {
        val nose = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.NOSE }
        val leftEye = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_EYE }
        val rightEye = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_EYE }
        val leftEar = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.LEFT_EAR }
        val rightEar = person.keyPoints.firstOrNull { it.bodyPart == BodyPart.RIGHT_EAR }

        if (nose == null || leftEye == null || rightEye == null || leftEar == null || rightEar == null) {
            return 0.5f
        }

        val noseY = nose.coordinate.y
        val avgEyesY = (leftEye.coordinate.y + rightEye.coordinate.y) / 2
        val avgEarsY = (leftEar.coordinate.y + rightEar.coordinate.y) / 2

        return when {
            avgEarsY <= avgEyesY -> 1f
            avgEarsY >= noseY -> 0f
            else -> betweenZeroAndOne((avgEarsY - noseY) / (avgEyesY - noseY), 1f)
        }
    }

    private fun betweenZeroAndOne(value: Float, zoom: Float): Float {
        return min(
            (1).toFloat(),
            max(
                (0).toFloat(),
                ((abs(value) - 0.5) * zoom + 0.5).toFloat()
            )
        )
    }
}
