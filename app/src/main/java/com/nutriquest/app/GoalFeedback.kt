package com.nutriquest.app

internal enum class GoalSignal { OVER_ENTERED_TARGET, SWEET_SNACK_REMINDER, PLANNED_TREAT, PROTEIN_PROGRESS, CONSISTENCY }

internal data class GoalFeedback(val signal: GoalSignal, val evidence: String, val reminder: String)

internal fun goalFeedback(meals: List<Meal>, dayMeals: List<Meal>, profile: UserProfile): GoalFeedback {
    val day = dayMeals.flatMap { it.items }.nutrition()
    val hasSweetSnack = meals.flatMap { it.items }.any { it.food.sweetSnack }
    val signal = when {
        day.energyKcal > profile.energyKcal -> GoalSignal.OVER_ENTERED_TARGET
        hasSweetSnack && profile.goal == DietGoal.FEWER_SWEETS -> GoalSignal.SWEET_SNACK_REMINDER
        hasSweetSnack && profile.goal == DietGoal.WEIGHT_MANAGEMENT -> GoalSignal.PLANNED_TREAT
        profile.goal == DietGoal.MUSCLE && day.proteinGrams > 0 -> GoalSignal.PROTEIN_PROGRESS
        else -> GoalSignal.CONSISTENCY
    }
    val evidence = "Recorded day so far: approximately ${day.energyKcal} kcal against the user's ${profile.energyKcal} kcal target; " +
        "${day.proteinGrams} g protein against their ${profile.proteinGrams} g target. " + when (signal) {
        GoalSignal.OVER_ENTERED_TARGET -> "The entered energy target is exceeded in the log. Challenge excuses, then encourage returning to usual planned meals."
        GoalSignal.SWEET_SNACK_REMINDER -> "A sweet snack is logged and the user explicitly wants fewer sweet snacks. Challenge this intention-choice gap without claiming the whole diet has failed."
        GoalSignal.PLANNED_TREAT -> "A sweet snack is logged during a weight-management plan. A treat can fit; remind them to account for the portion, not that dessert prevents weight loss."
        GoalSignal.PROTEIN_PROGRESS -> "Protein is recorded toward the user's goal. Acknowledge progress and challenge them to stay consistent; do not invent a failure."
        GoalSignal.CONSISTENCY -> "No definite goal conflict is established. Motivate continued logging and following the stated plan; do not manufacture a failure."
    }
    val english = when (signal) {
        GoalSignal.OVER_ENTERED_TARGET -> "Your log is over your entered energy target. Keep logging honestly and return to your usual planned meals."
        GoalSignal.SWEET_SNACK_REMINDER -> "You chose fewer sweet snacks. Keep the portion honest and make your next choice match that goal."
        GoalSignal.PLANNED_TREAT -> "A treat can fit your plan. Account for the portion and keep following your plan."
        GoalSignal.PROTEIN_PROGRESS -> "Your log records progress toward your protein target. Keep your next meals consistent with your plan."
        GoalSignal.CONSISTENCY -> "Keep logging your portions and following your own plan. Consistency counts."
    }
    val cantonese = when (signal) {
        GoalSignal.OVER_ENTERED_TARGET -> "紀錄暫時超過你設定嘅熱量目標；照實記低，之後跟返平時嘅用餐計劃。"
        GoalSignal.SWEET_SNACK_REMINDER -> "你揀咗少食甜零食；份量照實記低，下一個選擇跟返自己個目標。"
        GoalSignal.PLANNED_TREAT -> "甜品可以係計劃一部分；份量記準，繼續跟返你個計劃。"
        GoalSignal.PROTEIN_PROGRESS -> "紀錄入面嘅蛋白質正累積緊；之後繼續跟返你嘅用餐計劃。"
        GoalSignal.CONSISTENCY -> "份量照實記，繼續跟自己個計劃；持續落去先係重點。"
    }
    val traditional = when (signal) {
        GoalSignal.OVER_ENTERED_TARGET -> "紀錄暫時超過你設定的熱量目標；如實記錄，之後回到平常的用餐計劃。"
        GoalSignal.SWEET_SNACK_REMINDER -> "你選擇了減少甜零食；如實記錄份量，讓下一個選擇符合目標。"
        GoalSignal.PLANNED_TREAT -> "甜點可以是計劃的一部分；記準份量，繼續遵循自己的計劃。"
        GoalSignal.PROTEIN_PROGRESS -> "紀錄中的蛋白質正在累積；之後繼續遵循自己的用餐計劃。"
        GoalSignal.CONSISTENCY -> "如實記錄份量，繼續遵循自己的計劃；持續下去才是重點。"
    }
    val reminder = when (profile.language) {
        RoastLanguage.ENGLISH -> english
        RoastLanguage.TRADITIONAL -> traditional
        RoastLanguage.CANTONESE -> cantonese
        RoastLanguage.MIXED -> "$cantonese Keep going."
    }
    return GoalFeedback(signal, evidence, reminder)
}
