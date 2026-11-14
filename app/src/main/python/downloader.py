"""Download public videos without a hosted proxy or external ffmpeg binary.

Strategy, in order:
1. One file that already holds audio and video (works for TikTok, X, Facebook,
   Instagram, Pinterest and many Reddit/YouTube links).
2. Separate H.264 video + AAC audio files. The Android app joins them with
   MediaMuxer, so no ffmpeg binary is needed.
"""
import os
import re
from urllib.parse import urlparse
import yt_dlp

HOSTS = ('youtube.com', 'youtu.be', 'instagram.com', 'facebook.com', 'fb.watch',
         'fb.com', 'x.com', 'twitter.com', 'pinterest.com', 'pin.it', 'reddit.com',
         'redd.it', 'tiktok.com')

ANSI = re.compile(r'\x1b\[[0-9;]*m')


def extract_link(text):
    for match in re.findall(r'https?://[^\s<>"​]+', text):
        candidate = match.rstrip('.,);!?]')
        parsed = urlparse(candidate)
        host = (parsed.hostname or '').lower()
        if parsed.scheme == 'https' and any(host == h or host.endswith('.' + h) for h in HOSTS):
            return candidate
    raise ValueError('Paste an HTTPS video link from one of the seven supported platforms.')


def clean_error(error):
    text = ANSI.sub('', str(error)).strip()
    text = re.sub(r'^ERROR:\s*', '', text)
    return text[:500] or error.__class__.__name__


def combined_selector(cap):
    # "b" already means one file with audio and video. Do not add
    # vcodec!=none / acodec!=none: many extractors leave codecs unknown, and
    # a strict filter then rejects every format. "<=?" keeps formats whose
    # height is unknown instead of dropping them.
    return f'b[ext=mp4][height<=?{cap}]/b[height<=?{cap}]/b'


def video_selector(cap):
    return f'bv[vcodec^=avc1][height<=?{cap}]/bv[vcodec~="^(avc|h264)"][height<=?{cap}]'


AUDIO_SELECTOR = 'ba[ext=m4a]/ba[acodec^=mp4a]'


def fetch(url, folder, selector, tag, hook):
    options = {
        'outtmpl': os.path.join(folder, f'%(id)s.{tag}.%(ext)s'),
        'format': selector,
        'noplaylist': True, 'playlist_items': '1', 'socket_timeout': 30,
        'retries': 3, 'quiet': True, 'no_warnings': True, 'noprogress': True,
        'progress_hooks': [hook], 'max_filesize': 1024 * 1024 * 1024,
    }
    with yt_dlp.YoutubeDL(options) as engine:
        info = engine.extract_info(url, download=True)
        if info and info.get('_type') in ('playlist', 'multi_video'):
            info = next((entry for entry in info.get('entries', []) if entry), None)
        if not info:
            raise ValueError('No downloadable video found.')
        downloads = info.get('requested_downloads') or []
        path = downloads[0].get('filepath') if downloads else engine.prepare_filename(info)
        if not path or not os.path.isfile(path):
            raise ValueError('The file was skipped (too large or not downloadable).')
        return path


def download(text, folder, quality, progress):
    """Return a Python list: [file] or [video_file, audio_file]."""
    url = extract_link(text)
    cap = {'Data saver (360p)': 360, 'Balanced (720p)': 720, 'Best available': 2160}[quality]

    def hook(data):
        if data['status'] == 'downloading':
            total = data.get('total_bytes') or data.get('total_bytes_estimate')
            progress.onProgress('Downloading: ' + (str(int(data.get('downloaded_bytes', 0) * 100 / total)) + '%' if total else 'in progress'))

    errors = []
    try:
        return [fetch(url, folder, combined_selector(cap), 'single', hook)]
    except Exception as error:
        errors.append(clean_error(error))
    try:
        video = fetch(url, folder, video_selector(cap), 'video', hook)
        audio = fetch(url, folder, AUDIO_SELECTOR, 'audio', hook)
        return [video, audio]
    except Exception as error:
        errors.append(clean_error(error))
    unique = []
    for message in errors:
        if message not in unique:
            unique.append(message)
    raise ValueError(' | '.join(unique))


def engine_version():
    return yt_dlp.version.__version__
