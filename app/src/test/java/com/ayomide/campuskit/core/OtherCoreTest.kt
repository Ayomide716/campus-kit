package com.ayomide.campuskit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AttendanceTest {
    @Test fun allowedMisses() {
        // 26 planned, 75% → must attend 20, may miss 6.
        val s = AttendanceCalc.status(26, 75, 10, 2)
        assertEquals(6, s.allowedMisses)
        assertEquals(4, s.missesLeft)
        assertEquals(12, s.held)
        assertEquals(83, s.percent)
        assertFalse(s.belowRequirement)
    }

    @Test fun belowRequirement() {
        val s = AttendanceCalc.status(10, 75, 3, 3) // must attend 8 → may miss 2
        assertEquals(2, s.allowedMisses)
        assertEquals(-1, s.missesLeft)
        assertTrue(s.belowRequirement)
    }

    @Test fun nothingHeld() {
        val s = AttendanceCalc.status(0, 75, 0, 0)
        assertNull(s.percent)
        assertEquals(0, s.missesLeft)
    }
}

class MoneyTest {
    @Test fun parse() {
        assertEquals(150_000L, Money.parseKobo("1500"))
        assertEquals(150_000L, Money.parseKobo("1,500"))
        assertEquals(150_050L, Money.parseKobo("₦1,500.50"))
        assertEquals(150_050L, Money.parseKobo("1500.5"))
        assertNull(Money.parseKobo("1.234"))
        assertNull(Money.parseKobo("0"))
        assertNull(Money.parseKobo("-5"))
        assertNull(Money.parseKobo("abc"))
        assertNull(Money.parseKobo(""))
    }

    @Test fun format() {
        assertEquals("₦1,500", Money.format(150_000))
        assertEquals("₦1,500.50", Money.format(150_050))
        assertEquals("₦0", Money.format(0))
        assertEquals("₦999", Money.format(99_900))
        assertEquals("₦1,000,000", Money.format(100_000_000))
        assertEquals("-₦200", Money.format(-20_000))
        assertEquals("₦0.05", Money.format(5))
    }

    @Test fun perDay() {
        assertEquals(33_333L, Money.perDay(100_000, 3))
        assertEquals(0L, Money.perDay(-5, 3))
        assertEquals(0L, Money.perDay(100, 0))
    }
}

class LeitnerTest {
    @Test fun answers() {
        assertEquals(Leitner.Next(2, 101), Leitner.answer(1, true, 100))
        assertEquals(Leitner.Next(5, 114), Leitner.answer(4, true, 100))
        assertEquals(Leitner.Next(5, 114), Leitner.answer(5, true, 100))
        assertEquals(Leitner.Next(1, 100), Leitner.answer(4, false, 100))
        assertTrue(Leitner.isDue(100, 100))
        assertFalse(Leitner.isDue(101, 100))
    }

    @Test fun bulk() {
        val cards = Leitner.parseBulk(
            "Mitochondria | Powerhouse of the cell\n\nH2O = Water\nCPU\tProcessor\nno separator\n | empty front\nKey | a=b"
        )
        assertEquals(
            listOf(
                "Mitochondria" to "Powerhouse of the cell",
                "H2O" to "Water",
                "CPU" to "Processor",
                "Key" to "a=b",
            ),
            cards,
        )
    }
}

class StreakTest {
    @Test fun streaks() {
        assertEquals(0, Streak.current(emptyList(), 100))
        assertEquals(3, Streak.current(listOf(98L, 99L, 100L), 100))
        assertEquals(2, Streak.current(listOf(98L, 99L), 100)) // today not studied yet
        assertEquals(0, Streak.current(listOf(97L, 98L), 100))
        assertEquals(1, Streak.current(listOf(100L, 100L, 95L), 100))
    }
}

