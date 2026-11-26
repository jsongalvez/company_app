import type { S } from "../api/client";

type Role = Pick<S["RoleResponse"], "name" | "assignable" | "noAccess">;

/**
 * The roles the Roles dialog saves. Its checkboxes only offer the [roles] the server marks assignable, and a
 * checkbox group rebuilds its list from the boxes on screen, so a role the dialog can't show (Superuser) would
 * silently drop and the server would refuse the save. Keep every role it doesn't offer; nothing at all saves the
 * server's no-access role.
 */
export function rolesToSave(picked: readonly string[], current: readonly string[], roles: readonly Role[]): string[] {
    const offered = new Set(roles.filter((r) => r.assignable).map((r) => r.name));
    const none = roles.filter((r) => r.noAccess).map((r) => r.name);
    const kept = current.filter((r) => !none.includes(r) && !offered.has(r));
    const out = [...new Set([...picked, ...kept])];
    return out.length ? out : none;
}
