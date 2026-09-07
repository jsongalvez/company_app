package com.companyb.companyapp.finance

import androidx.lifecycle.ViewModel
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.StatelessHooks
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

// #479 — extracted from FinanceReportsViewModel so the file-function wall (TMF) stays
// honest. Extension functions on the ViewModel; FinanceReportsScreen method references and
// FinanceReportsViewModelTest (same package) resolve them unchanged.

// ─────────────────────────── exports (D6) ───────────────────────────

internal fun FinanceReportsViewModel.exportMode(
    key: String,
    url: String,
) {
    if (downloadsState.value.containsKey(key) && downloadsState.value[key] is UiState.Loading) return
    exportErrorsState.value = exportErrorsState.value - key
    downloadsState.value = downloadsState.value + (key to UiState.Loading)
    handler.launchStateless(
        operation = "export:$key",
        endpoint = "GET export $key",
        block = { apiClient.httpClient.get(url) },
        transform = {
            downloadsState.value =
                downloadsState.value +
                (
                    key to
                        UiState.Success(
                            FinanceReportsViewModel.DownloadPayload(
                                fileName = fileNameOf(it),
                                bytes = it.readRawBytes(),
                            ),
                        )
                )
        },
        hooks =
            StatelessHooks(
                onNonSuccess = { response ->
                    downloadsState.value = downloadsState.value - key
                    exportErrorsState.value =
                        exportErrorsState.value + (key to "Export failed: ${response.status.value}")
                },
                onError = { e ->
                    downloadsState.value = downloadsState.value - key
                    exportErrorsState.value =
                        exportErrorsState.value + (key to "Export failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.modeExportUrl(
    mode: ReportMode,
    branchId: String,
    format: String,
): String =
    when (mode) {
        // DAILY deliberately absent: no toolbar export (D4 — per-day only, in the detail).
        ReportMode.DAILY -> {
            ""
        }

        ReportMode.MONTHLY -> {
            val month = appliedMonthState.value
            ApiRoutes.branchExportWithQuery(
                ApiRoutes.branchExportMonthly(branchId),
                "year=${month.year}&month=${month.month.ordinal + 1}&format=$format",
            )
        }

        ReportMode.ALL_TIME -> {
            ApiRoutes.branchExportWithQuery(ApiRoutes.branchExportAllTime(branchId), "format=$format")
        }

        ReportMode.DATE_RANGE -> {
            val range = appliedRangeState.value
            if (range != null) {
                ApiRoutes.branchExportWithQuery(
                    ApiRoutes.branchExportRange(branchId),
                    "from=${range.first}&to=${range.second}&format=$format",
                )
            } else {
                ""
            }
        }
    }

internal fun FinanceReportsViewModel.exportDay(
    day: DailySalesSummaryResponse,
    branchId: String,
    format: String,
) {
    // Keyed on branchDayId (pass-4/5 HARD): the date alone collides across branches — an
    // in-flight branch-A export would block + mislabel branch B's same-date row. The
    // screen's ExportButtons look up the SAME key (both sides must agree).
    exportMode(
        key = "day:${day.branchDayId}:$format",
        url =
            ApiRoutes.branchExportWithQuery(
                ApiRoutes.branchExportDaily(branchId),
                "date=${day.date}&format=$format",
            ),
    )
}

internal fun FinanceReportsViewModel.exportPublic(
    kind: String,
    format: String,
) {
    exportMode(
        key = "public:$kind:$format",
        url = ApiRoutes.branchExportWithQuery("${ApiRoutes.BRANCHES_EXPORT}/$kind", "format=$format"),
    )
}

/** #105 D4 — the toolbar's mode export (Daily has none — per-day only, in the detail). */
internal fun FinanceReportsViewModel.exportModeCurrent(format: String) {
    val branchId = selectedBranchIdState.value ?: return
    val url = modeExportUrl(modeState.value, branchId, format)
    if (url.isEmpty()) return
    // Keyed on branchId (pass-9 SOFT): a late landing from a superseded branch must not
    // block/mislabel the current branch's export.
    exportMode(
        key = "mode:$branchId:${modeState.value.name}:$format",
        url = url,
    )
}

internal fun FinanceReportsViewModel.consumeDownload(key: String) {
    downloadsState.value = downloadsState.value - key
}

internal fun FinanceReportsViewModel.fileNameOf(response: HttpResponse): String {
    val disposition = response.headers[HttpHeaders.ContentDisposition]
    val quoted =
        disposition
            ?.substringAfter("filename=\"", missingDelimiterValue = "")
            ?.substringBefore('"')
            ?.takeIf { it.isNotBlank() }
    return quoted ?: "companyapp-export.${extensionOf(response)}"
}

internal fun FinanceReportsViewModel.extensionOf(response: HttpResponse): String {
    val type = response.contentType()
    return when (type?.withoutParameters()?.toString()) {
        "application/pdf" -> "pdf"
        "text/csv" -> "csv"
        else -> "bin"
    }
}
