import { markdown } from './markdown.pipe';
describe('Markdown rendering',()=>{
    it('escapes HTML and only renders safe Markdown links', () => { const html = markdown('<img src=x onerror=alert(1)> **safe** [bad](javascript:alert) [ok](https://example.com)'); expect(html).not.toContain('<img'); expect(html).not.toContain('href="javascript:'); expect(html).toContain('<strong>safe</strong>'); expect(html).toContain('href="https://example.com"'); });
});
