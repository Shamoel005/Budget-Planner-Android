@file:OptIn(ExperimentalMaterial3Api::class)

package com.mydash.planner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =====================  DATA  =====================

data class Txn(val id: Long, val type: String, val amount: Double, val category: String, val note: String, val date: Long)
data class Goal(val id: Long, val name: String, val icon: String, val target: Double, val saved: Double, val deadline: Long)
data class Bill(val id: Long, val name: String, val amount: Double, val day: Int, val category: String, val lastPaid: Int)
data class Data(
    val txns: List<Txn> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val budget: Double = 0.0,
    val catBudgets: Map<String, Double> = emptyMap()
)
data class Advice(val emoji: String, val title: String, val text: String, val tone: Int)

class Cat(val name: String, val emoji: String, val color: Color)

val ExpenseCats = listOf(
    Cat("Food", "🍔", Color(0xFFFF8A4C)), Cat("Groceries", "🛒", Color(0xFF34B27B)),
    Cat("Transport", "🚗", Color(0xFF3B82F6)), Cat("Shopping", "🛍️", Color(0xFFEC4899)),
    Cat("Bills", "💡", Color(0xFFF5B301)), Cat("Rent", "🏠", Color(0xFF8B5CF6)),
    Cat("Health", "💊", Color(0xFF14B8A6)), Cat("Fun", "🎬", Color(0xFFEF4444)),
    Cat("Travel", "✈️", Color(0xFF06B6D4)), Cat("Education", "📚", Color(0xFF6366F1)),
    Cat("Other", "📦", Color(0xFF94A3B8))
)
val IncomeCats = listOf(
    Cat("Salary", "💼", Color(0xFF12A150)), Cat("Freelance", "💻", Color(0xFF3B82F6)),
    Cat("Gift", "🎁", Color(0xFFEC4899)), Cat("Interest", "🏦", Color(0xFFF5B301)),
    Cat("Other", "➕", Color(0xFF94A3B8))
)
val GoalIcons = listOf("✈️", "📱", "🏠", "🚗", "🎓", "💍", "🎁", "💻", "🏝️", "🎯")

fun catOf(name: String, type: String): Cat {
    val list = if (type == "income") IncomeCats else ExpenseCats
    return list.firstOrNull { it.name == name } ?: list.last()
}

fun <T> List<T>.upsertBy(x: T, key: (T) -> Long): List<T> =
    if (any { key(it) == key(x) }) map { if (key(it) == key(x)) x else it } else this + x

object Store {
    private fun file(c: Context) = File(c.filesDir, "budget.json")

    fun toJson(d: Data): String {
        val o = JSONObject()
        o.put("budget", d.budget)
        val cb = JSONObject()
        d.catBudgets.forEach { (k, v) -> cb.put(k, v) }
        o.put("catBudgets", cb)
        val t = JSONArray()
        d.txns.forEach {
            t.put(JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount)
                .put("category", it.category).put("note", it.note).put("date", it.date))
        }
        o.put("txns", t)
        val g = JSONArray()
        d.goals.forEach {
            g.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon)
                .put("target", it.target).put("saved", it.saved).put("deadline", it.deadline))
        }
        o.put("goals", g)
        val b = JSONArray()
        d.bills.forEach {
            b.put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)
                .put("day", it.day).put("category", it.category).put("lastPaid", it.lastPaid))
        }
        o.put("bills", b)
        return o.toString()
    }

    fun parse(text: String): Data? = try {
        val o = JSONObject(text)
        val t = o.optJSONArray("txns") ?: JSONArray()
        val g = o.optJSONArray("goals") ?: JSONArray()
        val b = o.optJSONArray("bills") ?: JSONArray()
        val cb = o.optJSONObject("catBudgets")
        Data(
            txns = (0 until t.length()).map { i ->
                val x = t.getJSONObject(i)
                Txn(x.getLong("id"), x.getString("type"), x.getDouble("amount"), x.getString("category"), x.optString("note"), x.getLong("date"))
            },
            goals = (0 until g.length()).map { i ->
                val x = g.getJSONObject(i)
                Goal(x.getLong("id"), x.getString("name"), x.optString("icon", "🎯"), x.getDouble("target"), x.optDouble("saved", 0.0), x.optLong("deadline"))
            },
            bills = (0 until b.length()).map { i ->
                val x = b.getJSONObject(i)
                Bill(x.getLong("id"), x.getString("name"), x.getDouble("amount"), x.getInt("day"), x.optString("category", "Bills"), x.optInt("lastPaid", -1))
            },
            budget = o.optDouble("budget", 0.0),
            catBudgets = if (cb == null) emptyMap() else cb.keys().asSequence().associateWith { k -> cb.getDouble(k) }
        )
    } catch (e: Exception) { null }

    fun load(c: Context): Data {
        val f = file(c)
        return if (f.exists()) (parse(f.readText()) ?: Data()) else Data()
    }

    fun save(c: Context, d: Data) { file(c).writeText(toJson(d)) }

    fun csv(d: Data): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder("Date,Type,Category,Amount,Note\n")
        d.txns.sortedBy { it.date }.forEach {
            sb.append(f.format(Date(it.date))).append(',').append(it.type).append(',').append(it.category).append(',')
                .append(it.amount).append(',').append('"').append(it.note.replace("\"", "\"\"")).append("\"\n")
        }
        return sb.toString()
    }
}

// =====================  HELPERS  =====================

