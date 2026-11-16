package com.radardisco.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radardisco.android.core.ai.CloudUrlValidator
import com.radardisco.android.model.AiApiProvider
import com.radardisco.android.model.AiSettings
import com.radardisco.android.ui.theme.RadarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsDialog(
    initialSettings: AiSettings,
    hasScannedData: Boolean,
    onDismiss: () -> Unit,
    onSave: (AiSettings, Boolean) -> Unit,
    onFetchModels: suspend (AiApiProvider, String, String) -> Result<List<String>>
) {
    var provider by remember(initialSettings) { mutableStateOf(initialSettings.provider) }
    var apiKey by remember(initialSettings) { mutableStateOf(initialSettings.apiKey) }
    var model by remember(initialSettings) { mutableStateOf(initialSettings.model) }
    var customBaseUrl by remember(initialSettings) { mutableStateOf(initialSettings.customBaseUrl) }
    var showKey by remember { mutableStateOf(false) }

    var loadingModels by remember { mutableStateOf(false) }
    var detectedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val customUrlValidationError = if (provider == AiApiProvider.CUSTOM_OPENAI_API) {
        CloudUrlValidator.validateCloudApiUrl(customBaseUrl).exceptionOrNull()?.message
    } else {
        null
    }

    val candidateModels = remember(provider, detectedModels) {
        (detectedModels + provider.presetModels).distinct()
    }

    val canSave = apiKey.isNotBlank() &&
        model.isNotBlank() &&
        customUrlValidationError == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(RadarColors.BgDialog)
                .border(1.dp, RadarColors.BorderMuted, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = RadarColors.Fuchsia400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Configurar IA (APIs na Nuvem)",
                        color = RadarColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar",
                        tint = RadarColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "A versão Android opera exclusivamente com provedores externos via API HTTPS. Modelos locais não estão disponíveis.",
                    color = RadarColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RadarColors.BgSurfaceAlt)
                        .border(1.dp, RadarColors.BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                )

                Text(
                    text = "Provedor de API",
                    color = RadarColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiApiProvider.entries.forEach { p ->
                        val selected = p == provider
                        Text(
                            text = p.displayName,
                            color = if (selected) RadarColors.Fuchsia200 else RadarColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) RadarColors.Fuchsia600.copy(alpha = 0.25f) else RadarColors.BgRoot
                                )
                                .border(
                                    1.dp,
                                    if (selected) RadarColors.Fuchsia400 else RadarColors.BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    provider = p
                                    detectedModels = emptyList()
                                    modelsError = null
                                    if (p != AiApiProvider.CUSTOM_OPENAI_API) {
                                        model = p.defaultModel
                                        customBaseUrl = p.defaultBaseUrl
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                // Chave da API
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Chave da API (API Key)", fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            text = if (provider == AiApiProvider.GEMINI) "AIza..." else "sk-...",
                            fontSize = 12.sp,
                            color = RadarColors.TextMuted
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Mostrar ou ocultar chave",
                                tint = RadarColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = RadarColors.BgRoot,
                        unfocusedContainerColor = RadarColors.BgRoot,
                        focusedBorderColor = RadarColors.Fuchsia400,
                        unfocusedBorderColor = RadarColors.BorderSubtle,
                        focusedTextColor = RadarColors.TextPrimary,
                        unfocusedTextColor = RadarColors.TextPrimary,
                        focusedLabelColor = RadarColors.Fuchsia300,
                        unfocusedLabelColor = RadarColors.TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // URL Customizada somente quando CUSTOM_OPENAI_API
                if (provider == AiApiProvider.CUSTOM_OPENAI_API) {
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = { Text("Endpoint HTTPS da API na Nuvem", fontSize = 12.sp) },
                        placeholder = { Text("https://api.exemplo.com/v1", fontSize = 12.sp, color = RadarColors.TextMuted) },
                        singleLine = true,
                        isError = customUrlValidationError != null,
                        supportingText = {
                            Text(
                                text = customUrlValidationError
                                    ?: "Use HTTPS público compatível com OpenAI (/chat/completions).",
                                color = if (customUrlValidationError != null) RadarColors.Rose300 else RadarColors.TextMuted,
                                fontSize = 10.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = RadarColors.BgRoot,
                            unfocusedContainerColor = RadarColors.BgRoot,
                            focusedBorderColor = RadarColors.Fuchsia400,
                            unfocusedBorderColor = RadarColors.BorderSubtle,
                            focusedTextColor = RadarColors.TextPrimary,
                            unfocusedTextColor = RadarColors.TextPrimary,
                            focusedLabelColor = RadarColors.Fuchsia300,
                            unfocusedLabelColor = RadarColors.TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Modelo + Detectar Modelos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = RadarColors.BgRoot,
                            unfocusedContainerColor = RadarColors.BgRoot,
                            focusedBorderColor = RadarColors.Fuchsia400,
                            unfocusedBorderColor = RadarColors.BorderSubtle,
                            focusedTextColor = RadarColors.TextPrimary,
                            unfocusedTextColor = RadarColors.TextPrimary,
                            focusedLabelColor = RadarColors.Fuchsia300,
                            unfocusedLabelColor = RadarColors.TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loadingModels = true
                                modelsError = null
                                val targetUrl = if (provider == AiApiProvider.CUSTOM_OPENAI_API) {
                                    customBaseUrl
                                } else {
                                    provider.defaultBaseUrl
                                }
                                val res = onFetchModels(provider, targetUrl, apiKey)
                                loadingModels = false
                                res.onSuccess { list ->
                                    detectedModels = list
                                    if (list.isNotEmpty() && model.isBlank()) {
                                        model = list.first()
                                    }
                                }.onFailure { err ->
                                    modelsError = err.message ?: "Falha ao detectar modelos."
                                }
                            }
                        },
                        enabled = !loadingModels && apiKey.isNotBlank() && customUrlValidationError == null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
                    ) {
                        if (loadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = RadarColors.Fuchsia300
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detectar", fontSize = 11.sp)
                    }
                }

                if (modelsError != null) {
                    Text(
                        text = modelsError.orEmpty(),
                        color = RadarColors.Rose300,
                        fontSize = 11.sp
                    )
                }

                if (candidateModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidateModels.forEach { candidate ->
                            val active = candidate == model.trim()
                            Text(
                                text = candidate,
                                color = if (active) RadarColors.Sky300 else RadarColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) RadarColors.Sky500.copy(alpha = 0.2f) else RadarColors.BgRoot)
                                    .border(
                                        1.dp,
                                        if (active) RadarColors.Sky400 else RadarColors.BorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { model = candidate }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Rodapé de botões
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSave(
                            AiSettings(
                                provider = provider,
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                customBaseUrl = if (provider == AiApiProvider.CUSTOM_OPENAI_API) {
                                    customBaseUrl.trim()
                                } else {
                                    provider.defaultBaseUrl
                                }
                            ),
                            false
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Salvar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        onSave(
                            AiSettings(
                                provider = provider,
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                customBaseUrl = if (provider == AiApiProvider.CUSTOM_OPENAI_API) {
                                    customBaseUrl.trim()
                                } else {
                                    provider.defaultBaseUrl
                                }
                            ),
                            hasScannedData
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadarColors.Fuchsia600,
                        contentColor = RadarColors.TextPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasScannedData) "Salvar e Analisar" else "Salvar Chave",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
