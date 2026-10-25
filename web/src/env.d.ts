/** Build time, sent with feedback so reports show which version they came from. */
declare const __APP_VERSION__: string;

interface ImportMetaEnv {
    /** Error tracker address (Sentry or GlitchTip DSN). Unset: nothing is sent. */
    readonly VITE_SENTRY_DSN?: string;
}