fun ymOf(ms: Long): Int {
    val c = Calendar.getInstance(); c.timeInMillis = ms
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}
fun nowYm(): Int = ymOf(System.currentTimeMillis())
fun ymCal(ym: Int): Calendar { val c = Calendar.getInstance(); c.clear(); c.set(ym / 12, ym % 12, 1); return c }
fun ymLabel(ym: Int): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(ymCal(ym).time)
fun ymShort(ym: Int): String = SimpleDateFormat("MMM yy", Locale.getDefault()).format(ymCal(ym).time)
fun monthName(ym: Int): String = SimpleDateFormat("MMMM", Locale.getDefault()).format(ymCal(ym).time)
fun daysIn(ym: Int): Int = ymCal(ym).getActualMaximum(Calendar.DAY_OF_MONTH)
fun dayOfMonth(ms: Long): Int { val c = Calendar.getInstance(); c.timeInMillis = ms; return c.get(Calendar.DAY_OF_MONTH) }
fun dayStart(ms: Long): Long {
    val c = Calendar.getInstance(); c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
fun todayStart(): Long = dayStart(System.currentTimeMillis())
const val DAY = 24L * 60 * 60 * 1000
fun fmtShort(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
fun fmtDayHeader(ms: Long): String = when (dayStart(ms)) {
    todayStart() -> "Today"
    todayStart() - DAY -> "Yesterday"
    else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(ms))
}
fun localFromUtcDate(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")); u.timeInMillis = utc
    val c = Calendar.getInstance()
    c.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    return c.timeInMillis
}

fun money(v: Double): String {
    val f = NumberFormat.getNumberInstance(Locale("en", "IN"))
    val frac = if (abs(v) % 1.0 < 0.005) 0 else 2
    f.maximumFractionDigits = frac
    f.minimumFractionDigits = frac
    return (if (v < -0.004) "-" else "") + "₹" + f.format(abs(v))
}
fun compact(v: Double): String = when {
    v >= 1e7 -> "%.1fCr".format(v / 1e7)
    v >= 1e5 -> "%.1fL".format(v / 1e5)
    v >= 1e3 -> "%.1fK".format(v / 1e3)
    else -> v.roundToInt().toString()
}
fun pct(a: Double, b: Double): Int = if (b <= 0) 0 else (a / b * 100).roundToInt()

fun Data.monthTx(ym: Int): List<Txn> = txns.filter { ymOf(it.date) == ym }
fun List<Txn>.expense(): Double = filter { it.type == "expense" }.sumOf { it.amount }
fun List<Txn>.income(): Double = filter { it.type == "income" }.sumOf { it.amount }
fun List<Txn>.byCategory(): List<Pair<String, Double>> =
    filter { it.type == "expense" }.groupBy { it.category }
        .map { (k, v) -> k to v.sumOf { x -> x.amount } }.sortedByDescending { it.second }

fun billDue(b: Bill, ym: Int): Long {
    val c = Calendar.getInstance(); c.clear()
    c.set(ym / 12, ym % 12, min(b.day, daysIn(ym)), 9, 0, 0)
    return c.timeInMillis
}
fun nextDueYm(b: Bill): Int = if (b.lastPaid == nowYm()) nowYm() + 1 else nowYm()
fun nextDue(b: Bill): Long = billDue(b, nextDueYm(b))

// =====================  ADVICE ENGINE  =====================

fun monthAdvice(d: Data, ym: Int): List<Advice> {
    val out = mutableListOf<Advice>()
    val tx = d.monthTx(ym)
    val exp = tx.filter { it.type == "expense" }
    val spent = exp.sumOf { it.amount }
    val income = tx.income()
    if (exp.isEmpty()) {
        out.add(Advice("📝", "Nothing logged yet", "Add your expenses as you spend. The more you log, the smarter these insights get.", 0))
        return out
    }
    val isCur = ym == nowYm()
    val days = daysIn(ym)
    val elapsed = if (isCur) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) else days
    val left = days - elapsed
    val b = d.budget
    if (b > 0) {
        if (spent > b) {
            out.add(Advice("🚨", "Over budget", "You are ${money(spent - b)} over your ${money(b)} budget." + (if (isCur && left > 0) " Try a no-spend stretch for the last $left days." else ""), 2))
        } else if (isCur) {
            val used = spent / b
            val time = elapsed.toDouble() / days
            val perDay = money((b - spent) / (left + 1))
            if (used > time + 0.15) {
                out.add(Advice("⚠️", "Spending is ahead of schedule", "You've used ${(used * 100).roundToInt()}% of your budget but only ${(time * 100).roundToInt()}% of the month has passed. Aim for about $perDay a day from here.", 2))
            } else if (used < time - 0.10) {
                out.add(Advice("✅", "Nicely on track", "Only ${(used * 100).roundToInt()}% of your budget is used. You can spend about $perDay a day and still finish within budget.", 1))
            }
            if (elapsed >= 5) {
                val proj = spent / elapsed * days
                if (proj > b) out.add(Advice("📈", "Projected overspend", "At this pace you'll spend about ${money(proj)}, which is ${money(proj - b)} over budget.", 2))
            }
        } else {
            out.add(Advice("✅", "Within budget", "You finished ${money(b - spent)} under budget. Well done!", 1))
        }
    } else {
        out.add(Advice("🎯", "Set a monthly budget", "Add a budget so I can track your pace and warn you before you overspend.", 0))
    }
    d.catBudgets.forEach { (c, lim) ->
        if (lim > 0) {
            val s = exp.filter { it.category == c }.sumOf { it.amount }
            if (s > lim) out.add(Advice("🔔", "$c is over its limit", "${money(s)} spent against a ${money(lim)} limit.", 2))
        }
    }
    val cats = tx.byCategory()
    val top = cats.firstOrNull()
    if (top != null && cats.size > 1) {
        val share = top.second / spent
        if (share > 0.35) {
            out.add(Advice("🔍", "${top.first} leads your spending", "${(share * 100).roundToInt()}% of this month (${money(top.second)}) went to ${top.first}. Trimming 10% would save ${money(top.second * 0.1)}.", 0))
        }
    }
    val prev = d.monthTx(ym - 1).filter { it.type == "expense" }
    if (prev.isNotEmpty()) {
        val prevMap = prev.groupBy { it.category }.mapValues { e -> e.value.sumOf { x -> x.amount } }
        cats.filter { (c, v) -> val p = prevMap[c] ?: 0.0; p > 0 && v > p * 1.25 && v - p > 500 }.take(2).forEach { (c, v) ->
            val p = prevMap[c] ?: 0.0
            out.add(Advice("📊", "$c is up ${((v / p - 1) * 100).roundToInt()}%", "${money(v)} this month vs ${money(p)} last month.", 2))
        }
        val prevTotal = prev.sumOf { it.amount }
        if (!isCur && spent < prevTotal * 0.9) {
            out.add(Advice("🎉", "Spending is down", "${money(prevTotal - spent)} less than last month.", 1))
        }
    }
    if (income > 0) {
        val rate = (income - spent) / income
        if (rate < 0) {
            out.add(Advice("🚨", "Spending exceeds income", "You spent ${money(spent - income)} more than you earned this month.", 2))
        } else if (rate < 0.10) {
            out.add(Advice("💰", "Low savings rate", "You're saving ${(rate * 100).roundToInt()}% of your income. A common target is 20%, about ${money(income * 0.2)} a month.", 2))
        } else if (rate >= 0.20) {
            out.add(Advice("🏆", "Great savings rate", "You're saving ${(rate * 100).roundToInt()}% of your income. Consider moving some into a savings goal.", 1))
        }
    } else {
        out.add(Advice("💼", "Add your income", "Log your salary or other income to see your savings rate.", 0))
    }
    exp.groupBy { it.category }.forEach { (c, l) ->
        if (l.size >= 10) {
            val tot = l.sumOf { it.amount }
            val avg = tot / l.size
            if (avg < 400) out.add(Advice("☕", "Small $c spends add up", "${l.size} purchases averaging ${money(avg)} total ${money(tot)}. Skipping 3 a week would save about ${money(avg * 12)} a month.", 0))
        }
    }
    val big = exp.maxByOrNull { it.amount }
    if (big != null) {
        out.add(Advice("🧾", "Biggest expense", "${money(big.amount)} on ${big.note.ifBlank { big.category }} (${fmtShort(big.date)}).", 0))
    }
    return out.take(8)
}

