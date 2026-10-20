"""Opt-in live Execute Webhook / Message Create boundary probe (posts test messages).

Run from the repository root: python scripts/measure_discord_embeds.py [variant ...]
--transport both compares both endpoints in the designated webhook's channel.
Reads DISCORD_WEBHOOK_URL and optionally DISCORD_BOT_TOKEN from .env; Bot token
falls back to config.json base.discordToken only when Message Create is requested.
Never prints the URL or token. Results go to ignored build/embed-measurement/.
"""
import json
import pathlib
import time
import urllib.request
import urllib.error
import argparse

values = dict(line.strip().split('=', 1) for line in pathlib.Path('.env').read_text(encoding='utf-8-sig').splitlines()
              if line.strip() and not line.lstrip().startswith('#') and '=' in line)
url = values['DISCORD_WEBHOOK_URL'].strip().strip('"\'')
url += ('&' if '?' in url else '?') + 'wait=true'
pathlib.Path('build/embed-measurement').mkdir(parents=True, exist_ok=True)

def send(payload, escaped=False, endpoint=None, token=None):
    data = json.dumps(payload, ensure_ascii=escaped, separators=(',', ':')).encode('utf-8')
    for attempt in range(6):
        headers = {'Content-Type': 'application/json', 'User-Agent': 'EEWBot embed boundary test'}
        if token:
            headers['Authorization'] = 'Bot ' + token
        req = urllib.request.Request(endpoint or url, data=data, headers=headers)
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
    elif variant == 'baseEmoji': embed['description'] = '\U0001f600' * (n // 4) + 'a' * (n % 4)
    elif variant == 'combined':
        image = 'https://cdn.discordapp.com/embed/avatars/0.png'
        embed.update(title='title', color=16711680, timestamp='2026-10-19T00:00:00.123456789Z',
                     url='https://example.com/a', thumbnail={'url': image}, image={'url': image},
                     footer={'text': 'footer', 'icon_url': image},
                     author={'name': 'author', 'url': 'https://example.com/a', 'icon_url': image},
                     fields=[{'name': 'n' * 256, 'value': filler(3072), 'inline': True}])
    return payload

variants = ['base', 'fields1', 'fields2', 'fields25', 'embeds2', 'embeds10', 'color',
            'timestamp', 'title', 'footer', 'author', 'url', 'image', 'thumbnail',
            'footerIcon', 'authorUrls', 'metadata', 'content', 'escapes', 'color0',
            'field127', 'field128', 'fieldLong', 'imageResolved', 'thumbnailResolved',
            'footerResolved', 'authorResolved', 'timestampNanos', 'username1',
            'usernameLong', 'avatarResolved', 'usernameLongAvatar', 'baseEmoji', 'combined']


def text_characters(payload):
    total = len(payload.get('content', ''))
    for embed in payload['embeds']:
        total += sum(len(embed.get(key, '')) for key in ('title', 'description'))
        total += len(embed.get('footer', {}).get('text', '')) + len(embed.get('author', {}).get('name', ''))
        total += sum(len(field['name']) + len(field['value']) for field in embed.get('fields', []))
    return total


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--transport', choices=('webhook', 'message', 'both'), default='webhook')
    parser.add_argument('--seed', type=pathlib.Path, help='Prior webhook results to accelerate bracketing; all boundaries are rechecked')
    parser.add_argument('variants', nargs='*', choices=variants)
    args = parser.parse_args()
    seeds = {row['variant']: row['max_filler_bytes'] for row in json.loads(args.seed.read_text())
             if row.get('transport', 'webhook') == 'webhook'} if args.seed else {}
    endpoints = {'webhook': (url, None)}
    if args.transport != 'webhook':
        token = values.get('DISCORD_BOT_TOKEN', '').strip().strip('"\'')
        if not token:
            token = json.loads(pathlib.Path('config.json').read_text(encoding='utf-8-sig'))['base']['discordToken']
        # Use the already designated test sink, including a webhook thread if supplied.
        from urllib.parse import urlsplit, parse_qs
        query = parse_qs(urlsplit(url).query)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'EEWBot embed boundary test'}), timeout=30) as response:
                channel = query.get('thread_id', [json.load(response)['channel_id']])[0]
        except Exception:
            raise RuntimeError('Could not resolve test channel (connection details omitted)') from None
        endpoints['message'] = ('https://discord.com/api/v10/channels/' + channel + '/messages', token)

    results = []
    for variant in args.variants or variants:
        webhook_boundary = None
        for transport in (('webhook', 'message') if args.transport == 'both' else (args.transport,)):
            if transport == 'message' and variant in ('metadata', 'username1', 'usernameLong', 'avatarResolved', 'usernameLongAvatar'):
                continue  # These overrides exist only on Execute Webhook.
            endpoint, token = endpoints[transport]
            bare = make(0, variant)
            prefix = len(bare['embeds'][0]['description'])
            max_chars = min(4096 - prefix, 6000 - text_characters(bare))
            maximum = max_chars * (4 if variant == 'baseEmoji' else 3)
            cache = {}
            def checked(n):
                if n not in cache:
                    cache[n] = send(make(n, variant), endpoint=endpoint, token=token)
                return cache[n]

            baseline = webhook_boundary if webhook_boundary is not None else seeds.get(variant, 10143)
            guess = min(maximum, baseline + (15 if transport == 'message' else 0))
            if checked(guess)[0] == 200:
                lo, hi = guess, min(maximum, guess + 1)
                while checked(hi)[0] == 200 and hi < maximum:
                    lo = hi
                    hi = min(maximum, hi + max(16, (hi - guess) * 2))
                if checked(hi)[0] == 200:
                    lo = hi
                else:
                    hi -= 1
            else:
                lo, hi = max(0, guess - 16), guess - 1
                while checked(lo)[0] != 200:
                    if lo == 0:
                        raise RuntimeError(f'{variant}: even the zero-filler control failed')
                    hi = lo - 1
                    lo = max(0, guess - (guess - lo) * 2)
            while lo < hi:
                mid = (lo + hi + 1) // 2
                status, _ = checked(mid)
                if status == 200:
                    lo = mid
                else:
                    hi = mid - 1
            good, raw = checked(lo)
            bad = None
            if lo < maximum:
                bad, _ = checked(lo + 1)
            escaped, escaped_raw = send(make(lo, variant), True, endpoint, token)
            if good != 200 or (bad is not None and bad not in (413, 500)) or escaped != 200:
                raise RuntimeError(f'{variant}: boundary changed; inspect response statuses')
            result = dict(transport=transport, variant=variant, max_filler_bytes=lo,
                          character_limit_reached=lo == maximum, status=good, next_status=bad,
                          raw_json_bytes=raw, escaped_status=escaped, escaped_json_bytes=escaped_raw)
            if transport == 'webhook':
                webhook_boundary = lo
            elif webhook_boundary is not None:
                result['at_webhook_boundary'] = [checked(n)[0]
                                                 for n in (webhook_boundary, webhook_boundary + 1) if n <= maximum]
            results.append(result)
            print(json.dumps(result), flush=True)
            pathlib.Path('build/embed-measurement/results-' + args.transport + '.json').write_text(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