class CountdownTest {
    @Test fun texts() {
        val m = 60_000L; val h = 60 * m; val d = 24 * h
        assertEquals("now", Countdown.until(0))
        assertEquals("in 1 min", Countdown.until(10_000))
        assertEquals("in 45 min", Countdown.until(45 * m))
        assertEquals("in 1 hr 30 min", Countdown.until(90 * m))
        assertEquals("in 2 hrs", Countdown.until(2 * h))
        assertEquals("in 1 day", Countdown.until(d))
        assertEquals("in 3 days 4 hrs", Countdown.until(3 * d + 4 * h + 5 * m))
    }
}

class ScheduleTest {
    private val zone = ZoneId.of("Africa/Lagos")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // 2026-10-12 is a Monday.
    private val csc = ClassSlot(1, "CSC 201", "Programming", "LT2", 1, 8 * 60, 10 * 60)
    private val mth = ClassSlot(2, "MTH 101", "", "", 3, 14 * 60, 16 * 60)

    @Test fun nextClass() {
        val now = ms(2026, 10, 12, 7, 0) // Monday 07:00
        assertEquals(ms(2026, 10, 12, 8, 0), Schedule.currentOrNext(listOf(csc, mth), now, zone)!!.startMillis)
        val during = ms(2026, 10, 12, 9, 0)
        assertEquals("CSC 201", Schedule.currentOrNext(listOf(csc, mth), during, zone)!!.slot.code)
        assertTrue(Schedule.isClassOngoing(listOf(csc), during, zone))
        val after = ms(2026, 10, 12, 10, 0) // ends exactly now → next is Wednesday MTH
        assertEquals("MTH 101", Schedule.currentOrNext(listOf(csc, mth), after, zone)!!.slot.code)
        assertFalse(Schedule.isClassOngoing(listOf(csc), after, zone))
        assertNull(Schedule.currentOrNext(emptyList(), now, zone))
    }

    @Test fun nextWeekWraps() {
        val tueNight = ms(2026, 10, 13, 22, 0)
        assertEquals(ms(2026, 10, 14, 14, 0), Schedule.currentOrNext(listOf(csc, mth), tueNight, zone)!!.startMillis)
        val sat = ms(2026, 10, 17, 12, 0)
        assertEquals(ms(2026, 10, 19, 8, 0), Schedule.currentOrNext(listOf(csc), sat, zone)!!.startMillis)
    }

    @Test fun events() {
        val after = ms(2026, 10, 12, 7, 0)
        val until = ms(2026, 10, 12, 23, 59)
        val exam = ExamItem(9, "GST 101", ms(2026, 10, 13, 9, 0), "Hall A")
        val ev = Schedule.events(listOf(csc), listOf(exam), after, until, zone, 10, silenceBoundaries = true)
        assertEquals(
            listOf(EventType.CLASS_REMINDER, EventType.CLASS_START, EventType.EXAM_DAY_BEFORE, EventType.CLASS_END),
            ev.map { it.type },
        )
        assertEquals(ms(2026, 10, 12, 7, 50), ev[0].atMillis)
        assertEquals("CSC 201 in 10 min", ev[0].title)
        assertEquals(ms(2026, 10, 12, 9, 0), ev[2].atMillis)

        val noRem = Schedule.events(listOf(csc), emptyList(), after, until, zone, 0, silenceBoundaries = false)
        assertTrue(noRem.isEmpty())
    }

    @Test fun eventWindowIsExclusiveStartInclusiveEnd() {
        val rem = ms(2026, 10, 12, 7, 50)
        assertTrue(Schedule.events(listOf(csc), emptyList(), rem, rem + 1, zone, 10, false).isEmpty())
        assertEquals(1, Schedule.events(listOf(csc), emptyList(), rem - 1, rem, zone, 10, false).size)
    }

    @Test fun formatting() {
        assertEquals("08:00–10:00", Schedule.range(csc))
        assertEquals("00:05", Schedule.hhmm(5))
    }
}
