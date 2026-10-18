"""Opt-in live Execute Webhook boundary probe (posts test messages).

Run from the repository root: python scripts/measure_discord_embeds.py [variant ...]
Reads only DISCORD_WEBHOOK_URL from .env; never prints the URL or token.
Results are written to ignored build/embed-measurement/results.json.
"""
import json
import pathlib
import time
import urllib.request
import urllib.error
import sys

values = dict(line.strip().split('=', 1) for line in pathlib.Path('.env').read_text(encoding='utf-8-sig').splitlines()
              if line.strip() and not line.lstrip().startswith('#') and '=' in line)
url = values['DISCORD_WEBHOOK_URL'].strip().strip('"\'')
url += ('&' if '?' in url else '?') + 'wait=true'
results = []
pathlib.Path('build/embed-measurement').mkdir(parents=True, exist_ok=True)

def send(payload, escaped=False):
    data = json.dumps(payload, ensure_ascii=escaped, separators=(',', ':')).encode('utf-8')
    for attempt in range(6):
        req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json', 'User-Agent': 'EEWBot embed boundary test'})
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                status = response.status
                response.read()
        except urllib.error.HTTPError as error:
            status = error.code
            body = error.read().decode()
            if status == 429:
                time.sleep(float(json.loads(body).get('retry_after', 2)) + .2)
                continue
            if status not in (413, 500):
                raise RuntimeError(f'Unexpected HTTP {status}: {body[:400]}') from None
        except (urllib.error.URLError, TimeoutError):
            raise RuntimeError('Webhook connection failed (URL and token omitted)') from None
        time.sleep(.45)
        return status, len(data)
    raise RuntimeError('Rate limit retries exhausted')

def filler(n):
    return '\u3042' * (n // 3) + 'a' * (n % 3)

def make(n, variant):
    embed = {'description': filler(n)}
    payload = {'embeds': [embed]}
    if variant.startswith('fields'):
        count = int(variant[6:])
        embed['fields'] = [{'name': 'n', 'value': 'v', 'inline': False} for _ in range(count)]
    elif variant.startswith('embeds'):
        payload['embeds'] += [{'description': 'v'} for _ in range(int(variant[6:]) - 1)]
    elif variant == 'color': embed['color'] = 16711680
    elif variant == 'timestamp': embed['timestamp'] = '2026-10-19T00:00:00Z'
    elif variant == 'title': embed['title'] = 'title'
    elif variant == 'footer': embed['footer'] = {'text': 'footer'}
    elif variant == 'author': embed['author'] = {'name': 'author'}
    elif variant == 'url': embed['url'] = 'https://example.com/a'
    elif variant == 'image': embed['image'] = {'url': 'https://example.com/a.png'}
    elif variant == 'thumbnail': embed['thumbnail'] = {'url': 'https://example.com/a.png'}
    elif variant == 'footerIcon': embed['footer'] = {'text': 'footer', 'icon_url': 'https://example.com/a.png'}
    elif variant == 'authorUrls': embed['author'] = {'name': 'author', 'url': 'https://example.com/a', 'icon_url': 'https://example.com/a.png'}
    elif variant == 'metadata':
        payload['username'] = 'EEWBot'
        payload['avatar_url'] = 'https://example.com/a.png'
    elif variant == 'content': payload['content'] = 'hello'
    elif variant == 'color0': embed['color'] = 0
    elif variant == 'fieldLong': embed['fields'] = [{'name': 'n' * 256, 'value': filler(3072), 'inline': False}]
    elif variant == 'field128': embed['fields'] = [{'name': 'n' * 128, 'value': 'v' * 128, 'inline': True}]
    elif variant == 'field127': embed['fields'] = [{'name': 'n' * 127, 'value': 'v' * 127, 'inline': False}]
    elif variant == 'imageResolved': embed['image'] = {'url': 'https://cdn.discordapp.com/embed/avatars/0.png'}
    elif variant == 'thumbnailResolved': embed['thumbnail'] = {'url': 'https://cdn.discordapp.com/embed/avatars/0.png'}
    elif variant == 'footerResolved': embed['footer'] = {'text': 'footer', 'icon_url': 'https://cdn.discordapp.com/embed/avatars/0.png'}
    elif variant == 'authorResolved': embed['author'] = {'name': 'author', 'icon_url': 'https://cdn.discordapp.com/embed/avatars/0.png'}
    elif variant == 'timestampNanos': embed['timestamp'] = '2026-10-19T00:00:00.123456789Z'
    elif variant == 'username1': payload['username'] = 'x'
    elif variant == 'usernameLong': payload['username'] = '\U0001f600' * 80
    elif variant == 'avatarResolved': payload['avatar_url'] = 'https://cdn.discordapp.com/embed/avatars/0.png'
    elif variant == 'usernameLongAvatar':
        payload['username'] = '\U0001f600' * 80
        payload['avatar_url'] = 'https://cdn.discordapp.com/embed/avatars/0.png'
    elif variant == 'escapes': embed['description'] = '\\"<>&=\'\n\t' * 50 + filler(n)
    return payload

variants = ['base', 'fields1', 'fields2', 'fields25', 'embeds2', 'embeds10', 'color',
            'timestamp', 'title', 'footer', 'author', 'url', 'image', 'thumbnail',
            'footerIcon', 'authorUrls', 'metadata', 'content', 'escapes', 'color0',
            'field127', 'field128', 'fieldLong', 'imageResolved', 'thumbnailResolved',
            'footerResolved', 'authorResolved', 'timestampNanos', 'username1',
            'usernameLong', 'avatarResolved', 'usernameLongAvatar']
for variant in sys.argv[1:] or variants:
    if variant not in variants:
        raise ValueError(f'Unknown variant: {variant}')
    lo, hi = 0, 11000
    while lo < hi:
        mid = (lo + hi + 1) // 2
        status, _ = send(make(mid, variant))
        if status == 200: lo = mid
        else: hi = mid - 1
    good, raw = send(make(lo, variant))
    bad, _ = send(make(lo + 1, variant))
    escaped, escaped_raw = send(make(lo, variant), True)
    if good != 200 or bad not in (413, 500) or escaped != 200:
        raise RuntimeError(f'{variant}: no reproducible one-byte boundary; inspect response statuses')
    result = dict(variant=variant, max_filler_bytes=lo, status=good, next_status=bad, raw_json_bytes=raw, escaped_status=escaped, escaped_json_bytes=escaped_raw)
    results.append(result)
    print(json.dumps(result), flush=True)
    pathlib.Path('build/embed-measurement/results.json').write_text(json.dumps(results, indent=2))
