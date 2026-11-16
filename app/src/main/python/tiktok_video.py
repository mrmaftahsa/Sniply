"""TikTok playback selection: exclude watermarked download variants."""
import re
from urllib.parse import urlparse


def canonical_url(url):
    parsed = urlparse(url)
    if parsed.hostname == 'tiktok.com' or (parsed.hostname or '').endswith('.tiktok.com'):
        match = re.fullmatch(r'/(@[^/]+/video/\d+)/?', parsed.path)
        if match:
            return 'https://www.tiktok.com/' + match.group(1)
    return url  # Leave short links to yt-dlp's redirect resolver.


def playback_selector(cap):
    def select(context):
        formats = []
        for fmt in context.get('formats', []):
            note = str(fmt.get('format_note') or '').lower()
            ident = str(fmt.get('format_id') or '').lower()
            codec = str(fmt.get('vcodec') or '').lower()
            if 'watermark' in note or 'unplayable' in note or ident.startswith('download'):
                continue
            if codec in ('none', 'bytevc2') or fmt.get('acodec') == 'none':
                continue
            if fmt.get('ext') != 'mp4':
                continue
            if fmt.get('height') is not None and fmt['height'] > cap:
                continue
            formats.append(fmt)
        if not formats:
            raise ValueError('TikTok did not provide a watermark-free playback version at this quality. No watermarked copy was saved.')
        # Prefer widely supported H.264, retaining yt-dlp's quality ordering.
        compatible = [f for f in formats if str(f.get('vcodec', '')).startswith(('h264', 'avc'))]
        yield (compatible or formats)[-1]
    return select
