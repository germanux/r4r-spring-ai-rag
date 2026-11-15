import { validResponse } from './rag.models';
describe('RAG response contract',()=>{
    it('rejects incomplete stream answers', () => { expect(validResponse({ answer: 'x', abstained: false })).toBeFalse(); expect(validResponse({ answer: 'x', abstained: false, citations: [] })).toBeTrue(); });
});
