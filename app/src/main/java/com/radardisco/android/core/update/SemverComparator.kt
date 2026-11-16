package com.radardisco.android.core.update

object SemverComparator {
    fun parseVersionParts(version: String): List<Int> {
        val clean = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (clean.isBlank()) return listOf(0, 0, 0)
        return clean.split('.').map { it.toIntOrNull() ?: 0 }
    }

    /**
     * Retorna 1 se a > b, -1 se a < b, 0 se forem iguais.
     */
    fun compareSemverVersions(a: String, b: String): Int {
        val pa = parseVersionParts(a)
        val pb = parseVersionParts(b)
        val maxLen = maxOf(pa.size, pb.size, 3)
        for (i in 0 until maxLen) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va > vb) return 1
            if (va < vb) return -1
        }
        return 0
    }
}