fun yearAdvice(d: Data, yr: Int): List<Advice> {
    val out = mutableListOf<Advice>()
    val months = (0..11).map { d.monthTx(yr * 12 + it) }
    val exps = months.map { it.expense() }
    val incs = months.map { it.income() }
    val totalExp = exps.sum()
    val totalInc = incs.sum()
    if (totalExp == 0.0 && totalInc == 0.0) {
        out.add(Advice("📝", "No data for $yr", "Once you log transactions, yearly insights will appear here.", 0))
        return out
    }
    if (totalInc > 0) {
        val rate = (totalInc - totalExp) / totalInc
        if (rate >= 0.20) out.add(Advice("🏆", "Strong year of saving", "You saved ${(rate * 100).roundToInt()}% of your income (${money(totalInc - totalExp)}).", 1))
        else if (rate >= 0) out.add(Advice("💰", "Room to save more", "You saved ${(rate * 100).roundToInt()}% of income this year. Raising it toward 20% would add about ${money(totalInc * 0.2 - (totalInc - totalExp))}.", 2))
        else out.add(Advice("🚨", "Spent more than you earned", "Spending was ${money(totalExp - totalInc)} above income this year.", 2))
    }
    val maxIdx = exps.indices.maxByOrNull { exps[it] }
    if (maxIdx != null && exps[maxIdx] > 0) {
        out.add(Advice("📅", "Costliest month: ${monthName(yr * 12 + maxIdx)}", "You spent ${money(exps[maxIdx])}. Check what drove it and plan ahead next time.", 0))
    }
    val nz = exps.filter { it > 0 }
    if (nz.size >= 6) {
        val last3 = nz.takeLast(3).average()
        val prev3 = nz.dropLast(3).takeLast(3).average()
        if (last3 > prev3 * 1.15) out.add(Advice("📈", "Spending is trending up", "Your last 3 months average ${money(last3)}, up from ${money(prev3)} before.", 2))
        else if (last3 < prev3 * 0.85) out.add(Advice("📉", "Spending is trending down", "Your last 3 months average ${money(last3)}, down from ${money(prev3)} before.", 1))
    }
    val cats = months.flatten().byCategory()
    val top = cats.firstOrNull()
    if (top != null && totalExp > 0) {
        out.add(Advice("🔍", "${top.first} is your biggest category", "${pct(top.second, totalExp)}% of the year's spending (${money(top.second)}).", 0))
    }
    return out
}

// =====================  THEME  =====================

class Pal(
    val dark: Boolean, val bg: Color, val card: Color, val text: Color, val mute: Color, val line: Color,
    val primary: Color, val primarySoft: Color, val income: Color, val expense: Color, val warn: Color, val accent: Color
)
val LightPal = Pal(
    false, Color(0xFFF3F6FB), Color.White, Color(0xFF0F172A), Color(0xFF64748B), Color(0xFFE5E9F2),
    Color(0xFF0E9F6E), Color(0xFFE0F5EC), Color(0xFF12A150), Color(0xFFF2545B), Color(0xFFF59E0B), Color(0xFF6366F1)
)
val DarkPal = Pal(
    true, Color(0xFF0A101C), Color(0xFF131C2E), Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF223049),
    Color(0xFF2DD4A0), Color(0xFF12332F), Color(0xFF34D399), Color(0xFFFB7185), Color(0xFFFBBF24), Color(0xFF818CF8)
)
val LocalPal = compositionLocalOf { LightPal }

@Composable
fun T(
    text: String, size: Int = 14, color: Color = LocalPal.current.text, bold: Boolean = false,
    weight: FontWeight? = null, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size.sp,
        fontWeight = weight ?: (if (bold) FontWeight.Bold else FontWeight.Normal),
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, pad: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth()
            .shadow(if (p.dark) 0.dp else 3.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x1A0F172A))
            .clip(shape).background(p.card)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(pad.dp),
        content = content
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).background(if (selected) p.primary else Color.Transparent)
            .border(1.dp, if (selected) p.primary else p.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    ) { T(label, 13, if (selected) Color.White else p.text, weight = FontWeight.Medium) }
}

@Composable
fun Bar(frac: Float, color: Color, h: Int = 8, track: Color = LocalPal.current.line) {
    Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun MonthBar(ym: Int, onYm: (Int) -> Unit) {
    val p = LocalPal.current
    val canNext = ym < nowYm()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onYm(ym - 1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Previous month", tint = p.text) }
        T(ymLabel(ym), 16, bold = true)
        IconButton(onClick = { if (canNext) onYm(ym + 1) }, enabled = canNext) {
            Icon(Icons.Default.KeyboardArrowRight, "Next month", tint = if (canNext) p.text else p.line)
        }
    }
}

@Composable
fun Heading(text: String, sub: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(text, 17, bold = true, modifier = Modifier.weight(1f))
        if (sub.isNotEmpty()) T(sub, 12, LocalPal.current.mute)
    }
}

@Composable
fun EmptyNote(msg: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        T(msg, 14, LocalPal.current.mute, align = TextAlign.Center)
    }
}

// =====================  CHARTS (Canvas)  =====================

@Composable
fun DailyBars(values: List<Double>, todayIdx: Int, line: Double, color: Color) {
    val p = LocalPal.current
    val maxV = max(values.maxOrNull() ?: 0.0, line).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val n = values.size
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = max((v / maxV).toFloat() * size.height, 3.dp.toPx())
            drawRoundRect(
                color = if (i == todayIdx) color else color.copy(alpha = 0.45f),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2, bw / 2)
            )
        }
        if (line > 0) {
            val y = size.height - (line / maxV).toFloat() * size.height
            drawLine(p.mute, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}

@Composable
fun Donut(parts: List<Pair<Color, Double>>, top: String, bottom: String) {
    val p = LocalPal.current
    val total = parts.sumOf { it.second }
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 20.dp.toPx()
            val dia = size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            drawArc(p.line, 0f, 360f, false, topLeft = tl, size = Size(dia, dia), style = Stroke(sw))
            var start = -90f
            if (total > 0) {
                parts.forEach { (c, v) ->
                    val sweep = (v / total * 360).toFloat()
                    drawArc(c, start, max(sweep - 2f, 0.5f), false, topLeft = tl, size = Size(dia, dia), style = Stroke(sw))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(top, 11, p.mute)
            T(bottom, 17, bold = true)
        }
    }
}

@Composable
fun YearBars(inc: List<Double>, exp: List<Double>, incColor: Color, expColor: Color) {
    val maxV = max(inc.maxOrNull() ?: 0.0, exp.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val gw = size.width / 12
        val bw = gw * 0.30f
        for (i in 0 until 12) {
            val hi = max((inc[i] / maxV).toFloat() * size.height, if (inc[i] > 0) 3.dp.toPx() else 0f)
            val he = max((exp[i] / maxV).toFloat() * size.height, if (exp[i] > 0) 3.dp.toPx() else 0f)
            val x0 = i * gw + gw * 0.18f
            drawRoundRect(incColor, Offset(x0, size.height - hi), Size(bw, hi), CornerRadius(bw / 2, bw / 2))
            drawRoundRect(expColor, Offset(x0 + bw + 2.dp.toPx(), size.height - he), Size(bw, he), CornerRadius(bw / 2, bw / 2))
        }
    }
}

@Composable
fun Ring(frac: Float, color: Color, emoji: String) {
    val p = LocalPal.current
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 7.dp.toPx()
            val dia = size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            drawArc(p.line, 0f, 360f, false, topLeft = tl, size = Size(dia, dia), style = Stroke(sw))
            drawArc(color, -90f, 360f * frac.coerceIn(0f, 1f), false, topLeft = tl, size = Size(dia, dia), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(emoji, fontSize = 22.sp)
    }
}

// =====================  ACTIVITY + APP  =====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BudgetApp() }
    }
}

