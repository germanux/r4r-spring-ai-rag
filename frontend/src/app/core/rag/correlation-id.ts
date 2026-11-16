interface IdCrypto {
    randomUUID?: () => string;
    getRandomValues(array: Uint8Array): Uint8Array;
}

/** UUID v4 for ephemeral references, including LAN HTTP where randomUUID is unavailable. */
export function correlationId(source: IdCrypto = globalThis.crypto): string {
    if (typeof source.randomUUID === 'function') return source.randomUUID();
    // getRandomValues also works in insecure contexts. Never weaken rights references with Math.random.
    const bytes = source.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
