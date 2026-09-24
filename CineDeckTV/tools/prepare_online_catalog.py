#!/usr/bin/env python3
import json, os, re, urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DIR = os.path.join(ROOT, 'catalog-data')
POSTER_DIR = os.path.join(OUT_DIR, 'posters')
os.makedirs(POSTER_DIR, exist_ok=True)
BASE = 'https://v3-cinemeta.strem.io'
UA = 'CineDeckRU-Catalog/1.1.5'
CDN_BASE = 'https://cdn.jsdelivr.net/gh/nick3dddsss-ctrl/ai-architect-facade@cinedeck-tv-build/catalog-data/posters'
RAW_BASE = 'https://raw.githubusercontent.com/nick3dddsss-ctrl/ai-architect-facade/cinedeck-tv-build/catalog-data/posters'


def request(url, timeout=25):
    return urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': '*/*'})


def get_json(url, timeout=25):
    with urllib.request.urlopen(request(url), timeout=timeout) as r:
        return json.loads(r.read().decode('utf-8'))


def clean(s):
    if not s:
        return ''
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', str(s))).strip()


def year_of(meta):
    raw = str(meta.get('releaseInfo') or meta.get('year') or '')
    m = re.search(r'(19|20)\d{2}', raw)
    return m.group(0) if m else ''


def safe_id(value):
    value = re.sub(r'[^A-Za-z0-9._-]+', '_', str(value or 'item'))
    return value[:100] or 'item'


def cache_poster(item_id, source_url):
    if not source_url:
        return '', ''
    name = safe_id(item_id) + '.img'
    path = os.path.join(POSTER_DIR, name)
    if not os.path.exists(path) or os.path.getsize(path) < 1000:
        try:
            with urllib.request.urlopen(request(source_url), timeout=30) as r:
                data = r.read(2_500_000)
            if len(data) >= 1000:
                with open(path, 'wb') as f:
                    f.write(data)
        except Exception as e:
            print('poster error', item_id, e)
    if os.path.exists(path) and os.path.getsize(path) >= 1000:
        return f'{CDN_BASE}/{name}', f'{RAW_BASE}/{name}'
    return source_url, ''


def fetch_kind(kind, limit=60):
    data = get_json(f'{BASE}/catalog/{kind}/top.json')
    out = []
    for m in data.get('metas') or []:
        name = clean(m.get('name'))
        if not name:
            continue
        item_id = str(m.get('id') or '')
        genres = m.get('genres') or []
        if not isinstance(genres, list):
            genres = []
        poster, poster_raw = cache_poster(item_id, str(m.get('poster') or ''))
        out.append({
            'id': item_id,
            'type': kind,
            'name': name,
            'year': year_of(m),
            'rating': str(m.get('imdbRating') or m.get('rating') or ''),
            'description': clean(m.get('description') or 'Описание отсутствует.'),
            'genres': [clean(x) for x in genres[:4] if clean(x)],
            'posterUrl': poster,
            'posterRawUrl': poster_raw,
            'backgroundUrl': ''
        })
        if len(out) >= limit:
            break
    return out


def main():
    items = []
    errors = []
    for kind in ('movie', 'series'):
        try:
            items.extend(fetch_kind(kind))
        except Exception as e:
            errors.append(f'{kind}: {e}')
    if len(items) < 20:
        raise SystemExit('Catalog too small; refusing to overwrite remote feed')
    payload = {
        'version': 3,
        'generatedBy': 'CineDeck RU stable online catalog updater',
        'count': len(items),
        'items': items,
        'errors': errors,
    }
    with open(os.path.join(OUT_DIR, 'catalog.json'), 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print('online catalog items:', len(items))
    print('cached posters:', len([x for x in os.listdir(POSTER_DIR) if os.path.isfile(os.path.join(POSTER_DIR, x))]))
    if errors:
        print('errors:', errors)

if __name__ == '__main__':
    main()
