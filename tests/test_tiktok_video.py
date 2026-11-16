import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'app/src/main/python'))
from tiktok_video import canonical_url,playback_selector
class TikTokTests(unittest.TestCase):
    def test_shared_link_tracking_is_removed(self):
        self.assertEqual(canonical_url('https://m.tiktok.com/@9ardito/video/7689870025150221590?_r=1'), 'https://www.tiktok.com/@9ardito/video/7689870025150221590')
        self.assertEqual(canonical_url('https://vm.tiktok.com/abc/'), 'https://vm.tiktok.com/abc/')
    def test_watermarked_higher_quality_is_never_selected(self):
        clean={'format_id':'play','ext':'mp4','vcodec':'h264','acodec':'aac','height':480}
        marked={**clean,'format_id':'download','height':1080,'format_note':'watermarked'}
        self.assertEqual(list(playback_selector(2160)({'formats':[clean,marked]})),[clean])
    def test_no_silent_fallback_to_watermarked_or_silent_video(self):
        for fmt in [{'format_id':'download','ext':'mp4'},{'format_id':'play','ext':'mp4','acodec':'none'},{'format_id':'play','ext':'mp4','format_note':'watermarked'}]:
            with self.assertRaises(ValueError):list(playback_selector(2160)({'formats':[fmt]}))
