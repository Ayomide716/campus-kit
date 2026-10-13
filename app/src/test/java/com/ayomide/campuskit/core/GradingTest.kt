package com.ayomide.campuskit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {
    private val bands = Bands()

    @Test fun defaultBands() {
        assertEquals("A", Grading.gradeFor(70, bands))
        assertEquals("B", Grading.gradeFor(69, bands))
        assertEquals("B", Grading.gradeFor(60, bands))
        assertEquals("C", Grading.gradeFor(50, bands))
        assertEquals("D", Grading.gradeFor(45, bands))
        assertEquals("E", Grading.gradeFor(40, bands))
        assertEquals("F", Grading.gradeFor(39, bands))
        assertEquals("F", Grading.gradeFor(0, bands))
    }

    @Test fun customBands() {
        val b = Bands(75, 65, 55, 50, 45)
        assertEquals("B", Grading.gradeFor(70, b))
        assertEquals("F", Grading.gradeFor(44, b))
        assertTrue(b.isValid())
        assertFalse(Bands(60, 70, 50, 45, 40).isValid())
        assertFalse(Bands(101, 60, 50, 45, 40).isValid())
    }

    @Test fun parseResult() {
        assertEquals(Result.Score(68), Grading.parseResult(" 68 "))
        assertEquals(Result.Letter("B"), Grading.parseResult("b"))
        assertNull(Grading.parseResult("101"))
        assertNull(Grading.parseResult("-1"))
        assertNull(Grading.parseResult("G"))
        assertNull(Grading.parseResult(""))
        assertNull(Grading.parseResult(null))
    }

    @Test fun gpa() {
        // 3×A(5) + 2×B(4) + 1×F(0) = 15 + 8 + 0 = 23 over 6 units = 3.83
        val items = listOf(3 to "A", 2 to "B", 1 to "F")
        assertEquals(23, Grading.qualityPoints(items))
        assertEquals("3.83", Grading.format(Grading.gpa(items)))
        assertNull(Grading.gpa(emptyList()))
        assertEquals("–", Grading.format(null))
        assertEquals("5.00", Grading.format(Grading.gpa(listOf(2 to "A"))))
    }

    @Test fun degreeClass() {
        assertEquals("First Class", Grading.degreeClass(4.5))
        assertEquals("Second Class Upper (2:1)", Grading.degreeClass(4.49))
        assertEquals("Second Class Upper (2:1)", Grading.degreeClass(3.5))
        assertEquals("Second Class Lower (2:2)", Grading.degreeClass(2.4))
        assertEquals("Third Class", Grading.degreeClass(1.5))
        assertEquals("Pass", Grading.degreeClass(1.0))
        assertEquals("Below pass", Grading.degreeClass(0.9))
    }

    @Test fun requiredGpa() {
        // 60 quality points over 15 units (4.00); want 4.20 after 15 more units → 4.40
        assertEquals(4.40, Grading.requiredGpa(4.2, 60, 15, 15)!!, 1e-9)
        assertNull(Grading.requiredGpa(4.0, 60, 15, 0))
    }
}
