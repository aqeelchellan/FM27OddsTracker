package com.aky.fm27odds

data class Odds(val home: String, val draw: String, val away: String,
                val homePct: Int, val drawPct: Int, val awayPct: Int, val favourite: String)

object OddsParser {
    // Initial heuristic: finds three decimal odds in a single OCR text block.
    // It deliberately requires plausible decimal odds to reduce false detections.
    fun find(raw: String): Odds? {
        val cleaned = raw.replace(',', '.')
        val matches = Regex("(?<![\\d.])([1-9]\\d?\\.\\d{1,2})(?![\\d.])")
            .findAll(cleaned).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 1.01..100.0 }.toList()
        if (matches.size < 3) return null
        val triple = matches.windowed(3).firstOrNull { it.all { n -> n > 1.0 } } ?: return null
        val inv = triple.map { 1.0 / it }
        val total = inv.sum()
        val pct = inv.map { (it / total * 100).toInt() }
        val names = listOf("Home", "Draw", "Away")
        val fav = names[inv.indices.minByOrNull { triple[it] } ?: 0] + " favourite"
        return Odds(triple[0].toString(), triple[1].toString(), triple[2].toString(),
            pct[0], pct[1], pct[2], fav)
    }
}
