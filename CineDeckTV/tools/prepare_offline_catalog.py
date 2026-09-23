#!/usr/bin/env python3
import json, os, re, sys, urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
POSTERS = os.path.join(ASSETS, 'posters')
os.makedirs(POSTERS, exist_ok=True)

UA = 'CineDeckRU-Build/1.1.2'
BASE = 'https://v3-cinemeta.strem.io'


def get_bytes(url, timeout=20):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': '*/*'})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def get_json(url):
    return json.loads(get_bytes(url).decode('utf-8'))


def clean(s):
    if not s:
        return ''
    s = re.sub(r'<[^>]+>', ' ', str(s))
    return re.sub(r'\s+', ' ', s).strip()


def year_of(meta):
    raw = str(meta.get('releaseInfo') or meta.get('year') or '')
    m = re.search(r'(19|20)\d{2}', raw)
    return m.group(0) if m else ''


def add_type(kind, limit, out):
    url = f'{BASE}/catalog/{kind}/top.json'
    data = get_json(url)
    metas = data.get('metas') or []
    added = 0
    for idx, m in enumerate(metas):
        if added >= limit:
            break
        name = clean(m.get('name'))
        if not name:
            continue
        item_id = str(m.get('id') or f'{kind}_{idx}')
        genres = m.get('genres') or []
        if not isinstance(genres, list):
            genres = []
        poster_url = str(m.get('poster') or '')
        poster_asset = ''
        if poster_url:
            poster_name = f'{kind}_{added:02d}.jpg'
            try:
                blob = get_bytes(poster_url, timeout=20)
                if len(blob) > 2000:
                    with open(os.path.join(POSTERS, poster_name), 'wb') as f:
                        f.write(blob)
                    poster_asset = 'posters/' + poster_name
            except Exception as e:
                print('poster failed', name, e)
        out.append({
            'id': item_id,
            'type': kind,
            'name': name,
            'year': year_of(m),
            'rating': str(m.get('imdbRating') or m.get('rating') or ''),
            'description': clean(m.get('description') or 'Описание будет доступно при подключении к интернету.'),
            'genres': [clean(x) for x in genres[:4] if clean(x)],
            'posterAsset': poster_asset,
            'posterUrl': poster_url,
            'background': str(m.get('background') or '')
        })
        added += 1


def fallback_catalog():
    movies = [
        ('tt0111161','Побег из Шоушенка','1994'),('tt0068646','Крёстный отец','1972'),
        ('tt0468569','Тёмный рыцарь','2008'),('tt0109830','Форрест Гамп','1994'),
        ('tt0137523','Бойцовский клуб','1999'),('tt1375666','Начало','2010'),
        ('tt0816692','Интерстеллар','2014'),('tt0133093','Матрица','1999')]
    series = [
        ('tt0903747','Во все тяжкие','2008'),('tt0944947','Игра престолов','2011'),
        ('tt4574334','Очень странные дела','2016'),('tt1475582','Шерлок','2010'),
        ('tt7366338','Чернобыль','2019'),('tt3032476','Лучше звоните Солу','2015'),
        ('tt0141842','Клан Сопрано','1999'),('tt0386676','Офис','2005')]
    out=[]
    for kind, src in [('movie',movies),('series',series)]:
        for item_id,name,year in src:
            out.append({'id':item_id,'type':kind,'name':name,'year':year,'rating':'','description':'Встроенная резервная карточка. Для поиска доступности используйте российские сервисы.','genres':[],'posterAsset':'','posterUrl':'','background':''})
    return out


def main():
    out=[]
    errors=[]
    for kind in ('movie','series'):
        try:
            add_type(kind, 18, out)
        except Exception as e:
            errors.append(f'{kind}: {e}')
            print('catalog failed', kind, e)
    if len(out) < 12:
        out = fallback_catalog()
    payload = {
        'version': 1,
        'generatedBy': 'CineDeck RU build',
        'count': len(out),
        'items': out,
        'errors': errors,
    }
    with open(os.path.join(ASSETS, 'catalog.json'), 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print('offline catalog items:', len(out))
    print('poster files:', len(os.listdir(POSTERS)))

if __name__ == '__main__':
    main()