@Composable
fun BudgetApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getInt("theme", 0)) }
    var daily by remember { mutableStateOf(prefs.getBoolean("daily", false)) }
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (theme) { 1 -> false; 2 -> true; else -> sysDark }
    val pal = if (dark) DarkPal else LightPal

    var data by remember { mutableStateOf(Store.load(ctx)) }
    var tab by remember { mutableStateOf(0) }
    var ym by remember { mutableStateOf(nowYm()) }
    var txnEdit by remember { mutableStateOf<Txn?>(null) }
    var goalEdit by remember { mutableStateOf<Goal?>(null) }
    var addMoney by remember { mutableStateOf<Goal?>(null) }
    var billEdit by remember { mutableStateOf<Bill?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }

    fun commit(d: Data) { data = d; Store.save(ctx, d) }

    val act = ctx as ComponentActivity
    SideEffect {
        act.window.statusBarColor = pal.bg.toArgb()
        act.window.navigationBarColor = pal.card.toArgb()
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        Reminders.scheduleAll(ctx, data)
    }

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.toJson(data).toByteArray()) }
            Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
        }
    }
    val csvL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.csv(data).toByteArray()) }
            Toast.makeText(ctx, "CSV exported", Toast.LENGTH_SHORT).show()
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            val d = if (txt != null) Store.parse(txt) else null
            if (d != null) {
                data.bills.forEach { Reminders.cancelBill(ctx, it) }
                commit(d)
                Reminders.scheduleAll(ctx, d)
                Toast.makeText(ctx, "Restored ${d.txns.size} transactions", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(ctx, "That file isn't a valid backup", Toast.LENGTH_SHORT).show()
        }
    }

    fun payBill(b: Bill) {
        val now = System.currentTimeMillis()
        val t = Txn(now, "expense", b.amount, b.category, b.name, now)
        val nb = b.copy(lastPaid = nowYm())
        commit(data.copy(txns = data.txns + t, bills = data.bills.map { if (it.id == b.id) nb else it }))
        Reminders.scheduleBill(ctx, nb)
    }

    val scheme = if (dark)
        darkColorScheme(primary = pal.primary, onPrimary = Color(0xFF00382A), background = pal.bg, surface = pal.card, onSurface = pal.text,
            onBackground = pal.text, onSurfaceVariant = pal.mute, outline = pal.line, outlineVariant = pal.line, surfaceVariant = pal.line)
    else
        lightColorScheme(primary = pal.primary, onPrimary = Color.White, background = pal.bg, surface = pal.card, onSurface = pal.text,
            onBackground = pal.text, onSurfaceVariant = pal.mute, outline = pal.line, outlineVariant = pal.line, surfaceVariant = pal.line)

    val tabs = listOf("Home", "Activity", "Insights", "Goals", "Bills")
    val tabIcons = listOf(Icons.Default.Home, Icons.Default.List, Icons.Default.Info, Icons.Default.Favorite, Icons.Default.Notifications)

    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = scheme) {
            Scaffold(
                containerColor = pal.bg,
                bottomBar = {
                    NavigationBar(containerColor = pal.card) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(tabIcons[i], t) },
                                label = { Text(t, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = pal.primary, selectedTextColor = pal.primary, indicatorColor = pal.primarySoft,
                                    unselectedIconColor = pal.mute, unselectedTextColor = pal.mute
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab != 2) {
                        FloatingActionButton(
                            onClick = {
                                when (tab) {
                                    3 -> goalEdit = Goal(0L, "", "🎯", 0.0, 0.0, 0L)
                                    4 -> billEdit = Bill(0L, "", 0.0, 1, "Bills", -1)
                                    else -> txnEdit = Txn(0L, "expense", 0.0, "Food", "", System.currentTimeMillis())
                                }
                            },
                            containerColor = pal.primary, contentColor = Color.White, shape = CircleShape
                        ) { Icon(Icons.Default.Add, "Add") }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        0 -> HomeScreen(data, ym, { ym = it }, { tab = 1 }, { txnEdit = it }, { showBudget = true }, { showSettings = true }, { tab = 4 })
                        1 -> ActivityScreen(data, ym, { ym = it }, { txnEdit = it })
                        2 -> InsightsScreen(data, ym, { ym = it }) { m -> ym = m; tab = 1 }
                        3 -> GoalsScreen(data, { goalEdit = it }, { addMoney = it })
                        else -> BillsScreen(data, { billEdit = it }, { payBill(it) })
                    }
                }
            }

            txnEdit?.let { start ->
                TxnDialog(
                    start, start.id != 0L,
                    onSave = { t ->
                        val nt = if (t.id == 0L) t.copy(id = System.currentTimeMillis()) else t
                        commit(data.copy(txns = data.txns.upsertBy(nt) { it.id }))
                        txnEdit = null
                    },
                    onDelete = { commit(data.copy(txns = data.txns.filter { it.id != start.id })); txnEdit = null },
                    onDismiss = { txnEdit = null }
                )
            }
            goalEdit?.let { start ->
                GoalDialog(
                    start, start.id != 0L,
                    onSave = { g ->
                        val ng = if (g.id == 0L) g.copy(id = System.currentTimeMillis()) else g
                        commit(data.copy(goals = data.goals.upsertBy(ng) { it.id }))
                        goalEdit = null
                    },
                    onDelete = { commit(data.copy(goals = data.goals.filter { it.id != start.id })); goalEdit = null },
                    onDismiss = { goalEdit = null }
                )
            }
            addMoney?.let { g ->
                AddMoneyDialog(g, onApply = { delta ->
                    val ng = g.copy(saved = max(0.0, g.saved + delta))
                    commit(data.copy(goals = data.goals.map { if (it.id == g.id) ng else it }))
                    addMoney = null
                }, onDismiss = { addMoney = null })
            }
            billEdit?.let { start ->
                BillDialog(
                    start, start.id != 0L,
                    onSave = { b ->
                        val nb = if (b.id == 0L) b.copy(id = System.currentTimeMillis()) else b
                        commit(data.copy(bills = data.bills.upsertBy(nb) { it.id }))
                        Reminders.scheduleBill(ctx, nb)
                        billEdit = null
                    },
                    onDelete = {
                        Reminders.cancelBill(ctx, start)
                        commit(data.copy(bills = data.bills.filter { it.id != start.id }))
                        billEdit = null
                    },
                    onDismiss = { billEdit = null }
                )
            }
            if (showBudget) {
                BudgetDialog(data, onSave = { total, cats -> commit(data.copy(budget = total, catBudgets = cats)); showBudget = false }, onDismiss = { showBudget = false })
            }
            if (showSettings) {
                SettingsDialog(
                    theme = theme, daily = daily,
                    onTheme = { theme = it; prefs.edit().putInt("theme", it).apply() },
                    onDaily = { on ->
                        daily = on
                        prefs.edit().putBoolean("daily", on).apply()
                        Reminders.setDaily(ctx, on)
                    },
                    onBackup = { showSettings = false; exportL.launch("budget-backup.json") },
                    onRestore = { showSettings = false; importL.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onCsv = { showSettings = false; csvL.launch("budget-transactions.csv") },
                    onDismiss = { showSettings = false }
                )
            }
        }
    }
}

// =====================  HOME  =====================

@Composable
fun TxnRow(t: Txn, showDate: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val c = catOf(t.category, t.type)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(c.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(t.note.ifBlank { t.category }, 15, weight = FontWeight.Medium, maxLines = 1)
            T(t.category + (if (showDate) " · " + fmtShort(t.date) else ""), 12, p.mute, maxLines = 1)
        }
        T((if (t.type == "income") "+" else "-") + money(t.amount), 15, if (t.type == "income") p.income else p.text, bold = true)
    }
}

