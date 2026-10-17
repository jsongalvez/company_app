<script lang="ts">
    /** Shared create/edit fields for a client. Values bind to `v`. */
    export type ClientDraft = {
        firstName: string;
        lastName: string;
        middleName: string;
        suffix: string;
        phoneNumber: string;
        address: string;
        gender: "M" | "F" | "";
        age: string;
        earlierVisits: string;
        systolicBp: string;
        diastolicBp: string;
        medicalConditions: string;
    };
    let { v = $bindable() }: { v: ClientDraft } = $props();
</script>

<div class="form-grid">
    <label class="field"><span class="req">Last name</span><input bind:value={v.lastName} required /></label>
    <label class="field"><span class="req">First name</span><input bind:value={v.firstName} required /></label>
    <label class="field">Middle name<input bind:value={v.middleName} /></label>
    <label class="field">Suffix<input bind:value={v.suffix} placeholder="Jr., III" /></label>
    <label class="field"
        ><span class="req">Gender</span>
        <select bind:value={v.gender} required>
            <option value="" disabled>Select</option>
            <option value="M">Male</option>
            <option value="F">Female</option>
        </select>
    </label>
    <label class="field"><span class="req">Age</span><input class="num" type="number" min="0" max="150" bind:value={v.age} required /></label>
    <label class="field" title="Visits this client had before we started using the app. Sets the visit type and price."
        >Visits before the app<input class="num" type="number" min="0" max="1000" placeholder="0" bind:value={v.earlierVisits} /></label
    >
    <label class="field">Phone<input type="tel" bind:value={v.phoneNumber} /></label>
    <label class="field"
        >BP (sys / dia)
        <span class="row"
            ><input class="num grow" type="number" min="0" placeholder="120" bind:value={v.systolicBp} /><input
                class="num grow"
                type="number"
                min="0"
                placeholder="80"
                bind:value={v.diastolicBp}
            /></span
        >
    </label>
    <label class="field wide">Address<input bind:value={v.address} /></label>
    <label class="field wide">Medical conditions<textarea bind:value={v.medicalConditions} rows="2"></textarea></label>
</div>
