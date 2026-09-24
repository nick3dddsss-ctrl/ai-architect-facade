package com.cinedeck.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProviderEngine {
    private static final String CINEMETA = "https://v3-cinemeta.strem.io";
    private static final String WATCHHUB = "https://watchhub.strem.io";
    private static final String PUBLIC_DOMAIN = "https://caching.stremio.net/publicdomainmovies.now.sh";
    private static final String ARCHIVE = "https://archive.org";
    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";
    private static final String UA = "CineDeckTV/1.5 AndroidTV";

    public static final class Media {
        public String id = "";
        public String type = "movie";
        public String imdbId = "";
        public String archiveId = "";
        public String name = "";
        public String year = "";
        public String rating = "";
        public String genres = "";
        public String description = "";
        public String poster = "";
        public String provider = "";
    }

    public static final class SubtitleOption {
        public String url = "";
        public String label = "";
        public String language = "und";
        public String mime = "text/vtt";
    }

    public static final class StreamOption {
        public String provider = "";
        public String title = "";
        public String url = "";
        public String externalUrl = "";
        public String youtubeId = "";
        public String quality = "";
        public int height = 0;
        public long sizeBytes = 0;
        public List<SubtitleOption> subtitles = new ArrayList<>();
        public boolean direct() { return url != null && !url.isEmpty(); }
        public boolean external() { return (externalUrl != null && !externalUrl.isEmpty()) || (youtubeId != null && !youtubeId.isEmpty()); }
        public String label() {
            StringBuilder s = new StringBuilder();
            if (provider != null && !provider.isEmpty()) s.append(provider);
            if (quality != null && !quality.isEmpty()) {
                if (s.length() > 0) s.append(" • ");
                s.append(quality);
            }
            if (sizeBytes > 0) {
                if (s.length() > 0) s.append(" • ");
                s.append(humanSize(sizeBytes));
            }
            if (subtitles != null && !subtitles.isEmpty()) {
                if (s.length() > 0) s.append(" • ");
                s.append("CC ").append(subtitles.size());
            }
            if (title != null && !title.isEmpty()) {
                if (s.length() > 0) s.append(" • ");
                s.append(title);
            }
            return s.length() == 0 ? "Источник" : s.toString();
        }
    }

    public List<Media> homeMovies() throws Exception {
        return fetchCinemeta("movie", "", 42);
    }

    public List<Media> homeSeries() throws Exception {
        return fetchCinemeta("series", "", 32);
    }

    public List<Media> watchNow() throws Exception {
        return fetchArchive("", 34);
    }

    public List<Media> search(String query) {
        LinkedHashMap<String, Media> out = new LinkedHashMap<>();
        try { merge(out, fetchCinemeta("movie", query, 30)); } catch (Exception ignored) {}
        try { merge(out, fetchCinemeta("series", query, 20)); } catch (Exception ignored) {}
        try { merge(out, fetchArchive(query, 24)); } catch (Exception ignored) {}
        return new ArrayList<>(out.values());
    }

    private void merge(Map<String, Media> map, List<Media> items) {
        for (Media m : items) {
            String key = !m.imdbId.isEmpty() ? m.imdbId : (!m.id.isEmpty() ? m.id : norm(m.name + "|" + m.year));
            if (!map.containsKey(key)) map.put(key, m);
        }
    }

    public List<StreamOption> resolve(Media media) {
        List<StreamOption> out = new ArrayList<>();
        if (media == null) return out;

        if (!media.archiveId.isEmpty()) {
            try { out.addAll(resolveArchive(media.archiveId)); } catch (Exception ignored) {}
        } else if ("movie".equalsIgnoreCase(media.type) && !media.name.isEmpty()) {
            try {
                List<Media> matches = fetchArchive(media.name, 8);
                int accepted = 0;
                for (Media candidate : matches) {
                    if (!strongTitleMatch(media.name, candidate.name)) continue;
                    if (!yearCompatible(media.year, candidate.year)) continue;
                    out.addAll(resolveArchive(candidate.archiveId));
                    if (++accepted >= 3 || out.size() >= 12) break;
                }
            } catch (Exception ignored) {}
        }

        if (!media.imdbId.isEmpty()) {
            try { out.addAll(resolveStremio(PUBLIC_DOMAIN, media.type, media.imdbId, "Public Domain")); } catch (Exception ignored) {}
            try { out.addAll(resolveStremio(WATCHHUB, media.type, media.imdbId, "WatchHub")); } catch (Exception ignored) {}
        }

        if ("movie".equalsIgnoreCase(media.type) && !media.name.isEmpty()) {
            try { out.addAll(resolveCommons(media)); } catch (Exception ignored) {}
        }

        Collections.sort(out, (a, b) -> {
            if (a.direct() != b.direct()) return a.direct() ? -1 : 1;
            if (a.direct() && b.direct() && a.height != b.height) return Integer.compare(b.height, a.height);
            if (a.direct() && b.direct() && a.subtitles.size() != b.subtitles.size()) return Integer.compare(b.subtitles.size(), a.subtitles.size());
            return a.label().compareToIgnoreCase(b.label());
        });
        return dedupeStreams(out);
    }

    private List<StreamOption> dedupeStreams(List<StreamOption> input) {
        LinkedHashMap<String, StreamOption> map = new LinkedHashMap<>();
        for (StreamOption s : input) {
            String key = !s.url.isEmpty() ? s.url : (!s.youtubeId.isEmpty() ? "yt:" + s.youtubeId : s.externalUrl);
            if (key == null || key.isEmpty()) continue;
            if (!map.containsKey(key)) map.put(key, s);
        }
        return new ArrayList<>(map.values());
    }

    private List<Media> fetchCinemeta(String type, String search, int limit) throws Exception {
        String endpoint = CINEMETA + "/catalog/" + type + "/top";
        if (search != null && !search.trim().isEmpty()) endpoint += "/search=" + enc(search.trim());
        endpoint += ".json";
        JSONObject root = getJson(endpoint, 8000, 12000);
        JSONArray arr = root.optJSONArray("metas");
        List<Media> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length() && out.size() < limit; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Media m = new Media();
            m.id = o.optString("id", "");
            m.imdbId = m.id.startsWith("tt") ? m.id : "";
            m.type = o.optString("type", type);
            m.name = o.optString("name", "");
            m.year = extractYear(firstNonEmpty(o.optString("releaseInfo", ""), o.optString("year", "")));
            m.rating = firstNonEmpty(o.optString("imdbRating", ""), o.optString("rating", ""));
            m.description = textValue(o.opt("description"));
            m.genres = join(o.optJSONArray("genres"), 3);
            m.poster = o.optString("poster", "");
            m.provider = "Cinemeta";
            if (!m.name.isEmpty()) out.add(m);
        }
        return out;
    }

    private List<Media> fetchArchive(String search, int limit) throws Exception {
        String rights = "(licenseurl:* OR collection:feature_films OR collection:prelinger)";
        String q;
        if (search == null || search.trim().isEmpty()) {
            q = "mediatype:movies AND " + rights + " AND NOT access-restricted-item:true";
        } else {
            String clean = search.trim().replace('"', ' ');
            q = "mediatype:movies AND " + rights + " AND title:(\"" + clean + "\") AND NOT access-restricted-item:true";
        }
        String url = ARCHIVE + "/advancedsearch.php?q=" + enc(q)
                + "&fl[]=identifier&fl[]=title&fl[]=date&fl[]=description&fl[]=subject"
                + "&sort[]=downloads%20desc&rows=" + Math.max(limit, 20) + "&page=1&output=json";
        JSONObject root = getJson(url, 9000, 15000);
        JSONObject response = root.optJSONObject("response");
        JSONArray docs = response == null ? null : response.optJSONArray("docs");
        List<Media> out = new ArrayList<>();
        if (docs == null) return out;
        for (int i = 0; i < docs.length() && out.size() < limit; i++) {
            JSONObject o = docs.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("identifier", "");
            String title = textValue(o.opt("title"));
            if (id.isEmpty() || title.isEmpty()) continue;
            Media m = new Media();
            m.id = "ia:" + id;
            m.archiveId = id;
            m.type = "movie";
            m.name = title;
            m.year = extractYear(textValue(o.opt("date")));
            m.description = stripHtml(textValue(o.opt("description")));
            m.genres = textValue(o.opt("subject"));
            if (m.genres.length() > 90) m.genres = m.genres.substring(0, 90);
            m.poster = ARCHIVE + "/services/img/" + encPath(id);
            m.provider = "Internet Archive";
            out.add(m);
        }
        return out;
    }

    private List<StreamOption> resolveArchive(String archiveId) throws Exception {
        JSONObject root = getJson(ARCHIVE + "/metadata/" + encPath(archiveId), 9000, 18000);
        JSONArray files = root.optJSONArray("files");
        List<StreamOption> out = new ArrayList<>();
        List<SubtitleOption> subtitles = new ArrayList<>();
        if (files == null) return out;

        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            String name = f.optString("name", "");
            String lower = name.toLowerCase(Locale.ROOT);
            if (name.isEmpty() || f.optBoolean("private", false)) continue;
            if (lower.endsWith(".srt") || lower.endsWith(".vtt")) {
                SubtitleOption sub = new SubtitleOption();
                sub.url = ARCHIVE + "/download/" + encPath(archiveId) + "/" + encodeFilePath(name);
                sub.mime = lower.endsWith(".srt") ? "application/x-subrip" : "text/vtt";
                sub.language = inferLanguage(lower);
                sub.label = subtitleLabel(sub.language, name);
                subtitles.add(sub);
            }
        }

        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            String name = f.optString("name", "");
            String format = f.optString("format", "");
            String lower = name.toLowerCase(Locale.ROOT);
            if (name.isEmpty() || f.optBoolean("private", false)) continue;
            boolean video = lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm")
                    || lower.endsWith(".m3u8") || lower.endsWith(".mpd")
                    || format.toLowerCase(Locale.ROOT).contains("mpeg4") || format.toLowerCase(Locale.ROOT).contains("h.264");
            if (!video || lower.contains("thumb") || lower.contains("sample") || lower.contains("spectrogram")) continue;
            StreamOption s = new StreamOption();
            s.provider = "Internet Archive";
            s.url = ARCHIVE + "/download/" + encPath(archiveId) + "/" + encodeFilePath(name);
            s.height = parseInt(f.optString("height", "0"));
            if (s.height == 0) s.height = parseQuality(qualityFromText(name));
            s.quality = s.height > 0 ? s.height + "p" : containerLabel(lower, format);
            s.sizeBytes = parseLong(f.optString("size", "0"));
            String source = f.optString("source", "");
            s.title = source.isEmpty() ? shortFileName(name) : source;
            s.subtitles = new ArrayList<>(subtitles);
            out.add(s);
        }
        out.sort((a, b) -> {
            if (a.height != b.height) return Integer.compare(b.height, a.height);
            return Long.compare(b.sizeBytes, a.sizeBytes);
        });
        if (out.size() > 10) return new ArrayList<>(out.subList(0, 10));
        return out;
    }

    private List<StreamOption> resolveCommons(Media media) throws Exception {
        String query = "\"" + media.name + "\" filetype:video";
        String url = COMMONS_API + "?action=query&generator=search&gsrsearch=" + enc(query)
                + "&gsrnamespace=6&gsrlimit=8&prop=imageinfo&iiprop=url%7Cmime&format=json&formatversion=2&origin=*";
        JSONObject root = getJson(url, 8000, 12000);
        JSONObject q = root.optJSONObject("query");
        JSONArray pages = q == null ? null : q.optJSONArray("pages");
        List<StreamOption> out = new ArrayList<>();
        if (pages == null) return out;
        for (int i = 0; i < pages.length(); i++) {
            JSONObject page = pages.optJSONObject(i);
            if (page == null) continue;
            String title = page.optString("title", "").replaceFirst("(?i)^File:", "");
            if (!strongTitleMatch(media.name, title.replaceAll("\\.[A-Za-z0-9]{2,5}$", ""))) continue;
            JSONArray info = page.optJSONArray("imageinfo");
            JSONObject ii = info == null ? null : info.optJSONObject(0);
            if (ii == null) continue;
            String direct = ii.optString("url", "");
            String mime = ii.optString("mime", "");
            String lower = direct.toLowerCase(Locale.ROOT);
            if (direct.isEmpty() || !(mime.startsWith("video/") || lower.endsWith(".webm") || lower.endsWith(".ogv") || lower.endsWith(".mp4"))) continue;
            StreamOption s = new StreamOption();
            s.provider = "Wikimedia Commons";
            s.url = direct;
            s.quality = containerLabel(lower, mime);
            s.title = title;
            out.add(s);
        }
        return out;
    }

    private List<StreamOption> resolveStremio(String base, String type, String id, String provider) throws Exception {
        JSONObject root = getJson(base + "/stream/" + encPath(type) + "/" + encPath(id) + ".json", 7000, 11000);
        JSONArray streams = root.optJSONArray("streams");
        List<StreamOption> out = new ArrayList<>();
        if (streams == null) return out;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject o = streams.optJSONObject(i);
            if (o == null) continue;
            StreamOption s = new StreamOption();
            s.provider = provider;
            s.title = firstNonEmpty(o.optString("title", ""), o.optString("name", ""));
            s.url = o.optString("url", "");
            s.externalUrl = o.optString("externalUrl", "");
            s.youtubeId = o.optString("ytId", "");
            String q = qualityFromText(s.title);
            s.quality = q;
            s.height = parseQuality(q);
            if (s.direct() || s.external()) out.add(s);
        }
        return out;
    }

    public boolean probe(String url) {
        try {
            HttpURLConnection c = connection(url, 5000, 7000);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            try { c.getInputStream().close(); } catch (Exception ignored) {}
            c.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject getJson(String url, int connect, int read) throws Exception {
        return new JSONObject(get(url, connect, read));
    }

    private String get(String url, int connect, int read) throws Exception {
        HttpURLConnection c = connection(url, connect, read);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("HTTP " + code + " " + url);
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private HttpURLConnection connection(String url, int connect, int read) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connect);
        c.setReadTimeout(read);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json,text/plain,*/*");
        return c;
    }

    private static String join(JSONArray arr, int max) {
        if (arr == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < arr.length() && i < max; i++) {
            String s = textValue(arr.opt(i));
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(s);
        }
        return b.toString();
    }

    private static String textValue(Object v) {
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < a.length() && i < 4; i++) {
                String s = textValue(a.opt(i));
                if (s.isEmpty()) continue;
                if (b.length() > 0) b.append(", ");
                b.append(s);
            }
            return b.toString();
        }
        return String.valueOf(v).replaceAll("\\s+", " ").trim();
    }

    private static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean strongTitleMatch(String a, String b) {
        String x = norm(a);
        String y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        if (x.length() < 5 || y.length() < 5) return false;
        String shorter = x.length() <= y.length() ? x : y;
        String longer = x.length() > y.length() ? x : y;
        return longer.contains(shorter) && ((double) shorter.length() / (double) longer.length()) >= 0.72;
    }

    private static boolean yearCompatible(String a, String b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return true;
        try { return Math.abs(Integer.parseInt(a) - Integer.parseInt(b)) <= 1; }
        catch (Exception e) { return true; }
    }

    private static String inferLanguage(String lower) {
        if (lower.matches(".*(^|[._ -])(ru|rus|russian)([._ -]|$).*")) return "ru";
        if (lower.matches(".*(^|[._ -])(en|eng|english)([._ -]|$).*")) return "en";
        if (lower.matches(".*(^|[._ -])(es|spa|spanish)([._ -]|$).*")) return "es";
        if (lower.matches(".*(^|[._ -])(fr|fre|fra|french)([._ -]|$).*")) return "fr";
        if (lower.matches(".*(^|[._ -])(de|ger|deu|german)([._ -]|$).*")) return "de";
        return "und";
    }

    private static String subtitleLabel(String lang, String name) {
        if ("ru".equals(lang)) return "Русские";
        if ("en".equals(lang)) return "English";
        if ("es".equals(lang)) return "Español";
        if ("fr".equals(lang)) return "Français";
        if ("de".equals(lang)) return "Deutsch";
        return shortFileName(name);
    }

    private static String containerLabel(String lower, String format) {
        if (lower.endsWith(".m3u8")) return "HLS";
        if (lower.endsWith(".mpd")) return "DASH";
        if (lower.endsWith(".webm")) return "WEBM";
        if (lower.endsWith(".ogv") || lower.endsWith(".ogg")) return "OGV";
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "MP4";
        return format == null || format.isEmpty() ? "VIDEO" : format;
    }

    private static String shortFileName(String name) {
        if (name == null) return "";
        String n = name;
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        return n.length() > 42 ? n.substring(0, 39) + "…" : n;
    }

    private static String humanSize(long bytes) {
        if (bytes <= 0) return "";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) return String.format(Locale.US, "%.1f GB", mb / 1024.0);
        return String.format(Locale.US, "%.0f MB", mb);
    }

    private static String extractYear(String raw) {
        if (raw == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(raw);
        return m.find() ? m.group() : "";
    }

    private static String qualityFromText(String s) {
        if (s == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(2160p|4k|1440p|1080p|720p|480p|360p)").matcher(s);
        return m.find() ? m.group().toUpperCase(Locale.ROOT).replace("4K", "2160p") : "";
    }

    private static int parseQuality(String q) {
        if (q == null) return 0;
        if (q.toLowerCase(Locale.ROOT).contains("2160")) return 2160;
        if (q.toLowerCase(Locale.ROOT).contains("1440")) return 1440;
        if (q.toLowerCase(Locale.ROOT).contains("1080")) return 1080;
        if (q.toLowerCase(Locale.ROOT).contains("720")) return 720;
        if (q.toLowerCase(Locale.ROOT).contains("480")) return 480;
        if (q.toLowerCase(Locale.ROOT).contains("360")) return 360;
        return 0;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private static String encPath(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20").replace("%2F", "/"); }
        catch (Exception e) { return s; }
    }

    private static String encodeFilePath(String path) {
        if (path == null) return "";
        String[] parts = path.split("/");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (b.length() > 0) b.append('/');
            b.append(encPath(p));
        }
        return b.toString();
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^a-zа-я0-9]+", " ").trim();
    }
}