@Composable
fun StatCard(modifier: Modifier, label: String, value: String, color: Color, sub: String = "") {
    val p = LocalPal.current
    Panel(modifier, pad = 12) {
        T(label, 12, p.mute)
        Spacer(Modifier.height(4.dp))
        T(value, 16, color, bold = true, maxLines = 1)
        if (sub.isNotEmpty()) T(sub, 11, p.mute)
    }
}

@Composable
fun HomeScreen(
    d: Data, ym: Int, onYm: (Int) -> Unit, onSeeAll: () -> Unit, onEdit: (Txn) -> Unit,
    onBudget: () -> Unit, onSettings: () -> Unit, onBills: () -> Unit
) {
    val p = LocalPal.current
    val tx = d.monthTx(ym)
    val spent = tx.expense()
    val inc = tx.income()
    val isCur = ym == nowYm()
    val days = daysIn(ym)
    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val daysLeft = if (isCur) days - today + 1 else 0
    val budget = d.budget
    val left = budget - spent
    val ratio = if (budget > 0) (spent / budget).toFloat() else 0f
    val barColor = when { ratio > 1f -> Color(0xFFFFB4B4); ratio > 0.8f -> Color(0xFFFFE08A); else -> Color.White }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"

    val daily = DoubleArray(days)
    tx.filter { it.type == "expense" }.forEach { daily[min(dayOfMonth(it.date), days) - 1] += it.amount }
    val cats = tx.byCategory()
    val upcoming = d.bills.filter { it.lastPaid != nowYm() }.sortedBy { nextDue(it) }.take(3)
    val recent = tx.sortedByDescending { it.date }.take(5)

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(greeting, 13, p.mute)
                    T("My Budget", 26, bold = true)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = p.text) }
            }
        }
        item { MonthBar(ym, onYm) }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0A7B5F), Color(0xFF17B88E))))
            ) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = (-60).dp).size(180.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
                Box(Modifier.align(Alignment.BottomStart).offset(x = (-40).dp, y = 50.dp).size(130.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
                Column(Modifier.padding(22.dp)) {
                    if (budget > 0) {
                        T(if (left >= 0) "Left to spend" else "Over budget by", 13, Color.White.copy(alpha = 0.8f))
                        T(money(abs(left)), 38, Color.White, bold = true)
                        Spacer(Modifier.height(12.dp))
                        Bar(ratio, barColor, 10, Color.White.copy(alpha = 0.25f))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            T("Spent ${money(spent)}", 13, Color.White, modifier = Modifier.weight(1f))
                            T("Budget ${money(budget)}", 13, Color.White.copy(alpha = 0.85f))
                        }
                        Spacer(Modifier.height(6.dp))
                        val note = if (isCur && left > 0) "About ${money(left / daysLeft)} a day for the next $daysLeft days"
                        else if (!isCur && left >= 0) "Finished ${money(left)} under budget"
                        else if (!isCur) "Finished ${money(-left)} over budget" else "Time to slow down this month"
                        T(note, 12, Color.White.copy(alpha = 0.85f))
                    } else {
                        T("Net this month", 13, Color.White.copy(alpha = 0.8f))
                        T(money(inc - spent), 38, Color.White, bold = true)
                        Spacer(Modifier.height(6.dp))
                        T("Set a monthly budget to see what's left to spend.", 12, Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                            .clickable(onClick = onBudget).padding(horizontal = 14.dp, vertical = 7.dp)
                    ) { T(if (budget > 0) "Edit budgets" else "Set budget", 13, Color.White, weight = FontWeight.SemiBold) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f), "Income", money(inc), p.income)
                StatCard(Modifier.weight(1f), "Spent", money(spent), p.expense)
                StatCard(Modifier.weight(1f), "Saved", money(inc - spent), if (inc - spent >= 0) p.primary else p.expense, if (inc > 0) "${pct(inc - spent, inc)}% of income" else "")
            }
        }
        item {
            Panel {
                Heading("Daily spending", if (spent > 0) "avg ${money(spent / (if (isCur) today else days))}/day" else "")
                Spacer(Modifier.height(12.dp))
                DailyBars(daily.toList(), if (isCur) today - 1 else -1, if (budget > 0) budget / days else 0.0, p.primary)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(1, 8, 15, 22, days).forEach { T("$it", 10, p.mute) }
                }
                if (budget > 0) { Spacer(Modifier.height(6.dp)); T("Dashed line = your daily budget (${money(budget / days)})", 11, p.mute) }
            }
        }
        item {
            Panel {
                Heading("Where your money went")
                Spacer(Modifier.height(10.dp))
                if (cats.isEmpty()) EmptyNote("No expenses yet this month.\nTap + to add one.")
                cats.take(5).forEach { (name, v) ->
                    val c = catOf(name, "expense")
                    val lim = d.catBudgets[name] ?: 0.0
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            T(name, 14, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            T(money(v), 14, bold = true)
                        }
                        Spacer(Modifier.height(6.dp))
                        if (lim > 0) {
                            Bar((v / lim).toFloat(), if (v > lim) p.expense else c.color, 6)
                            Spacer(Modifier.height(3.dp))
                            T("${money(v)} of ${money(lim)} limit", 11, if (v > lim) p.expense else p.mute)
                        } else Bar((v / spent).toFloat(), c.color, 6)
                    }
                }
            }
        }
        if (upcoming.isNotEmpty()) {
            item {
                Panel(onClick = onBills) {
                    Heading("Upcoming bills", "See all")
                    Spacer(Modifier.height(6.dp))
                    upcoming.forEach { b ->
                        val due = nextDue(b)
                        val late = due < todayStart()
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(catOf(b.category, "expense").emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                T(b.name, 14, weight = FontWeight.Medium)
                                T((if (late) "Overdue · " else "Due ") + fmtShort(due), 12, if (late) p.expense else p.mute)
                            }
                            T(money(b.amount), 14, bold = true)
                        }
                    }
                }
            }
        }
        item {
            Panel {
                Heading("Recent transactions", "")
                if (recent.isEmpty()) EmptyNote("Nothing here yet.")
                recent.forEach { t -> TxnRow(t, true) { onEdit(t) } }
                if (tx.size > 5) {
                    TextButton(onClick = onSeeAll, modifier = Modifier.align(Alignment.CenterHorizontally)) { T("See all ${tx.size}", 14, p.primary, bold = true) }
                }
            }
        }
    }
}

// =====================  ACTIVITY  =====================

@Composable
fun ActivityScreen(d: Data, ym: Int, onYm: (Int) -> Unit, onEdit: (Txn) -> Unit) {
    val p = LocalPal.current
    var q by remember { mutableStateOf("") }
    var f by remember { mutableStateOf(0) }
    val all = d.monthTx(ym)
    val list = all.filter {
        (f == 0 || (f == 1 && it.type == "expense") || (f == 2 && it.type == "income")) &&
            (q.isBlank() || it.note.contains(q, true) || it.category.contains(q, true))
    }
    val groups = list.groupBy { dayStart(it.date) }.toList().sortedByDescending { it.first }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { T("Activity", 26, bold = true) }
        item { MonthBar(ym, onYm) }
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp), placeholder = { Text("Search notes or categories") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Expenses", "Income").forEachIndexed { i, l -> Chip(l, f == i) { f = i } }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                T("${list.size} transactions", 13, p.mute, modifier = Modifier.weight(1f))
                T("Net " + money(list.income() - list.expense()), 13, p.mute)
            }
        }
        if (groups.isEmpty()) item { Panel { EmptyNote("No transactions found.") } }
        items(groups) { (day, txs) ->
            Panel(pad = 14) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(fmtDayHeader(day), 14, p.mute, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val s = txs.expense()
                    if (s > 0) T("-" + money(s), 13, p.mute)
                }
                txs.sortedByDescending { it.date }.forEach { t -> TxnRow(t, false) { onEdit(t) } }
            }
        }
    }
}

