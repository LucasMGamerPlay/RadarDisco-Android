package com.radardisco.android.ui.theme

import androidx.compose.ui.graphics.Color
import com.radardisco.android.model.FileCategory
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

data class CategoryUiMeta(
    val category: FileCategory,
    val label: String,
    val shortLabel: String,
    val accent: Color,
    val treemapStart: Color,
    val treemapEnd: Color,
    val treemapBorder: Color
)

object RadarFormatters {
    private val ptBrLocale = Locale("pt", "BR")

    val CATEGORY_META: Map<FileCategory, CategoryUiMeta> = mapOf(
        FileCategory.AI_MODELS to CategoryUiMeta(
            category = FileCategory.AI_MODELS,
            label = "Modelos & Pesos de IA",
            shortLabel = "Modelos IA",
            accent = Color(0xFFD946EF),
            treemapStart = Color(0xFF9333EA).copy(alpha = 0.45f),
            treemapEnd = Color(0xFF6B21A8).copy(alpha = 0.65f),
            treemapBorder = Color(0xFFD8B4FE).copy(alpha = 0.40f)
        ),
        FileCategory.GAMES_APPS to CategoryUiMeta(
            category = FileCategory.GAMES_APPS,
            label = "APKs, Jogos & Apps",
            shortLabel = "Apps / Jogos",
            accent = Color(0xFF0EA5E9),
            treemapStart = Color(0xFF0EA5E9).copy(alpha = 0.42f),
            treemapEnd = Color(0xFF0369A1).copy(alpha = 0.62f),
            treemapBorder = Color(0xFF7DD3FC).copy(alpha = 0.40f)
        ),
        FileCategory.MEDIA to CategoryUiMeta(
            category = FileCategory.MEDIA,
            label = "Vídeos, Áudio & Imagens",
            shortLabel = "Mídia",
            accent = Color(0xFF10B981),
            treemapStart = Color(0xFF10B981).copy(alpha = 0.42f),
            treemapEnd = Color(0xFF047857).copy(alpha = 0.62f),
            treemapBorder = Color(0xFF6EE7B7).copy(alpha = 0.40f)
        ),
        FileCategory.DEV_DEPS to CategoryUiMeta(
            category = FileCategory.DEV_DEPS,
            label = "Código, Caches & Deps",
            shortLabel = "Cache / Dev",
            accent = Color(0xFFF59E0B),
            treemapStart = Color(0xFFF59E0B).copy(alpha = 0.42f),
            treemapEnd = Color(0xFFB45309).copy(alpha = 0.62f),
            treemapBorder = Color(0xFFFCD34D).copy(alpha = 0.40f)
        ),
        FileCategory.ARCHIVES to CategoryUiMeta(
            category = FileCategory.ARCHIVES,
            label = "Compactados & Imagens",
            shortLabel = "Compactados",
            accent = Color(0xFFF43F5E),
            treemapStart = Color(0xFFF43F5E).copy(alpha = 0.42f),
            treemapEnd = Color(0xFFBE123C).copy(alpha = 0.62f),
            treemapBorder = Color(0xFFFDA4AF).copy(alpha = 0.40f)
        ),
        FileCategory.DOCUMENTS to CategoryUiMeta(
            category = FileCategory.DOCUMENTS,
            label = "Documentos & Backups",
            shortLabel = "Documentos",
            accent = Color(0xFF818CF8),
            treemapStart = Color(0xFF6366F1).copy(alpha = 0.42f),
            treemapEnd = Color(0xFF4338CA).copy(alpha = 0.62f),
            treemapBorder = Color(0xFFA5B4FC).copy(alpha = 0.40f)
        ),
        FileCategory.OTHER to CategoryUiMeta(
            category = FileCategory.OTHER,
            label = "Outros Arquivos",
            shortLabel = "Outros",
            accent = Color(0xFF94A3B8),
            treemapStart = Color(0xFF64748B).copy(alpha = 0.42f),
            treemapEnd = Color(0xFF334155).copy(alpha = 0.62f),
            treemapBorder = Color(0xFF94A3B8).copy(alpha = 0.35f)
        )
    )

    fun metaFor(category: FileCategory): CategoryUiMeta =
        CATEGORY_META[category] ?: CATEGORY_META.getValue(FileCategory.OTHER)

    fun formatBytes(bytes: Long, decimals: Int = 1): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        val idx = min(units.lastIndex, floor(ln(bytes.toDouble()) / ln(1024.0)).toInt())
        val value = bytes.toDouble() / 1024.0.pow(idx.toDouble())
        val effectiveDecimals = if (idx == 0 || value >= 100.0) 0 else decimals
        return String.format(ptBrLocale, "%.${effectiveDecimals}f %s", value, units[idx])
    }

    fun formatNumber(value: Int): String =
        NumberFormat.getIntegerInstance(ptBrLocale).format(value)

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0,0s"
        if (ms < 1000L) return String.format(ptBrLocale, "%.1fs", ms / 1000.0)
        val totalSec = ms / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        if (mins == 0L) {
            val tenths = (ms % 1000L) / 100L
            return "${secs},${tenths}s"
        }
        return "${mins}m ${secs.toString().padStart(2, '0')}s"
    }
}
