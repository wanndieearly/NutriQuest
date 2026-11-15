package com.nutriquest.app

import org.junit.Assert.assertEquals
import org.junit.Test

class YoloDecoderTest {
    @Test
    fun removesDuplicateBoxesButKeepsDifferentFoodsAndInstances() {
        val boxes = listOf(
            floatArrayOf(.3f, .3f, .2f, .2f, .91f, .02f),
            floatArrayOf(.31f, .3f, .2f, .2f, .81f, .04f),
            floatArrayOf(.3f, .3f, .2f, .2f, .02f, .76f),
            floatArrayOf(.8f, .8f, .2f, .2f, .67f, .02f),
            floatArrayOf(.5f, .5f, .2f, .2f, .25f, .22f)
        )
        val detected = decodeYolo(boxes.size, 2) { box, feature -> boxes[box][feature] }
        assertEquals(listOf(0, 1, 0), detected.map { it.label })
        assertEquals(listOf(.91f, .76f, .67f), detected.map { it.score })
    }
}