// =====================  INSIGHTS  =====================

@Composable
fun AdviceCard(a: Advice) {
    val p = LocalPal.current
    val tint = when (a.tone) { 1 -> p.income; 2 -> p.warn; else -> p.accent }
    Panel(pad = 14) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(a.emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(a.title, 15, bold = true)
                Spacer(Modifier.height(2.dp))
                T(a.text, 13, p.mute)
            }
        }
    }
}

@Composable
fun InsightsScreen(d: Data, ym: Int, onYm: (Int) -> Unit, onOpenMonth: (Int) -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var yr by remember { mutableStateOf(ym / 12) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { T("Insights", 26, bold = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Monthly", mode == 0) { mode = 0 }
                Chip("Yearly", mode == 1) { mode = 1 }
            }
        }
        if (mode == 0) {
            item { MonthBar(ym, onYm) }
            monthInsights(d, ym)
        } else {
            item {
                val p = LocalPal.current
                val canNext = yr < nowYm() / 12
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { yr -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Previous year", tint = p.text) }
                    T("$yr", 16, bold = true)
                    IconButton(onClick = { if (canNext) yr += 1 }, enabled = canNext) {
                        Icon(Icons.Default.KeyboardArrowRight, "Next year", tint = if (canNext) p.text else p.line)
                    }
                }
            }
            yearInsights(d, yr, onOpenMonth)
        }
    }
}

fun LazyListScope.monthInsights(d: Data, ym: Int) {
    val tx = d.monthTx(ym)
    val spent = tx.expense()
    val cats = tx.byCategory()
    val prev = d.monthTx(ym - 1)
    val prevSpent = prev.expense()
    val prevMap = prev.byCategory().toMap()

    item {
        val p = LocalPal.current
        Panel {
            Heading("Spending by category", money(spent))
            Spacer(Modifier.height(14.dp))
            if (cats.isEmpty()) EmptyNote("No expenses this month.") else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(cats.map { catOf(it.first, "expense").color to it.second }, "Total", compact(spent))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        cats.take(6).forEach { (n, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(catOf(n, "expense").color))
                                Spacer(Modifier.width(8.dp))
                                T("$n  ${pct(v, spent)}%", 13, p.text, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
    item {
        val p = LocalPal.current
        Panel {
            Heading("Compared with ${ymShort(ym - 1)}")
            Spacer(Modifier.height(8.dp))
            if (prevSpent <= 0) T("No spending recorded last month to compare with.", 13, p.mute) else {
                val delta = spent - prevSpent
                T((if (delta >= 0) "▲ " else "▼ ") + money(abs(delta)) + " (${abs(pct(delta, prevSpent))}%) " + (if (delta >= 0) "more" else "less") + " than last month",
                    14, if (delta > 0) p.expense else p.income, bold = true)
                Spacer(Modifier.height(8.dp))
                val names = (cats.map { it.first } + prevMap.keys).distinct()
                val curMap = cats.toMap()
                names.map { it to ((curMap[it] ?: 0.0) - (prevMap[it] ?: 0.0)) }
                    .sortedByDescending { abs(it.second) }.take(5).forEach { (n, diff) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(catOf(n, "expense").emoji, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            T(n, 14, modifier = Modifier.weight(1f))
                            T((if (diff >= 0) "▲ " else "▼ ") + money(abs(diff)), 13, if (diff > 0) p.expense else p.income, bold = true)
                        }
                    }
            }
        }
    }
    monthAdvice(d, ym).forEach { a -> item { AdviceCard(a) } }
    val big = tx.filter { it.type == "expense" }.sortedByDescending { it.amount }.take(3)
    if (big.isNotEmpty()) {
        item {
            Panel {
                Heading("Biggest expenses")
                big.forEach { t -> TxnRow(t, true) { } }
            }
        }
    }
}

fun LazyListScope.yearInsights(d: Data, yr: Int, onOpenMonth: (Int) -> Unit) {
    val months = (0..11).map { d.monthTx(yr * 12 + it) }
    val exps = months.map { it.expense() }
    val incs = months.map { it.income() }
    val totalExp = exps.sum()
    val totalInc = incs.sum()
    val active = exps.count { it > 0 }
    val cats = months.flatten().byCategory()
    val letters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

    item {
        val p = LocalPal.current
        Panel {
            Heading("$yr at a glance")
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) { T("Income", 12, p.mute); T(money(totalInc), 17, p.income, bold = true) }
                Column(Modifier.weight(1f)) { T("Spent", 12, p.mute); T(money(totalExp), 17, p.expense, bold = true) }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    T("Saved", 12, p.mute)
                    T(money(totalInc - totalExp), 17, if (totalInc - totalExp >= 0) p.primary else p.expense, bold = true)
                    if (totalInc > 0) T("${pct(totalInc - totalExp, totalInc)}% of income", 11, p.mute)
                }
                Column(Modifier.weight(1f)) {
                    T("Average a month", 12, p.mute)
                    T(if (active > 0) money(totalExp / active) else "₹0", 17, bold = true)
                }
            }
        }
    }
    item {
        val p = LocalPal.current
        Panel {
            Heading("Income vs spending")
            Spacer(Modifier.height(12.dp))
            YearBars(incs, exps, p.income, p.expense)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) { letters.forEach { T(it, 10, p.mute, modifier = Modifier.weight(1f), align = TextAlign.Center) } }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(p.income)); Spacer(Modifier.width(6.dp)); T("Income", 12, p.mute) }
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(p.expense)); Spacer(Modifier.width(6.dp)); T("Spending", 12, p.mute) }
            }
        }
    }
    yearAdvice(d, yr).forEach { a -> item { AdviceCard(a) } }
    item {
        val p = LocalPal.current
        Panel {
            Heading("Top categories", money(totalExp))
            Spacer(Modifier.height(8.dp))
            if (cats.isEmpty()) EmptyNote("No expenses this year.")
            cats.take(6).forEach { (n, v) ->
                val c = catOf(n, "expense")
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        T(n, 14, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        T(money(v), 14, bold = true)
                    }
                    Spacer(Modifier.height(5.dp))
                    Bar((v / totalExp).toFloat(), c.color, 6)
                }
            }
        }
    }
    item {
        val p = LocalPal.current
        Panel {
            Heading("Month by month", "tap a month")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                T("Month", 11, p.mute, modifier = Modifier.weight(1.2f))
                T("Income", 11, p.mute, modifier = Modifier.weight(1f), align = TextAlign.End)
                T("Spent", 11, p.mute, modifier = Modifier.weight(1f), align = TextAlign.End)
                T("Saved", 11, p.mute, modifier = Modifier.weight(1f), align = TextAlign.End)
            }
            var any = false
            for (m in 11 downTo 0) {
                if (months[m].isEmpty()) continue
                any = true
                val net = incs[m] - exps[m]
                Row(Modifier.fillMaxWidth().clickable { onOpenMonth(yr * 12 + m) }.padding(vertical = 9.dp)) {
                    T(monthName(yr * 12 + m).take(3), 14, weight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
                    T("₹" + compact(incs[m]), 13, p.income, modifier = Modifier.weight(1f), align = TextAlign.End)
                    T("₹" + compact(exps[m]), 13, modifier = Modifier.weight(1f), align = TextAlign.End)
                    T((if (net < 0) "-" else "") + "₹" + compact(abs(net)), 13, if (net >= 0) p.primary else p.expense, bold = true, modifier = Modifier.weight(1f), align = TextAlign.End)
                }
            }
            if (!any) EmptyNote("No data for $yr yet.")
        }
    }
}

