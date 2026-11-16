import {correlationId} from './correlation-id';

describe('Ephemeral correlation UUIDs', () => {
    it('uses native UUIDs when available', () => {
        const getRandomValues = jasmine.createSpy('getRandomValues');
        expect(correlationId({randomUUID: () => 'native-uuid', getRandomValues})).toBe('native-uuid');
        expect(getRandomValues).not.toHaveBeenCalled();
    });
    it('uses secure random bytes and sets UUID v4 version and variant without randomUUID', () => {
        expect(correlationId({getRandomValues: bytes => bytes.fill(0)})).toBe('00000000-0000-4000-8000-000000000000');
        expect(correlationId({getRandomValues: bytes => bytes.fill(255)})).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
    });
    it('keeps fallback references distinct using the browser CSPRNG', () => {
        const source = {getRandomValues: (bytes: Uint8Array) => crypto.getRandomValues(bytes)};
        const values = Array.from({length:32}, () => correlationId(source));
        expect(new Set(values).size).toBe(values.length);
        values.forEach(value => expect(value).toMatch(/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/));
    });
});
