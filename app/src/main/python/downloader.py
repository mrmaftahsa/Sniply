"""Download public videos without a hosted proxy or external ffmpeg binary."""
import os
import re
from urllib.parse import urlparse
import yt_dlp

HOSTS = ('youtube.com', 'youtu.be', 'instagram.com', 'facebook.com', 'fb.watch',
         'fb.com', 'x.com', 'twitter.com', 'pinterest.com', 'pin.it', 'reddit.com',
         'redd.it', 'tiktok.com')

def extract_link(text):
    for match in re.findall(r'https?://[^\s<>"\u200b]+', text):
        candidate = match.rstrip('.,);!?]')
        parsed = urlparse(candidate)
        host = (parsed.hostname or '').lower()
        if parsed.scheme == 'https' and any(host == h or host.endswith('.' + h) for h in HOSTS):
            return candidate
    raise ValueError('Paste an HTTPS video link from one of the seven supported platforms.')

def download(text, folder, quality, progress):
    url = extract_link(text)
    cap = {'Data saver (360p)': 360, 'Balanced (720p)': 720, 'Best available': 2160}[quality]
    def hook(data):
        if data['status'] == 'downloading':
            total = data.get('total_bytes') or data.get('total_bytes_estimate')
            progress.onProgress('Downloading: ' + (str(int(data.get('downloaded_bytes', 0) * 100 / total)) + '%' if total else 'in progress'))
    options = {
        'outtmpl': os.path.join(folder, '%(id)s.%(ext)s'),
        'format': f'best[ext=mp4][height<={cap}][vcodec!=none][acodec!=none]/best[height<={cap}][vcodec!=none][acodec!=none]',
        'noplaylist': True, 'playlist_items': '1', 'socket_timeout': 30,
        'retries': 3, 'quiet': True, 'no_warnings': True,
        'progress_hooks': [hook], 'max_filesize': 1024 * 1024 * 1024,
    }
    with yt_dlp.YoutubeDL(options) as engine:
        info = engine.extract_info(url, download=True)
        if info.get('_type') in ('playlist', 'multi_video'):
            info = next((entry for entry in info.get('entries', []) if entry), None)
            if not info:
                raise ValueError('No downloadable video found.')
        path = engine.prepare_filename(info)
        if not os.path.isfile(path):
            raise ValueError('This post did not return a downloadable video.')
        return path

def engine_version():
    return yt_dlp.version.__version__