// =====================  GOALS  =====================

@Composable
fun GoalsScreen(d: Data, onEdit: (Goal) -> Unit, onAddMoney: (Goal) -> Unit) {
    val p = LocalPal.current
    val totalT = d.goals.sumOf { it.target }
    val totalS = d.goals.sumOf { min(it.saved, it.target) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { T("Savings goals", 26, bold = true) }
        if (d.goals.isNotEmpty()) {
            item {
                Panel {
                    T("Saved so far", 13, p.mute)
                    T(money(totalS), 28, bold = true)
                    Spacer(Modifier.height(8.dp))
                    Bar((totalS / max(totalT, 1.0)).toFloat(), p.primary, 8)
                    Spacer(Modifier.height(6.dp))
                    T("of ${money(totalT)} across ${d.goals.size} goal${if (d.goals.size == 1) "" else "s"}", 12, p.mute)
                }
            }
        } else item { Panel { EmptyNote("Save for a trip, a gadget or anything you want.\nTap + to create your first goal.") } }
        items(d.goals.sortedBy { if (it.deadline == 0L) Long.MAX_VALUE else it.deadline }, key = { it.id }) { g ->
            val frac = if (g.target > 0) (g.saved / g.target).toFloat() else 0f
            val reached = g.saved >= g.target && g.target > 0
            Panel(onClick = { onEdit(g) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(frac, if (reached) p.income else p.primary, g.icon)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T(g.name, 17, bold = true, maxLines = 1)
                        T("${money(g.saved)} of ${money(g.target)}", 13, p.mute)
                        if (reached) T("Goal reached! 🎉", 13, p.income, bold = true)
                        else if (g.deadline > 0) {
                            val months = max(1.0, ceil((g.deadline - System.currentTimeMillis()) / (30.44 * DAY)))
                            val need = (g.target - g.saved) / months
                            val late = g.deadline < System.currentTimeMillis()
                            T(if (late) "Deadline passed · ${money(g.target - g.saved)} to go" else "Save ${money(need)}/month to finish by ${fmtShort(g.deadline)}", 12, if (late) p.expense else p.mute)
                        } else T("${money(g.target - g.saved)} to go", 12, p.mute)
                    }
                    T("${(frac * 100).roundToInt().coerceAtMost(100)}%", 16, bold = true)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.primarySoft)
                        .clickable { onAddMoney(g) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { T("Add or withdraw money", 14, p.primary, bold = true) }
            }
        }
    }
}

// =====================  BILLS  =====================

@Composable
fun BillsScreen(d: Data, onEdit: (Bill) -> Unit, onPay: (Bill) -> Unit) {
    val p = LocalPal.current
    val total = d.bills.sumOf { it.amount }
    val paid = d.bills.filter { it.lastPaid == nowYm() }.sumOf { it.amount }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { T("Bills & subscriptions", 24, bold = true) }
        if (d.bills.isNotEmpty()) {
            item {
                Panel {
                    T("This month", 13, p.mute)
                    T("${money(paid)} paid of ${money(total)}", 20, bold = true)
                    Spacer(Modifier.height(8.dp))
                    Bar((paid / max(total, 1.0)).toFloat(), p.primary, 8)
                    Spacer(Modifier.height(6.dp))
                    T("You'll get a reminder the day before and on the due date.", 12, p.mute)
                }
            }
        } else item { Panel { EmptyNote("Add rent, subscriptions, EMIs or any recurring bill.\nTap + and never miss a due date.") } }
        items(d.bills.sortedBy { nextDue(it) }, key = { it.id }) { b ->
            val isPaid = b.lastPaid == nowYm()
            val due = nextDue(b)
            val late = !isPaid && due < todayStart()
            val soon = !isPaid && !late && due - System.currentTimeMillis() < 3 * DAY
            val c = catOf(b.category, "expense")
            Panel(onClick = { onEdit(b) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(c.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Text(c.emoji, fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(b.name, 16, bold = true, maxLines = 1)
                        T(if (isPaid) "Paid · next on ${fmtShort(due)}" else (if (late) "Overdue since " else "Due ") + fmtShort(due),
                            12, if (late) p.expense else if (soon) p.warn else p.mute)
                    }
                    T(money(b.amount), 16, bold = true)
                }
                if (!isPaid) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.primary)
                            .clickable { onPay(b) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { T("Mark as paid", 14, Color.White, bold = true) }
                }
            }
        }
    }
}

// =====================  DIALOGS  =====================

