package com.ayomide.campuskit.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ayomide.campuskit.core.ClassSlot
import com.ayomide.campuskit.core.ExamItem

data class Semester(val id: Long, val name: String, val position: Int)
data class Course(val id: Long, val semesterId: Long, val code: String, val units: Int, val grade: String, val score: Int?)
data class AttCourse(val id: Long, val code: String, val planned: Int, val requiredPct: Int, val attended: Int, val missed: Int)
data class Session(val id: Long, val endMillis: Long, val minutes: Int)
data class Expense(val id: Long, val atMillis: Long, val amountKobo: Long, val category: String, val note: String)
data class Deck(val id: Long, val name: String)
data class Card(val id: Long, val deckId: Long, val front: String, val back: String, val box: Int, val dueDay: Long)

/** All app data lives in one local SQLite database. Nothing leaves the phone. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "campus.db", null, 1) {

    companion object {
        @Volatile private var instance: Db? = null
        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context).also { instance = it } }

        val EXPENSE_CATEGORIES = listOf("Food", "Transport", "Data & airtime", "Books & printing", "Hostel & bills", "Fun", "Other")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE classes(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', venue TEXT NOT NULL DEFAULT '', day INTEGER NOT NULL, start_min INTEGER NOT NULL, end_min INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE semesters(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE courses(id INTEGER PRIMARY KEY AUTOINCREMENT, semester_id INTEGER NOT NULL, code TEXT NOT NULL, units INTEGER NOT NULL, grade TEXT NOT NULL, score INTEGER)")
        db.execSQL("CREATE TABLE attendance(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, planned INTEGER NOT NULL, required_pct INTEGER NOT NULL, attended INTEGER NOT NULL DEFAULT 0, missed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE exams(id INTEGER PRIMARY KEY AUTOINCREMENT, course TEXT NOT NULL, at_millis INTEGER NOT NULL, venue TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, end_millis INTEGER NOT NULL, minutes INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, at_millis INTEGER NOT NULL, amount_kobo INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE decks(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY AUTOINCREMENT, deck_id INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, box INTEGER NOT NULL DEFAULT 1, due_day INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_courses_sem ON courses(semester_id)")
        db.execSQL("CREATE INDEX idx_cards_deck ON cards(deck_id)")
        db.execSQL("CREATE INDEX idx_expenses_at ON expenses(at_millis)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 only; future migrations go here.
    }

    // ---- helpers ----------------------------------------------------------

    private inline fun <T> query(sql: String, args: Array<String> = emptyArray(), map: (Cursor) -> T): List<T> =
        readableDatabase.rawQuery(sql, args).use { c ->
            val out = ArrayList<T>(c.count)
            while (c.moveToNext()) out += map(c)
            out
        }

    private fun longScalar(sql: String, args: Array<String> = emptyArray()): Long =
        readableDatabase.rawQuery(sql, args).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L }

    private fun upsert(table: String, id: Long, values: ContentValues): Long =
        if (id > 0) {
            writableDatabase.update(table, values, "id=?", arrayOf(id.toString())); id
        } else {
            writableDatabase.insert(table, null, values)
        }

    private fun delete(table: String, id: Long) {
        writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
    }

    // ---- timetable ----------------------------------------------------------

    fun classes(): List<ClassSlot> = query("SELECT id, code, title, venue, day, start_min, end_min FROM classes ORDER BY day, start_min") {
        ClassSlot(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getInt(4), it.getInt(5), it.getInt(6))
    }

    fun saveClass(c: ClassSlot): Long = upsert("classes", c.id, ContentValues().apply {
        put("code", c.code); put("title", c.title); put("venue", c.venue)
        put("day", c.day); put("start_min", c.startMin); put("end_min", c.endMin)
    })

    fun deleteClass(id: Long) = delete("classes", id)

    // ---- GPA ----------------------------------------------------------------

    fun semesters(): List<Semester> = query("SELECT id, name, position FROM semesters ORDER BY position, id") {
        Semester(it.getLong(0), it.getString(1), it.getInt(2))
    }

    fun saveSemester(s: Semester): Long = upsert("semesters", s.id, ContentValues().apply {
        put("name", s.name); put("position", s.position)
    })

    fun deleteSemester(id: Long) {
        writableDatabase.delete("courses", "semester_id=?", arrayOf(id.toString()))
        delete("semesters", id)
    }

    fun courses(): List<Course> = query("SELECT id, semester_id, code, units, grade, score FROM courses ORDER BY id") {
        Course(it.getLong(0), it.getLong(1), it.getString(2), it.getInt(3), it.getString(4), if (it.isNull(5)) null else it.getInt(5))
    }

    fun saveCourse(c: Course): Long = upsert("courses", c.id, ContentValues().apply {
        put("semester_id", c.semesterId); put("code", c.code); put("units", c.units); put("grade", c.grade)
        if (c.score == null) putNull("score") else put("score", c.score)
    })

    fun deleteCourse(id: Long) = delete("courses", id)

    // ---- attendance -----------------------------------------------------------

    fun attendance(): List<AttCourse> = query("SELECT id, code, planned, required_pct, attended, missed FROM attendance ORDER BY code") {
        AttCourse(it.getLong(0), it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5))
    }

    fun saveAttendance(a: AttCourse): Long = upsert("attendance", a.id, ContentValues().apply {
        put("code", a.code); put("planned", a.planned); put("required_pct", a.requiredPct)
        put("attended", a.attended); put("missed", a.missed)
    })

    fun deleteAttendance(id: Long) = delete("attendance", id)

    // ---- exams ----------------------------------------------------------------

    fun exams(): List<ExamItem> = query("SELECT id, course, at_millis, venue FROM exams ORDER BY at_millis") {
        ExamItem(it.getLong(0), it.getString(1), it.getLong(2), it.getString(3))
    }

    fun saveExam(e: ExamItem): Long = upsert("exams", e.id, ContentValues().apply {
        put("course", e.course); put("at_millis", e.atMillis); put("venue", e.venue)
    })

    fun deleteExam(id: Long) = delete("exams", id)

    // ---- study sessions ---------------------------------------------------------

    fun addSession(endMillis: Long, minutes: Int) {
        writableDatabase.insert("sessions", null, ContentValues().apply {
            put("end_millis", endMillis); put("minutes", minutes)
        })
    }

    fun sessions(limit: Int = 1000): List<Session> =
        query("SELECT id, end_millis, minutes FROM sessions ORDER BY end_millis DESC LIMIT $limit") {
            Session(it.getLong(0), it.getLong(1), it.getInt(2))
        }

    fun studyMinutesSince(millis: Long): Long =
        longScalar("SELECT SUM(minutes) FROM sessions WHERE end_millis >= ?", arrayOf(millis.toString()))

    fun clearSessions() {
        writableDatabase.delete("sessions", null, null)
    }

    // ---- budget -----------------------------------------------------------------

    fun expenses(fromMillis: Long, toMillis: Long): List<Expense> =
        query(
            "SELECT id, at_millis, amount_kobo, category, note FROM expenses WHERE at_millis >= ? AND at_millis < ? ORDER BY at_millis DESC",
            arrayOf(fromMillis.toString(), toMillis.toString()),
        ) { Expense(it.getLong(0), it.getLong(1), it.getLong(2), it.getString(3), it.getString(4)) }

    fun saveExpense(e: Expense): Long = upsert("expenses", e.id, ContentValues().apply {
        put("at_millis", e.atMillis); put("amount_kobo", e.amountKobo); put("category", e.category); put("note", e.note)
    })

    fun deleteExpense(id: Long) = delete("expenses", id)

    // ---- flashcards -------------------------------------------------------------

    fun decks(): List<Deck> = query("SELECT id, name FROM decks ORDER BY name COLLATE NOCASE") { Deck(it.getLong(0), it.getString(1)) }

    fun deck(id: Long): Deck? = query("SELECT id, name FROM decks WHERE id=?", arrayOf(id.toString())) {
        Deck(it.getLong(0), it.getString(1))
    }.firstOrNull()

    fun saveDeck(d: Deck): Long = upsert("decks", d.id, ContentValues().apply { put("name", d.name) })

    fun deleteDeck(id: Long) {
        writableDatabase.delete("cards", "deck_id=?", arrayOf(id.toString()))
        delete("decks", id)
    }

    fun cards(deckId: Long): List<Card> =
        query("SELECT id, deck_id, front, back, box, due_day FROM cards WHERE deck_id=? ORDER BY id", arrayOf(deckId.toString())) {
            Card(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getInt(4), it.getLong(5))
        }

    fun dueCount(deckId: Long?, today: Long): Long =
        if (deckId == null) longScalar("SELECT COUNT(*) FROM cards WHERE due_day <= ?", arrayOf(today.toString()))
        else longScalar("SELECT COUNT(*) FROM cards WHERE deck_id=? AND due_day <= ?", arrayOf(deckId.toString(), today.toString()))

    fun saveCard(c: Card): Long = upsert("cards", c.id, ContentValues().apply {
        put("deck_id", c.deckId); put("front", c.front); put("back", c.back); put("box", c.box); put("due_day", c.dueDay)
    })

    fun addCards(deckId: Long, pairs: List<Pair<String, String>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((f, b) in pairs) {
                db.insert("cards", null, ContentValues().apply {
                    put("deck_id", deckId); put("front", f); put("back", b); put("box", 1); put("due_day", 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCard(id: Long) = delete("cards", id)
}
