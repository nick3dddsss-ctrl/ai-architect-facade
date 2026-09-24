#!/usr/bin/env python3
import html
import json
import os
import re
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from html.parser import HTMLParser

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DIR = os.path.join(ROOT, 'catalog-data')
POSTER_DIR = os.path.join(OUT_DIR, 'autoplay-posters')
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(POSTER_DIR, exist_ok=True)
OUT = os.path.join(OUT_DIR, 'autoplay.json')
UA = 'CineDeckRU/1.3 official-catalog'
BASE = 'https://www.mosfilm.ru'
CDN_POSTERS = 'https://cdn.jsdelivr.net/gh/nick3dddsss-ctrl/ai-architect-facade@cinedeck-tv-build/catalog-data/autoplay-posters'
RAW_POSTERS = 'https://raw.githubusercontent.com/nick3dddsss-ctrl/ai-architect-facade/cinedeck-tv-build/catalog-data/autoplay-posters'

INDEXES = [
    BASE + '/cinema/films/?tags=online',
    BASE + '/cinema/films/?tags=online&releaseDate=2020',
    BASE + '/cinema/films/?tags=online&releaseDate=2010',
    BASE + '/cinema/films/?tags=online&releaseDate=2000',
    BASE + '/cinema/films/?tags=online&releaseDate=1990',
    BASE + '/cinema/films/?tags=online&releaseDate=1980',
    BASE + '/cinema/films/?tags=online&releaseDate=1970',
    BASE + '/cinema/films/?tags=online&releaseDate=1960',
    BASE + '/cinema/films/?tags=online&releaseDate=1950',
    BASE + '/cinema/films/?tags=online&genre=komediya',
]


def request(url):
    return urllib.request.Request(url, headers={
        'User-Agent': UA,
        'Accept': '*/*',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.5',
    })


def fetch(url, timeout=18):
    with urllib.request.urlopen(request(url), timeout=timeout) as r:
        return r.read().decode('utf-8', 'replace')


class LinkParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() != 'a':
            return
        href = dict(attrs).get('href')
        if not href:
            return
        absolute = urllib.parse.urljoin(BASE, href)
        parsed = urllib.parse.urlparse(absolute)
        if re.fullmatch(r'/cinema/films/[^/]+/?', parsed.path or ''):
            self.links.append(urllib.parse.urlunparse((parsed.scheme, parsed.netloc, parsed.path, '', '', '')))


def clean_text(s):
    if not s:
        return ''
    s = re.sub(r'<script\b[^>]*>.*?</script>', ' ', s, flags=re.I | re.S)
    s = re.sub(r'<style\b[^>]*>.*?</style>', ' ', s, flags=re.I | re.S)
    s = re.sub(r'<[^>]+>', ' ', s)
    s = html.unescape(s)
    return re.sub(r'\s+', ' ', s).strip()


def meta(doc, key, prop=False):
    attr = 'property' if prop else 'name'
    patterns = [
        rf'<meta[^>]+{attr}=["\']{re.escape(key)}["\'][^>]+content=["\']([^"\']+)["\']',
        rf'<meta[^>]+content=["\']([^"\']+)["\'][^>]+{attr}=["\']{re.escape(key)}["\']',
    ]
    for p in patterns:
        m = re.search(p, doc, re.I)
        if m:
            return html.unescape(m.group(1)).strip()
    return ''


def safe_name(value):
    return re.sub(r'[^A-Za-z0-9._-]+', '_', str(value or 'poster'))[:120] or 'poster'


def attr_value(tag, name):
    m = re.search(rf'\b{re.escape(name)}\s*=\s*(["\'])(.*?)\1', tag, re.I | re.S)
    return html.unescape(m.group(2)).strip() if m else ''


def image_candidates(doc, page_url, title):
    candidates = []

    def add(url, score, context=''):
        if not url:
            return
        url = html.unescape(url).strip()
        if url.startswith('//'):
            url = 'https:' + url
        url = urllib.parse.urljoin(page_url, url)
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme not in ('http', 'https'):
            return
        low = (url + ' ' + context).lower()
        if any(x in low for x in ('sprite', 'favicon', 'logo.svg', 'icon-', 'social-', 'counter', 'pixel', 'captcha')):
            score -= 120
        if any(x in low for x in ('poster', 'afisha', 'film', 'cinema', 'preview', 'cover', 'detail')):
            score += 25
        candidates.append((score, url))

    for key in ('og:image', 'twitter:image', 'twitter:image:src'):
        value = meta(doc, key, prop=(key == 'og:image'))
        if value:
            add(value, 150, key)

    for m in re.finditer(r'<link\b[^>]*>', doc, re.I | re.S):
        tag = m.group(0)
        rel = attr_value(tag, 'rel').lower()
        if 'image_src' in rel or 'preload' in rel:
            add(attr_value(tag, 'href'), 120 if 'image_src' in rel else 30, tag)

    title_norm = re.sub(r'\s+', ' ', title.lower()).strip()
    for m in re.finditer(r'<img\b[^>]*>', doc, re.I | re.S):
        tag = m.group(0)
        context = ' '.join([
            attr_value(tag, 'class'),
            attr_value(tag, 'alt'),
            attr_value(tag, 'title'),
        ])
        score = 45
        c_low = context.lower()
        if title_norm and title_norm in c_low:
            score += 100
        if any(x in c_low for x in ('poster', 'afisha', 'film', 'cinema', 'cover', 'preview', 'detail')):
            score += 60
        if any(x in c_low for x in ('logo', 'icon', 'avatar', 'person', 'actor', 'director')):
            score -= 80
        for name in ('data-src', 'data-original', 'data-lazy-src', 'src'):
            add(attr_value(tag, name), score + (15 if name != 'src' else 0), context)
        srcset = attr_value(tag, 'srcset') or attr_value(tag, 'data-srcset')
        if srcset:
            parts = [p.strip().split(' ')[0] for p in srcset.split(',') if p.strip()]
            if parts:
                add(parts[-1], score + 20, context)

    for p in (
        r'["\'](?:poster|posterUrl|image|imageUrl|preview|cover)["\']\s*:\s*["\']([^"\']+)["\']',
        r'(https?://[^"\'\s<>]+?\.(?:jpg|jpeg|png|webp)(?:\?[^"\'\s<>]*)?)',
    ):
        for m in re.finditer(p, doc, re.I):
            add(m.group(1).replace('\\/', '/'), 35, 'inline')

    seen = set()
    result = []
    for score, url in sorted(candidates, key=lambda x: x[0], reverse=True):
        if url not in seen:
            seen.add(url)
            result.append((score, url))
    return result


