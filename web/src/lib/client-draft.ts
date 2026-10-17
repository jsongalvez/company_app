import type { S } from "../api/client";
import type { ClientDraft } from "../components/ClientForm.svelte";

export function emptyDraft(name = ""): ClientDraft {
    // "Dela Cruz, Juan" or "Juan Dela Cruz" typed in a search box seeds the form.
    let firstName = "";
    let lastName = "";
    if (name.includes(",")) [lastName = "", firstName = ""] = name.split(",").map((s) => s.trim());
    else if (name && !/\d/.test(name)) {
        const parts = name.split(/\s+/);
        firstName = parts.slice(0, -1).join(" ") || (parts[0] ?? "");
        lastName = parts.length > 1 ? parts[parts.length - 1]! : "";
    }
    return {
        firstName,
        lastName,
        middleName: "",
        suffix: "",
        phoneNumber: /\d/.test(name) ? name : "",
        address: "",
        gender: "",
        age: "",
        earlierVisits: "",
        systolicBp: "",
        diastolicBp: "",
        medicalConditions: "",
    };
}

export function draftFrom(c: S["ClientResponse"]): ClientDraft {
    return {
        firstName: c.firstName ?? "",
        lastName: c.lastName ?? "",
        middleName: c.middleName ?? "",
        suffix: c.suffix ?? "",
        phoneNumber: c.phoneNumber ?? "",
        address: c.address ?? "",
        gender: c.gender === "M" || c.gender === "F" ? c.gender : "",
        age: String(c.age),
        earlierVisits: c.earlierVisits ? String(c.earlierVisits) : "",
        systolicBp: c.systolicBp?.toString() ?? "",
        diastolicBp: c.diastolicBp?.toString() ?? "",
        medicalConditions: c.medicalConditions ?? "",
    };
}

const opt = (s: string) => (s.trim() ? s.trim() : null);
const optInt = (s: string | number) => (String(s).trim() ? Number(s) : null);

export function createBody(id: string, d: ClientDraft): S["CreateClientRequest"] {
    return {
        id,
        firstName: d.firstName.trim(),
        lastName: d.lastName.trim(),
        middleName: opt(d.middleName),
        suffix: opt(d.suffix),
        phoneNumber: opt(d.phoneNumber),
        address: opt(d.address),
        gender: d.gender as "M" | "F",
        age: Number(d.age),
        earlierVisits: Number(d.earlierVisits) || 0,
        systolicBp: optInt(d.systolicBp),
        diastolicBp: optInt(d.diastolicBp),
        medicalConditions: opt(d.medicalConditions),
    };
}

/** PATCH body: changed fields only; blanked optional fields go to clearFields. */
export function updateBody(before: S["ClientResponse"], d: ClientDraft): S["UpdateClientRequest"] {
    const body: S["UpdateClientRequest"] = {};
    const clear: string[] = [];
    const text = ["firstName", "lastName", "middleName", "suffix", "phoneNumber", "address", "medicalConditions"] as const;
    for (const k of text) {
        const nv = d[k].trim();
        const ov = before[k] ?? "";
        if (nv === ov) continue;
        if (nv) body[k] = nv;
        else clear.push(k);
    }
    // BP clears only as a pair.
    const sys = optInt(d.systolicBp);
    const dia = optInt(d.diastolicBp);
    if (sys === null && dia === null) {
        if (before.systolicBp !== null || before.diastolicBp !== null) clear.push("systolicBp", "diastolicBp");
    } else {
        if (sys !== null && sys !== before.systolicBp) body.systolicBp = sys;
        if (dia !== null && dia !== before.diastolicBp) body.diastolicBp = dia;
    }
    if (Number(d.age) !== before.age) body.age = Number(d.age);
    if ((Number(d.earlierVisits) || 0) !== (before.earlierVisits ?? 0)) body.earlierVisits = Number(d.earlierVisits) || 0;
    if (d.gender && d.gender !== before.gender) body.gender = d.gender;
    if (clear.length) body.clearFields = clear;
    return body;
}
