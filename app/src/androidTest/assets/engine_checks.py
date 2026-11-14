"""Real yt-dlp downloads using deterministic local media, without platform requests."""
import os
from pathlib import Path
import downloader
from yt_dlp.extractor.common import InfoExtractor


def run(folder):
    original = downloader.yt_dlp.YoutubeDL
    class FixtureIE(InfoExtractor):
        IE_NAME = 'sniplyfixture'
        _VALID_URL = r'https://youtu\.be/sniply-fixture-(?P<id>combined|split)'
        def _real_extract(self, url):
            video_id = self._match_id(url)
            base = Path(folder)
            if video_id == 'combined':
                formats = [{'format_id': 'single', 'url': (base / 'combined.mp4').as_uri(), 'ext': 'mp4'}]
            else:
                formats = [
                    {'format_id': 'video', 'url': (base / 'video.mp4').as_uri(), 'ext': 'mp4', 'vcodec': 'avc1.42c00b', 'acodec': 'none', 'height': 120},
                    {'format_id': 'audio', 'url': (base / 'audio.m4a').as_uri(), 'ext': 'm4a', 'vcodec': 'none', 'acodec': 'mp4a.40.2'},
                ]
            return {'id': video_id, 'title': 'Sniply regression fixture', 'formats': formats}
    class FixtureYDL(original):
        def __init__(self, options):
            super().__init__({**options, 'enable_file_urls': True}, auto_init=False)
            self.add_info_extractor(FixtureIE())
    class Progress:
        def onProgress(self, text):
            pass
    downloader.yt_dlp.YoutubeDL = FixtureYDL
    try:
        combined = downloader.download('https://youtu.be/sniply-fixture-combined', folder, 'Best available', Progress())
        assert len(combined) == 1 and os.path.getsize(combined[0]) > 0, combined
        split = downloader.download('https://youtu.be/sniply-fixture-split', folder, 'Best available', Progress())
        assert len(split) == 2 and all(os.path.getsize(p) > 0 for p in split), split
        return split
    finally:
        downloader.yt_dlp.YoutubeDL = original
