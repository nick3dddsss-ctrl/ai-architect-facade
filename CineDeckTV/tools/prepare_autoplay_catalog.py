#!/usr/bin/env python3
import html
import json
import os
import re
import urllib.parse
import urllib.request
from html.parser import HTMLParser

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT_DIR = os.path.join(ROOT, 'catalog-data')
os.makedirs(OUT_DIR, exist_ok=True)
OUT = os.path.join(OUT_DIR, 'autoplay.json')
UA = 'CineDeckRU/1.3 official-catalog'
BASE = 'https://www.mosfilm.ru'

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


def fetch(url, timeout=25):
    req = urllib.request.Request(url, headers={
        'User-Agent': UA,
        'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en;q=0.5',
    })
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode('utf-8', 'replace')


class LinkParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
    def handle_starttag(self, tag, attrs):
        if tag.lower() != 'a':
            return
        href = dict(attrs).get('href')
        if href and '/cinema/films/' in href and href.rstrip('/').split('/')[-1] != 'films':
            self.links.append(urllib.parse.urljoin(BASE, href.split('#')[0]))


def clean_text(s):
    if not s:
        return ''
    s = re.sub(r'<script\b[^>]*>.*?</script>', ' ', s, flags=re.I|re.S)
    s = re.sub(r'<style\b[^>]*>.*?</style>', ' ', s, flags=re.I|re.S)
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


def parse_film(url):
    doc = fetch(url)
    text = clean_text(doc)
    if 'Смотреть фильм' not in text and 'Смотреть онлайн' not in text:
        return None

    title = meta(doc, 'og:title', True)
    if not title:
        m = re.search(r'<h1[^>]*>(.*?)</h1>', doc, re.I|re.S)
        title = clean_text(m.group(1)) if m else ''
    title = re.sub(r'\s*[|—-]\s*Киноконцерн.*$', '', title, flags=re.I).strip()
    if not title:
        return None

    desc = meta(doc, 'description') or meta(doc, 'og:description', True)
    desc = clean_text(desc)
    poster = meta(doc, 'og:image', True)
    poster = urllib.parse.urljoin(BASE, poster) if poster else ''

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
    m = re.search(r'Жанры\s*:\s*(.{2,80}?)(?:Дата\s+выхода|Год\s+реставрации|Длительность)', text, re.I)
    if m:
        genres = clean_text(m.group(1))[:80]

    slug = url.rstrip('/').split('/')[-1]
    return {
        'id': 'mosfilm:' + slug,
        'type': 'movie',
        'name': title,
        'year': year,
        'rating': rating,
        'genres': genres,
        'description': desc or 'Бесплатный официальный просмотр на сайте Киноконцерна «Мосфильм».',
        'posterUrl': poster,
        'posterRawUrl': poster,
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
            print('index error', index, e)

    print('candidate urls:', len(urls))
    items = []
    for n, u in enumerate(urls[:180], 1):
        try:
            item = parse_film(u)
            if item:
                items.append(item)
                print(n, item['name'])
        except Exception as e:
            print('film error', u, e)

    # Keep a useful feed if the site temporarily returns fewer pages.
    if len(items) < 10:
        if os.path.exists(OUT):
            print('too few new items, keeping existing autoplay feed:', len(items))
            return
        raise SystemExit('Autoplay catalog too small')

    payload = {
        'version': 1,
        'generatedBy': 'CineDeck RU official zero-config provider',
        'provider': 'Мосфильм',
        'count': len(items),
        'items': items,
    }
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print('autoplay items:', len(items))


if __name__ == '__main__':
    main()
