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
import android.graphics.PointF
import android.graphics.Rect
import com.r.openarcade.data.BodyPart
import com.r.openarcade.data.KeyPoint
import com.r.openarcade.data.Person
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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

    private fun calculateDistance(point1: PointF, point2: PointF): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return sqrt(dx * dx + dy * dy)
    }

    fun keepLastPoint(list: LinkedList<PointF>) {
        if (list.size > 1) {
            val lastItem = list.last()
            list.clear()
            list.add(lastItem)
        }
    }

    fun getStrokeThreshold(keyPoints: List<KeyPoint>): Float {
        val leftShoulder = keyPoints.find { it.bodyPart == BodyPart.LEFT_SHOULDER }?.coordinate
        val rightShoulder = keyPoints.find { it.bodyPart == BodyPart.RIGHT_SHOULDER }?.coordinate
        val leftElbow = keyPoints.find { it.bodyPart == BodyPart.LEFT_ELBOW }?.coordinate
        val rightElbow = keyPoints.find { it.bodyPart == BodyPart.RIGHT_ELBOW }?.coordinate
        val leftWrist = keyPoints.find { it.bodyPart == BodyPart.LEFT_WRIST }?.coordinate
        val rightWrist = keyPoints.find { it.bodyPart == BodyPart.RIGHT_WRIST }?.coordinate

        val distances = listOf(
            calculateDistance(leftShoulder!!, leftElbow!!),
            calculateDistance(rightShoulder!!, rightElbow!!),
            calculateDistance(leftElbow, leftWrist!!),
            calculateDistance(rightElbow, rightWrist!!),
            calculateDistance(leftShoulder, rightShoulder)
        )

        return distances.average().toFloat()
    }

    fun isVerticalStrokeInList(points: List<PointF>, threshold: Float): Float {
        val switchedPoints = points.map { PointF(it.y, it.x) }
        return isHorizontalStrokeInList(switchedPoints, threshold)
    }

    private fun rotatePoint(point: PointF, angle: Double): PointF {
        val rad = Math.toRadians(angle)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val newX = point.x * cosA - point.y * sinA
        val newY = point.x * sinA + point.y * cosA
        return PointF(newX.toFloat(), newY.toFloat())
    }

    fun isDiagonalStrokeInList(points: List<PointF>, threshold: Float, angle: Double): Float {
        val rotatedPoints = points.map { rotatePoint(it,  angle) }
        return isHorizontalStrokeInList(rotatedPoints, threshold)
    }

    fun isHorizontalStrokeInList(points: List<PointF>, threshold: Float): Float {
        if (points.size < 2) return 0f

        val firstPoint = points.first()
        val lastPoint = points.last()

        if (points.size == 2) return isHorizontalStroke(firstPoint, lastPoint, threshold)

        val averagePoint = getAveragePointF(points);

        var yVariation: Float = 1f
        for (point in points) {
            if (point == firstPoint || point == lastPoint) continue
            val yScore = getStrokeYScore(point, averagePoint, threshold)
            yVariation *= yScore;
        }
        yVariation = yVariation.pow((1.0 / (points.size - 2)).toFloat())

        return yVariation * isHorizontalStroke(firstPoint, lastPoint, threshold)
    }

    private fun isHorizontalStroke(old: PointF, new: PointF, threshold: Float): Float {
        val xScore = getStrokeXScore(old, new, threshold)
        val yScore = getStrokeYScore(old, new, threshold)
        val direction = if (new.x > old.x) 1 else -1

        return direction * sqrt(xScore * yScore)
    }

    private fun isVerticalStroke(old: PointF, new: PointF, threshold: Float): Float {
        // Switch x and y coordinates and call isHorizontalStroke
        val oldSwitched = PointF(old.y, old.x)
        val newSwitched = PointF(new.y, new.x)
        return isHorizontalStroke(oldSwitched, newSwitched, threshold)
    }

    private fun getAveragePointF(points: List<PointF>): PointF {
        if (points.isEmpty()) return PointF(0f, 0f)

        val sumX = points.sumOf { it.x.toDouble() }
        val sumY = points.sumOf { it.y.toDouble() }

        val avgX = sumX / points.size
        val avgY = sumY / points.size

        return PointF(avgX.toFloat(), avgY.toFloat())
    }

    private fun getStrokeXScore(old: PointF, new: PointF, threshold: Float): Float {
        val xDistance = abs(new.x - old.x)
        val xScore = when {
            xDistance < threshold * 0.5f -> 0f
            xDistance < threshold -> (xDistance - threshold * 0.5f) / threshold
            xDistance < threshold * 2 -> (xDistance - threshold) / (threshold * 2f) + 0.5f
            else -> 1f
        }
        return xScore
    }

    private fun getStrokeYScore(old: PointF, new: PointF, threshold: Float): Float {
        val yDistance = abs(new.y - old.y)
        val yScore = when {
            yDistance > threshold / 3 -> 0f
            yDistance > threshold / 4 -> abs(yDistance - threshold / 3) / (threshold / 3 - threshold / 4)
            yDistance > threshold / 5 -> abs(yDistance - threshold / 4) / (threshold / 4 - threshold / 5) + 0.5f
            else -> 1f
        }
        return yScore
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

    fun drawPianoKeys(bitmap: Bitmap, triggeredKeys: MutableSet<Int>): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        val rainbowColors = arrayOf(
            Color.parseColor("#FF0000"), // Red
            Color.parseColor("#FF7F00"), // Orange
            Color.parseColor("#FFFF00"), // Yellow
            Color.parseColor("#00FF00"), // Green
            Color.parseColor("#0000FF"), // Blue
            Color.parseColor("#4B0082"), // Indigo
            Color.parseColor("#8B00FF")  // Violet
        )

        val triggeredPaint = Paint().apply {
            color = Color.YELLOW
            alpha = 192
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            alpha = 192
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }

        val screenWidth = outputBitmap.width
        val screenHeight = outputBitmap.height
        val keyWidth = screenWidth / 7
        val defaultKeyHeight = 80 // Height of the piano keys
        val triggeredKeyHeight = 120 // Height of the triggered piano keys
        val noteNames = arrayOf("C - Do", "D - Re", "E - Mi", "F - Fa", "G - Sol", "A - La", "B - Si")

        for (i in 0 until 7) {
            val paint = Paint().apply {
                color = rainbowColors[i]
                alpha = if (i in triggeredKeys) 192 else 64 // Set alpha to 75% opaque if triggered, else 50% transparent
                style = Paint.Style.FILL
            }


            val left = i * keyWidth
            val right = left + keyWidth - 10
            val keyHeight = if (i in triggeredKeys) triggeredKeyHeight else defaultKeyHeight
            val rect = Rect(
                left + 20,
                (screenHeight - keyHeight) / 2,
                right,
                (screenHeight + keyHeight) / 2)

            canvas.drawRect(rect, paint)
            canvas.drawText(
                noteNames[i],
                (left + right) / 2f,
                screenHeight / 2f,
                textPaint)
        }

        return outputBitmap
    }
}
