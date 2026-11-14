import { Pipe, PipeTransform } from '@angular/core';
export function markdown(text: string): string { const escape = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;'); const inline = (s: string) => escape(s).replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/\*([^*]+)\*/g, '<em>$1</em>'); let list = '', html = ''; for (const line of text.split('\n')) {
    const m = /^(?:[-*] |\d+\. )(.*)$/.exec(line);
    const kind = m ? (/^\d/.test(line) ? 'ol' : 'ul') : '';
    if (list !== kind) {
        if (list)
            html += '</' + list + '>';
        if (kind)
            html += '<' + kind + '>';
        list = kind;
    }
    if (m)
        html += '<li>' + inline(m[1]) + '</li>';
    else if (line.trim())
        html += '<p>' + inline(line) + '</p>';
} return html + (list ? '</' + list + '>' : ''); }
@Pipe({ name: 'markdown', standalone: true })
export class MarkdownPipe implements PipeTransform {
    transform(text: string): string { return markdown(text); }
}
