// Rendered from Act by backend WebAccessTableTest. Do not edit.
export const CODES = [
    "ASSIGN_COMPENSATION",
    "ASSIGN_DELEGATE",
    "EDIT_BRANCH_DATA",
    "EDIT_PAST_DAY",
    "ERASE_CLIENT",
    "MANAGE_CATALOG",
    "MANAGE_PRODUCTS",
    "MANAGE_USERS",
    "SUBMIT_REMITTANCE",
    "VIEW_BRANCH_DATA",
    "VIEW_COMMISSION",
    "VOID_SESSION",
] as const;
export type Code = (typeof CODES)[number];
export type Reach = "EVERYWHERE" | "ANYWHERE" | "HOME" | "HOME_OR_EVERYWHERE" | "HOME_OR_DAY" | "HOME_OR_EVERYWHERE_OR_DAY";
export const ACTS = {
    SEE_BRANCH: { code: "VIEW_BRANCH_DATA", reach: "HOME_OR_EVERYWHERE" },
    SEE_EVERY_BRANCH: { code: "VIEW_BRANCH_DATA", reach: "EVERYWHERE" },
    SEE_DAY: { code: "VIEW_BRANCH_DATA", reach: "HOME_OR_EVERYWHERE_OR_DAY" },
    SEE_ANYWHERE: { code: "VIEW_BRANCH_DATA", reach: "ANYWHERE" },
    WORK_DAY: { code: "EDIT_BRANCH_DATA", reach: "HOME_OR_DAY" },
    WORK_HOME_BRANCH: { code: "EDIT_BRANCH_DATA", reach: "HOME" },
    WORK_ANYWHERE: { code: "EDIT_BRANCH_DATA", reach: "ANYWHERE" },
    VOID: { code: "VOID_SESSION", reach: "HOME_OR_DAY" },
    SEE_COMMISSION: { code: "VIEW_COMMISSION", reach: "HOME_OR_DAY" },
    CHANGE_CLOSED_DAY: { code: "EDIT_PAST_DAY", reach: "HOME" },
    PAY: { code: "ASSIGN_COMPENSATION", reach: "HOME_OR_EVERYWHERE" },
    SUBMIT_REMITTANCE: { code: "SUBMIT_REMITTANCE", reach: "HOME" },
    MANAGE_STOCK: { code: "MANAGE_PRODUCTS", reach: "HOME" },
    MANAGE_CATALOG: { code: "MANAGE_CATALOG", reach: "EVERYWHERE" },
    MANAGE_USERS: { code: "MANAGE_USERS", reach: "EVERYWHERE" },
    ERASE_CLIENT: { code: "ERASE_CLIENT", reach: "EVERYWHERE" },
    ASSIGN_DELEGATE: { code: "ASSIGN_DELEGATE", reach: "EVERYWHERE" },
} as const satisfies Record<string, { code: Code; reach: Reach }>;
