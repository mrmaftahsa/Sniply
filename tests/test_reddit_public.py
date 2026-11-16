import html
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
from reddit_public import EmbedMetadata

class PublicMetadataTests(unittest.TestCase):
    def parse(self, post):
        parser = EmbedMetadata('1aizlig')
        parser.feed('<shreddit-screenview-data data="' + html.escape(json.dumps({'post': post}), quote=True) + '"></shreddit-screenview-data>')
        return parser.media_id
    def test_exact_post_media(self):
        self.assertEqual(self.parse({'id':'t3_1aizlig','url':'https://v.redd.it/7qmxgw1b3ngc1'}), '7qmxgw1b3ngc1')
    def test_unrelated_recommended_post_is_ignored(self):
        self.assertIsNone(self.parse({'id':'t3_another','url':'https://v.redd.it/other'}))
    def test_external_and_spoofed_media_hosts_are_ignored(self):
        for url in ['https://v.redd.it.evil.test/video','https://other.test/video','javascript:alert(1)']:
            self.assertIsNone(self.parse({'id':'t3_1aizlig','url':url}))
