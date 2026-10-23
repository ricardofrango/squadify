package com.ricardofrango.squadify.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TeamRole became AgeGroupRole, but the permission setting's JSON key stays "minTeamRole", so apps from
// before the rename keep reading and sending it.
class ClubPermissionSettingJsonTest {

    @Test
    fun theMinimumRoleKeepsItsOldJsonName() {
        val json = Json.encodeToString(ClubPermissionSetting.serializer(), ClubPermissionSetting(ConfigurablePermission.EDIT_PLAYER, AgeGroupRole.STAFF, null))
        val fields = Json.parseToJsonElement(json).jsonObject
        assertTrue("minTeamRole" in fields, json)
        assertEquals("\"STAFF\"", fields.getValue("minTeamRole").toString())
    }

    @Test
    fun anOldAppsSettingStillReads() {
        val setting = Json.decodeFromString(ClubPermissionSetting.serializer(), """{"action":"EDIT_PLAYER","minTeamRole":"COACH","minClubRole":null}""")
        assertEquals(AgeGroupRole.COACH, setting.minAgeGroupRole)
    }
}
