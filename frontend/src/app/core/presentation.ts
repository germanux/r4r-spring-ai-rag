/** One application, with commercial presentation only at the dedicated customer route. */
export function isCommercialAssistant(pathname = location.pathname): boolean {
    return /^\/asistente-ia(?:\/|$)/.test(pathname);
}