@Composable
fun CatGrid(cats: List<Cat>, selected: String, onPick: (String) -> Unit) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val sel = c.name == selected
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.weight(1f).clip(shape).background(if (sel) c.color.copy(alpha = 0.18f) else p.bg)
                            .border(1.5.dp, if (sel) c.color else Color.Transparent, shape)
                            .clickable { onPick(c.name) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(c.emoji, fontSize = 20.sp)
                        T(c.name, 10, p.text, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun cleanNum(s: String): String = s.filter { it.isDigit() || it == '.' }.take(12)

@Composable
fun TxnDialog(start: Txn, exists: Boolean, onSave: (Txn) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    var type by remember { mutableStateOf(start.type) }
    var amount by remember { mutableStateOf(if (start.amount > 0) (if (start.amount % 1.0 == 0.0) start.amount.toLong().toString() else start.amount.toString()) else "") }
    var cat by remember { mutableStateOf(start.category) }
    var note by remember { mutableStateOf(start.note) }
    var date by remember { mutableStateOf(start.date) }
    var picker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.card,
        title = { T(if (exists) "Edit transaction" else "New transaction", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Expense", type == "expense") { type = "expense"; cat = ExpenseCats.first().name }
                    Chip("Income", type == "income") { type = "income"; cat = IncomeCats.first().name }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = cleanNum(it); error = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error,
                    prefix = { Text("₹ ", fontSize = 20.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    label = { Text(if (error) "Enter an amount" else "Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                CatGrid(if (type == "income") IncomeCats else ExpenseCats, cat) { cat = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Note (optional)") }
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isToday = dayStart(date) == todayStart()
                    val isYest = dayStart(date) == todayStart() - DAY
                    Chip("Today", isToday) { date = System.currentTimeMillis() }
                    Chip("Yesterday", isYest) { date = System.currentTimeMillis() - DAY }
                    Chip(if (!isToday && !isYest) fmtShort(date) else "Pick date", !isToday && !isYest) { picker = true }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                if (a <= 0) error = true else onSave(start.copy(type = type, amount = a, category = cat, note = note.trim(), date = date))
            }) { T("Save", 15, p.primary, bold = true) }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = onDelete) { T("Delete", 15, p.expense) }
                TextButton(onClick = onDismiss) { T("Cancel", 15, p.mute) }
            }
        }
    )
    if (picker) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { date = localFromUtcDate(it) }; picker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun GoalDialog(start: Goal, exists: Boolean, onSave: (Goal) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    var name by remember { mutableStateOf(start.name) }
    var icon by remember { mutableStateOf(start.icon) }
    var target by remember { mutableStateOf(if (start.target > 0) start.target.toLong().toString() else "") }
    var saved by remember { mutableStateOf(if (start.saved > 0) start.saved.toLong().toString() else "") }
    var deadline by remember { mutableStateOf(start.deadline) }
    var picker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.card,
        title = { T(if (exists) "Edit goal" else "New savings goal", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalIcons.forEach { e ->
                        val sel = e == icon
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (sel) p.primarySoft else p.bg)
                                .border(1.5.dp, if (sel) p.primary else Color.Transparent, CircleShape).clickable { icon = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = false }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = error, label = { Text(if (error) "Name and target are required" else "Goal name (e.g. Goa trip)") }
                )
                OutlinedTextField(
                    value = target, onValueChange = { target = cleanNum(it); error = false }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    prefix = { Text("₹ ") }, label = { Text("Target amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = saved, onValueChange = { saved = cleanNum(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    prefix = { Text("₹ ") }, label = { Text("Already saved") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                T("Deadline", 12, p.mute)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun inMonths(n: Int): Long { val c = Calendar.getInstance(); c.add(Calendar.MONTH, n); return dayStart(c.timeInMillis) }
                    Chip("None", deadline == 0L) { deadline = 0L }
                    Chip("3 months", deadline == inMonths(3)) { deadline = inMonths(3) }
                    Chip("6 months", deadline == inMonths(6)) { deadline = inMonths(6) }
                    Chip("1 year", deadline == inMonths(12)) { deadline = inMonths(12) }
                    Chip(if (deadline > 0 && deadline != inMonths(3) && deadline != inMonths(6) && deadline != inMonths(12)) fmtShort(deadline) else "Pick date",
                        deadline > 0 && deadline != inMonths(3) && deadline != inMonths(6) && deadline != inMonths(12)) { picker = true }
                }
                if (deadline > 0) T(fmtShort(deadline), 12, p.text)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = target.toDoubleOrNull() ?: 0.0
                if (name.isBlank() || t <= 0) error = true
                else onSave(start.copy(name = name.trim(), icon = icon, target = t, saved = saved.toDoubleOrNull() ?: 0.0, deadline = deadline))
            }) { T("Save", 15, p.primary, bold = true) }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = onDelete) { T("Delete", 15, p.expense) }
                TextButton(onClick = onDismiss) { T("Cancel", 15, p.mute) }
            }
        }
    )
    if (picker) {
        val st = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { deadline = dayStart(localFromUtcDate(it)) }; picker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }
        ) { DatePicker(state = st) }
    }
}

@Composable
fun AddMoneyDialog(g: Goal, onApply: (Double) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.card,
        title = { T("${g.icon} ${g.name}", 18, bold = true) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Saved ${money(g.saved)} of ${money(g.target)}", 13, p.mute)
                OutlinedTextField(
                    value = amount, onValueChange = { amount = cleanNum(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    prefix = { Text("₹ ") }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 5000).forEach { v -> Chip("+$v", false) { amount = v.toString() } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { val a = amount.toDoubleOrNull() ?: 0.0; if (a > 0) onApply(a) }) { T("Add", 15, p.primary, bold = true) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { val a = amount.toDoubleOrNull() ?: 0.0; if (a > 0) onApply(-a) }) { T("Withdraw", 15, p.expense) }
                TextButton(onClick = onDismiss) { T("Cancel", 15, p.mute) }
            }
        }
    )
}

@Composable
fun BillDialog(start: Bill, exists: Boolean, onSave: (Bill) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    var name by remember { mutableStateOf(start.name) }
    var amount by remember { mutableStateOf(if (start.amount > 0) start.amount.toLong().toString() else "") }
    var day by remember { mutableStateOf(start.day.toString()) }
    var cat by remember { mutableStateOf(start.category) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.card,
        title = { T(if (exists) "Edit bill" else "New recurring bill", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = false }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = error, label = { Text(if (error) "Fill name, amount and a day (1-31)" else "Name (e.g. Netflix, Rent)") }
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = cleanNum(it); error = false }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    prefix = { Text("₹ ") }, label = { Text("Amount every month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = day, onValueChange = { day = it.filter { c -> c.isDigit() }.take(2); error = false }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Due day of month (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                CatGrid(ExpenseCats, cat) { cat = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                val dd = day.toIntOrNull() ?: 0
                if (name.isBlank() || a <= 0 || dd !in 1..31) error = true
                else onSave(start.copy(name = name.trim(), amount = a, day = dd, category = cat))
            }) { T("Save", 15, p.primary, bold = true) }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = onDelete) { T("Delete", 15, p.expense) }
                TextButton(onClick = onDismiss) { T("Cancel", 15, p.mute) }
            }
        }
    )
}

@Composable
fun BudgetDialog(d: Data, onSave: (Double, Map<String, Double>) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    var total by remember { mutableStateOf(if (d.budget > 0) d.budget.toLong().toString() else "") }
    val limits = remember {
        mutableStateMapOf<String, String>().apply {
            ExpenseCats.forEach { c -> put(c.name, d.catBudgets[c.name]?.let { if (it > 0) it.toLong().toString() else "" } ?: "") }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.card,
        title = { T("Monthly budget", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = total, onValueChange = { total = cleanNum(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    prefix = { Text("₹ ") }, label = { Text("Total budget per month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                T("Tip: a popular split is 50% needs, 30% wants and 20% savings.", 12, p.mute)
                T("Category limits (optional)", 13, p.text, bold = true)
                ExpenseCats.forEach { c ->
                    OutlinedTextField(
                        value = limits[c.name] ?: "", onValueChange = { v -> limits[c.name] = v.filter { ch -> ch.isDigit() }.take(9) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, prefix = { Text("₹ ") },
                        label = { Text("${c.emoji} ${c.name}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = HashMap<String, Double>()
                limits.forEach { (k, v) -> val x = v.toDoubleOrNull() ?: 0.0; if (x > 0) m[k] = x }
                onSave(total.toDoubleOrNull() ?: 0.0, m)
            }) { T("Save", 15, p.primary, bold = true) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", 15, p.mute) } }
    )
}

@Composable
fun SettingsDialog(
    theme: Int, daily: Boolean, onTheme: (Int) -> Unit, onDaily: (Boolean) -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit, onCsv: () -> Unit, onDismiss: () -> Unit
) {
    val p = LocalPal.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.card,
        title = { T("Settings", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Appearance", 12, p.mute)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { i, l -> Chip(l, theme == i) { onTheme(i) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Daily reminder", 15, weight = FontWeight.Medium)
                        T("At 9 PM: log today's spending", 12, p.mute)
                    }
                    Switch(checked = daily, onCheckedChange = onDaily)
                }
                T("Your data stays on this phone", 12, p.mute)
                Row { Chip("Back up to file", false, onBackup) }
                Row { Chip("Restore from backup", false, onRestore) }
                Row { Chip("Export all as CSV", false, onCsv) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { T("Done", 15, p.primary, bold = true) } }
    )
}
