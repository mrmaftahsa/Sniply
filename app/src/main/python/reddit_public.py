"""Resolve Reddit-hosted video through Reddit's public embed metadata."""
import json
import re
from html.parser import HTMLParser
from urllib.parse import urlparse
from urllib.request import Request, urlopen


class EmbedMetadata(HTMLParser):
    def __init__(self, post_id):
        super().__init__()
        self.post_id, self.media_id = post_id, None

    def handle_starttag(self, tag, attrs):
        if tag != 'shreddit-screenview-data':
            return
        try:
            post = json.loads(dict(attrs).get('data', '{}')).get('post', {})
            if post.get('id') != 't3_' + self.post_id:
                return
            match = re.fullmatch(r'https://v\.redd\.it/([a-zA-Z0-9]+)/?', post.get('url', ''))
            if match:
                self.media_id = match.group(1)
        except (ValueError, TypeError, AttributeError):
            pass


def manifest_for(url):
    """Return a public MPD URL, or None when this post exposes no native video."""
    parsed = urlparse(url)
    headers = {'User-Agent': 'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36'}
    if '/comments/' not in parsed.path:
        with urlopen(Request(url, headers=headers), timeout=15) as response:
            parsed = urlparse(response.geturl())
    if parsed.scheme != 'https' or not (parsed.hostname == 'reddit.com' or (parsed.hostname or '').endswith('.reddit.com')):
        return None
    match = re.search(r'/comments/([a-zA-Z0-9]+)(?:/|$)', parsed.path)
    if not match:
        return None
    with urlopen(Request('https://embed.reddit.com' + parsed.path, headers=headers), timeout=15) as response:
        page = response.read(2 * 1024 * 1024).decode('utf-8', errors='replace')
    parser = EmbedMetadata(match.group(1))
    parser.feed(page)
    if parser.media_id:
        return 'https://v.redd.it/' + parser.media_id + '/DASHPlaylist.mpd'
    return None
