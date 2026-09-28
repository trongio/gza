package ge.hackerman.gza.feature.plan

/** Test tags, not user-facing. Also Maestro ids (testTagsAsResourceId). */
object PlanTestTags {
    const val SCREEN = "plan_screen"
    const val FROM = "plan_from"
    const val TO = "plan_to"

    fun mode(mode: PlanMode) = when (mode) {
        PlanMode.LeaveNow -> "plan_mode_leave_now"
        PlanMode.DepartAt -> "plan_mode_depart_at"
        PlanMode.ArriveBy -> "plan_mode_arrive_by"
    }
}