def cache_poster(item_id, doc, page_url, title):
    filename = safe_name(item_id) + '.img'
    path = os.path.join(POSTER_DIR, filename)

    if os.path.exists(path) and os.path.getsize(path) >= 5000:
        return f'{CDN_POSTERS}/{filename}', f'{RAW_POSTERS}/{filename}'

    for score, source_url in image_candidates(doc, page_url, title)[:12]:
        try:
            with urllib.request.urlopen(request(source_url), timeout=15) as r:
                content_type = (r.headers.get('Content-Type') or '').lower()
                data = r.read(3_000_000)
            if len(data) < 5000:
                continue
            if 'image/' not in content_type and not re.search(r'\.(jpg|jpeg|png|webp)(?:\?|$)', source_url, re.I):
                continue
            with open(path, 'wb') as f:
                f.write(data)
            print('POSTER', title, score, source_url, flush=True)
            return f'{CDN_POSTERS}/{filename}', f'{RAW_POSTERS}/{filename}'
        except Exception:
            continue

    print('NO_POSTER', title, flush=True)
    return '', ''


def parse_film(url):
    doc = fetch(url)
    text = clean_text(doc)
    if 'Смотреть фильм' not in text and 'Смотреть онлайн' not in text:
        return None

    title = meta(doc, 'og:title', True)
    if not title:
        m = re.search(r'<h1[^>]*>(.*?)</h1>', doc, re.I | re.S)
        title = clean_text(m.group(1)) if m else ''
    title = re.sub(r'\s*[|—-]\s*Киноконцерн.*$', '', title, flags=re.I).strip()
    if not title:
        return None

    desc = clean_text(meta(doc, 'description') or meta(doc, 'og:description', True))

    year = ''
    m = re.search(r'Дата\s+выхода\s*:?\s*((?:19|20)\d{2})', text, re.I)
    if not m:
        m = re.search(r'\b((?:19|20)\d{2})\b', text)
    if m:
        year = m.group(1)

    rating = ''
    m = re.search(r'Рейтинг\s*([0-9]+(?:[.,][0-9]+)?)', text, re.I)
    if m:
        rating = m.group(1).replace(',', '.')

    genres = ''
    m = re.search(r'Жанры\s*:?\s*(.{2,80}?)(?:Дата\s+выхода|Год\s+реставрации|Длительность)', text, re.I)
    if m:
        genres = clean_text(m.group(1))[:80]

    slug = urllib.parse.urlparse(url).path.rstrip('/').split('/')[-1]
    item_id = 'mosfilm:' + slug
    poster, poster_raw = cache_poster(item_id, doc, url, title)

    return {
        'id': item_id,
        'type': 'movie',
        'name': title,
        'year': year,
        'rating': rating,
        'genres': genres,
        'description': desc or 'Бесплатный официальный просмотр на сайте Киноконцерна «Мосфильм».',
        'posterUrl': poster,
        'posterRawUrl': poster_raw,
        'provider': 'Мосфильм',
        'playMode': 'web',
        'pageUrl': url,
        'streamUrl': '',
        'freeOfficial': True,
    }


def main():
    urls = []
    seen = set()
    for index in INDEXES:
        try:
            parser = LinkParser()
            parser.feed(fetch(index))
            for u in parser.links:
                if u not in seen:
                    seen.add(u)
                    urls.append(u)
        except Exception as e:
            print('index error', index, e, flush=True)

    urls = urls[:180]
    print('candidate urls:', len(urls), flush=True)
    items_by_url = {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(parse_film, u): u for u in urls}
        for future in as_completed(futures):
            u = futures[future]
            try:
                item = future.result()
                if item:
                    items_by_url[u] = item
                    print('OK', item['name'], flush=True)
            except Exception as e:
                print('film error', u, e, flush=True)

    items = [items_by_url[u] for u in urls if u in items_by_url]

    if len(items) < 10:
        if os.path.exists(OUT):
            print('too few new items, keeping existing autoplay feed:', len(items), flush=True)
            return
        raise SystemExit('Autoplay catalog too small: ' + str(len(items)))

    payload = {
        'version': 2,
        'generatedBy': 'CineDeck RU official zero-config provider',
        'provider': 'Мосфильм',
        'count': len(items),
        'posterCount': sum(1 for x in items if x.get('posterUrl')),
        'items': items,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print('autoplay items:', len(items), flush=True)
    print('posters:', payload['posterCount'], flush=True)


if __name__ == '__main__':
    main()
