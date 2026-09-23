#!/usr/bin/env python3
import json, os, re, urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DIR = os.path.join(ROOT, 'catalog-data')
os.makedirs(OUT_DIR, exist_ok=True)
BASE = 'https://v3-cinemeta.strem.io'
UA = 'CineDeckRU-Catalog/1.1.3'


def get_json(url, timeout=20):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json,*/*'})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode('utf-8'))


def clean(s):
    if not s:
        return ''
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', str(s))).strip()


def year_of(meta):
    raw = str(meta.get('releaseInfo') or meta.get('year') or '')
    m = re.search(r'(19|20)\d{2}', raw)
    return m.group(0) if m else ''


def fetch_kind(kind, limit=60):
    data = get_json(f'{BASE}/catalog/{kind}/top.json')
    out = []
    for m in data.get('metas') or []:
        name = clean(m.get('name'))
        if not name:
            continue
        genres = m.get('genres') or []
        if not isinstance(genres, list):
            genres = []
        out.append({
            'id': str(m.get('id') or ''),
            'type': kind,
            'name': name,
            'year': year_of(m),
            'rating': str(m.get('imdbRating') or m.get('rating') or ''),
            'description': clean(m.get('description') or 'Описание отсутствует.'),
            'genres': [clean(x) for x in genres[:4] if clean(x)],
            'posterUrl': str(m.get('poster') or ''),
            'backgroundUrl': str(m.get('background') or '')
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
    payload = {
        'version': 2,
        'generatedBy': 'CineDeck RU online catalog updater',
        'count': len(items),
        'items': items,
        'errors': errors,
    }
    with open(os.path.join(OUT_DIR, 'catalog.json'), 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print('online catalog items:', len(items))
    if errors:
        print('errors:', errors)
    if len(items) < 20:
        raise SystemExit('Catalog too small; refusing to overwrite remote feed')

if __name__ == '__main__':
    main()
