"""Run each public URL through Sniply on Android; collect reports without saving videos."""
import argparse
import json
import os
import re
import shlex
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

DOMAINS = {
    'youtube': ('youtube.com', 'youtu.be'), 'instagram': ('instagram.com',),
    'facebook': ('facebook.com', 'fb.com', 'fb.watch'), 'x': ('x.com', 'twitter.com'),
    'pinterest': ('pinterest.com', 'pin.it'), 'reddit': ('reddit.com', 'redd.it'), 'tiktok': ('tiktok.com',),
}
QUALITIES = ('Best available', 'Balanced (720p)', 'Data saver (360p)')


def validate(suite):
    if not isinstance(suite, dict) or suite.get('version') != 1:
        raise ValueError('Expected a version 1 Sniply test batch')
    if not isinstance(suite.get('id'), str) or not re.fullmatch(r'[a-zA-Z0-9-]{1,60}', suite['id']):
        raise ValueError('Invalid batch ID')
    if suite.get('quality') not in QUALITIES:
        raise ValueError('Invalid quality')
    platforms = suite.get('platforms')
    if not isinstance(platforms, dict) or set(platforms) - set(DOMAINS):
        raise ValueError('Unknown platform')
    cases = []
    for platform, domains in DOMAINS.items():
        links = platforms.get(platform, [])
        if not isinstance(links, list) or len(links) > 6:
            raise ValueError('Use at most six links per platform')
        for url in links:
            if not isinstance(url, str) or len(url) > 1400 or any(ord(c) < 32 for c in url):
                raise ValueError('Invalid URL')
            parsed = urlparse(url)
            host = (parsed.hostname or '').lower()
            if parsed.scheme != 'https' or parsed.username or parsed.password or parsed.port not in (None, 443) or not any(host == d or host.endswith('.' + d) for d in domains):
                raise ValueError('Link does not belong to ' + platform)
            if url in [c['url'] for c in cases]:
                raise ValueError('Duplicate URL')
            cases.append({'platform': platform, 'url': url})
    if not cases:
        raise ValueError('Add at least one video link')
    return cases


def command(*args, **kwargs):
    return subprocess.run(['adb', '-s', 'emulator-5554', *args], capture_output=True, text=True, **kwargs)


def run(suite, output):
    cases = validate(suite)
    report = {'version': 1, 'id': suite['id'], 'quality': suite['quality'], 'source_sha': os.getenv('GITHUB_SHA', ''),
              'run_id': os.getenv('GITHUB_RUN_ID', ''), 'environment': 'Android 15 x86_64 emulator on GitHub Actions',
              'created_at': datetime.now(timezone.utc).isoformat(), 'results': []}
    for index, case in enumerate(cases):
        started = time.monotonic()
        try:
            process = command('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'com.sniply.app.LiveLinkTest',
                              '-e', 'video_url', shlex.quote(case['url']), '-e', 'quality', shlex.quote(suite['quality']),
                              'com.sniply.app.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
            # adb shell joins arguments; quote the user-controlled URL and quality for its remote shell.
            matches = [line.split('sniply_result=', 1)[1] for line in process.stdout.splitlines() if line.startswith('INSTRUMENTATION_STATUS: sniply_result=')]
            if not matches:
                raise RuntimeError('Android test did not return a result. ' + process.stdout[-600:])
            result = json.loads(matches[-1])
        except subprocess.TimeoutExpired:
            result = {'status': 'failed', 'error': 'Test exceeded the 3 minute limit; the Android process was stopped.'}
        except Exception as error:
            result = {'status': 'failed', 'error': str(error)[:1200]}
        finally:
            try:
                command('shell', 'am', 'force-stop', 'com.sniply.app', timeout=20)
                command('shell', 'pm', 'clear', 'com.sniply.app', timeout=20)
            except Exception:
                result['note'] = 'Emulator cleanup was interrupted; inspect the workflow logs.'
        report['results'].append({**case, **result, 'elapsed_ms': result.get('elapsed_ms', int((time.monotonic() - started) * 1000))})
        Path(output).write_text(json.dumps(report, indent=2))
        print(f"Case {index + 1}/{len(cases)} ({case['platform']}): {result['status']}", flush=True)
    passed = sum(r['status'] == 'passed' for r in report['results'])
    summary = f"## Sniply Link Lab\n\n{passed} passed · {len(cases) - passed} failed · {len(cases)} tested.\n\nDownload the **Sniply-link-report** artifact and import its ZIP into the test dashboard. A green workflow means the runner completed; inspect the report for per-link failures. Videos are deleted after testing. GitHub network restrictions may differ from your phone.\n"
    if os.getenv('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as stream:
            stream.write(summary)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--output', default='sniply-report.json')
    args = parser.parse_args()
    suite = json.loads(os.environ['SUITE_JSON'])
    if args.validate:
        print(f'Validated {len(validate(suite))} video links')
        Path('live-suite.json').write_text(json.dumps(suite))
    else:
        run(suite, args.output)
