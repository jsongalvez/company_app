/**
 * The roles the Roles dialog saves. Its checkboxes only offer [assignable] roles, and a checkbox
 * group rebuilds its list from the boxes on screen, so a role the dialog can't show (Superuser)
 * would silently drop and the server would refuse the save. Keep every role it doesn't offer.
 */
export function rolesToSave(picked: readonly string[], current: readonly string[], assignable: readonly string[]): string[] {
    const kept = current.filter((r) => r !== "ONBOARDING" && !assignable.includes(r));
    const roles = [...new Set([...picked, ...kept])];
    return roles.length ? roles : ["ONBOARDING"];
}
