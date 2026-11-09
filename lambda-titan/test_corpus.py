import unittest
from corpus import normalize, validate_vector, fingerprint

class CorpusTests(unittest.TestCase):
    def test_empty_headings_omitted_and_text_unchanged(self):
        result = normalize(dict(source='x.md', source_text=' Original\n', ordinal=0, headingPath=[]))
        self.assertNotIn('headingPath',result)
        self.assertEqual(' Original\n',result['source_text'])
    def test_preserves_context_and_headings(self):
        meta = dict(source='x',source_text='text',ordinal=3,headingPath=['A'],r4r_context='pgvector-heading-v1')
        self.assertEqual(meta,normalize(meta))
    def test_rejects_invalid_provenance(self):
        for meta in [dict(),dict(source='x',source_text='t',ordinal=-1),dict(source='x',source_text='t',ordinal=0,headingPath=[4])]:
            with self.assertRaises(ValueError): normalize(meta)
    def test_vectors(self):
        self.assertEqual(1024,len(validate_vector([1]+[0]*1023)))
        for vector in [[0]*1024,[1]*768,[float('nan')]*1024]:
            with self.assertRaises(ValueError): validate_vector(vector)
    def test_stable_manifest(self):
        self.assertEqual(fingerprint({'a':1,'b':2}),fingerprint({'b':2,'a':1}))
