package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.presentation.billing.Replacement
import com.sportssoftw.squadcall.coach.presentation.billing.storeReplacement
import com.sportssoftw.squadcall.model.BillingPeriod.MONTHLY
import com.sportssoftw.squadcall.model.BillingPeriod.YEARLY
import com.sportssoftw.squadcall.model.SpacePlan
import com.sportssoftw.squadcall.presentation.ui.components.previewPlans
import kotlin.test.Test
import kotlin.test.assertEquals

// Changing a store subscription (REQUIREMENTS §5c): going up, or from monthly to yearly, is at once;
// going down, or from yearly to monthly, waits for the renewal.
class StoreReplacementTest {
    private val basic = SpacePlan("BASIC")
    private val pro = SpacePlan("PRO")

    @Test
    fun upIsAtOnceAndDownAtTheRenewal() {
        assertEquals(Replacement.UP, storeReplacement(previewPlans, basic, MONTHLY, pro, MONTHLY))
        assertEquals(Replacement.LONGER, storeReplacement(previewPlans, basic, MONTHLY, pro, YEARLY))
        assertEquals(Replacement.LONGER, storeReplacement(previewPlans, basic, MONTHLY, basic, YEARLY))
        assertEquals(Replacement.DOWN, storeReplacement(previewPlans, pro, MONTHLY, basic, MONTHLY))
        assertEquals(Replacement.DOWN, storeReplacement(previewPlans, basic, YEARLY, basic, MONTHLY))
        assertEquals(Replacement.DOWN, storeReplacement(previewPlans, basic, YEARLY, pro, MONTHLY))
        assertEquals(Replacement.UP, storeReplacement(previewPlans, basic, YEARLY, pro, YEARLY))
    }
}
