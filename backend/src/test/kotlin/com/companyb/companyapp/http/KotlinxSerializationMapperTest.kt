package com.companyb.companyapp.http

import com.companyb.companyapp.contracts.session.DashboardCommissionResponse
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.TodayScreenResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinxSerializationMapperTest {
    private val mapper = KotlinxSerializationMapper()
    private val reference = Json { ignoreUnknownKeys = true }

    private val dashboard =
        DashboardResponse(sessions = emptyList(), commission = DashboardCommissionResponse("12.50", 3))

    /** What the mapper wrote before the direct path: encode to a JsonElement tree, then to text. */
    private inline fun <reified T> viaTree(value: T): String =
        reference.encodeToString(JsonElement.serializer(), reference.encodeToJsonElement(value))

    @Test
    fun `an object is written with the same bytes as through a JSON tree`() {
        val screen =
            TodayScreenResponse(dashboard = dashboard, dashboardRefusal = null, bookings = emptyList(), roster = null)

        assertEquals(viaTree(screen), mapper.toJsonString(screen, TodayScreenResponse::class.java))
    }

    @Test
    fun `a list of objects keeps its shape`() {
        val list = listOf(dashboard, dashboard)

        assertEquals(viaTree(list), mapper.toJsonString(list, list.javaClass))
    }

    @Test
    fun `a map of objects keeps its shape`() {
        val map = mapOf("a" to dashboard, "b" to null)

        assertEquals("""{"a":${viaTree(dashboard)},"b":null}""", mapper.toJsonString(map, map.javaClass))
    }

    @Test
    fun `an object typed as Object is written by its own class`() {
        assertEquals(viaTree(dashboard), mapper.toJsonString(dashboard, Any::class.java))
    }
}
