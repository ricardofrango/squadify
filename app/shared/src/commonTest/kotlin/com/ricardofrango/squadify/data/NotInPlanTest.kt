package com.ricardofrango.squadify.data

import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.data.remote.isNotInPlan
import com.ricardofrango.squadify.model.PLAN_LIMIT_CODE
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The coach server's "not on the space's plan" (REQUIREMENTS §5c) is a 403 with its own code: screens show
// what the plan allows instead of an error. Any other 403 stays an error.
class NotInPlanTest {
    @Test
    fun onlyThePlansOwn403IsNotInPlan() {
        assertTrue(ApiException(403, PLAN_LIMIT_CODE, "Stats are on the Basic and Pro plans").isNotInPlan)
        assertFalse(ApiException(403, "AUTH_002", "Access Denied").isNotInPlan)
        assertFalse(ApiException(400, PLAN_LIMIT_CODE, "?").isNotInPlan)
        assertFalse(IllegalStateException().isNotInPlan)
    }
}
