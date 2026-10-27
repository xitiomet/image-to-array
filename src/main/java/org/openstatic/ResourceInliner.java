package org.openstatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Replaces external resources referenced by an HTML or Markdown document
 * (images, stylesheets, scripts, fonts, icons, media) with base64 data URIs
 * so the document becomes fully self contained.
 */
public class ResourceInliner
{
    public enum DocumentType { HTML, MARKDOWN }

    public static class Resource
    {
        public final String location;
        public final byte[] data;
        public final String contentType;

        public Resource(String location, byte[] data, String contentType)
        {
            this.location = location;
            this.data = data;
            this.contentType = contentType;
        }
    }

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ImageArrayTool; +https://openstatic.org/projects/imagetoarray/)";
    private static final int MAX_REDIRECTS = 10;

    private static final Pattern TAG_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9:-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern ATTR_PATTERN = Pattern.compile("([^\\s\"'>/=]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?");
    private static final Pattern BASE_HREF_PATTERN = Pattern.compile("<base\\b[^>]*?\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))", Pattern.CASE_INSENSITIVE);

    private static final Pattern CSS_PATTERN = Pattern.compile(
        "(/\\*.*?\\*/)"
        + "|@import\\s+(?:url\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^)\\s]*))\\s*\\)|\"([^\"]*)\"|'([^']*)')([^;]*);"
        + "|url\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^)\\s]*))\\s*\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CSS_CHARSET_PATTERN = Pattern.compile("^\\s*@charset\\s+[\"']([^\"']+)[\"']\\s*;", Pattern.CASE_INSENSITIVE);

    private static final Pattern MD_CODE_PATTERN = Pattern.compile(
        "(?m)^[ ]{0,3}(`{3,}|~{3,})[^\\n]*\\n(?s:.*?)(?:^[ ]{0,3}\\1[ \\t]*$|\\z)"
        + "|(`+)(?s:.+?)\\2");
    private static final Pattern MD_INLINE_IMAGE_PATTERN = Pattern.compile(
        "(!\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]\\(\\s*)(<[^>\\n]*>|[^\\s)]+)((?:\\s+(?:\"[^\"]*\"|'[^']*'|\\([^)]*\\)))?\\s*\\))");
    private static final Pattern MD_REF_IMAGE_PATTERN = Pattern.compile("!\\[((?:[^\\[\\]]|\\[[^\\]]*\\])*)\\](?:\\s?\\[([^\\]]*)\\])?");
    private static final Pattern MD_REF_DEFINITION_PATTERN = Pattern.compile("(?m)^([ ]{0,3}\\[([^\\]]+)\\]:[ \\t]*)(<[^>\\n]*>|\\S+)");

    private final DocumentType forcedType;
    private final Map<String, String> cache = new HashMap<String, String>();
    private int inlinedCount = 0;
    private int skippedCount = 0;

    public ResourceInliner(DocumentType forcedType)
    {
        this.forcedType = forcedType;
    }

    public static DocumentType parseDocumentType(String value)
    {
        if (value == null)
            return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("html") || v.equals("htm"))
            return DocumentType.HTML;
        if (v.equals("markdown") || v.equals("md"))
            return DocumentType.MARKDOWN;
        throw new IllegalArgumentException("Unknown document type '" + value + "' (expected html or markdown)");
    }

    public int getInlinedCount()
    {
        return inlinedCount;
    }

    public int getSkippedCount()
    {
        return skippedCount;
    }

    /** Detect the document type and inline all of its external resources. */
    public String inlineDocument(Resource document)
    {
        String text = decodeText(document.data, document.contentType);
        DocumentType type = (forcedType != null) ? forcedType : detectType(document.location, document.contentType, text);
        ImageArrayTool.debugMessage("Inlining resources for " + document.location + " (detected " + type.name().toLowerCase(Locale.ROOT) + ")");
        String result = (type == DocumentType.HTML) ? inlineHtml(text, document.location) : inlineMarkdown(text, document.location);
        ImageArrayTool.debugMessage("Inlined " + inlinedCount + " resource(s), skipped " + skippedCount);
        return result;
    }

    public static DocumentType detectType(String location, String contentType, String text)
    {
        String ext = extensionOf(location);
        if (ext.matches("x?html?|shtml|php|aspx?|jsp"))
            return DocumentType.HTML;
        if (ext.matches("md|markdown|mdown|mkdn?|mdwn|mdtext"))
            return DocumentType.MARKDOWN;
        String mime = baseMime(contentType);
        if (mime != null)
        {
            if (mime.equals("text/html") || mime.equals("application/xhtml+xml"))
                return DocumentType.HTML;
            if (mime.equals("text/markdown") || mime.equals("text/x-markdown"))
                return DocumentType.MARKDOWN;
        }
        String head = text.substring(0, Math.min(text.length(), 4096)).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<html")
            || head.contains("<head") || head.contains("<body"))
        {
            return DocumentType.HTML;
        }
        return DocumentType.MARKDOWN;
    }

    /* ------------------------------------------------------------------ */
    /* HTML                                                                */
    /* ------------------------------------------------------------------ */

    public String inlineHtml(String html, String documentLocation)
    {
        String base = documentLocation;
        Matcher bm = BASE_HREF_PATTERN.matcher(html);
        if (bm.find())
        {
            String href = decodeEntities(firstNonNull(bm.group(1), bm.group(2), bm.group(3)));
            String resolved = resolve(documentLocation, href);
            if (resolved != null)
                base = resolved;
        }

        StringBuilder out = new StringBuilder(html.length());
        Matcher tm = TAG_PATTERN.matcher(html);
        int pos = 0;
        int len = html.length();
        while (pos < len)
        {
            int lt = html.indexOf('<', pos);
            if (lt < 0)
            {
                out.append(html, pos, len);
                break;
            }
            out.append(html, pos, lt);
            if (html.startsWith("<!--", lt))
            {
                int end = html.indexOf("-->", lt + 4);
                end = (end < 0) ? len : end + 3;
                out.append(html, lt, end);
                pos = end;
                continue;
            }
            tm.region(lt, len);
            if (!tm.lookingAt())
            {
                out.append('<');
                pos = lt + 1;
                continue;
            }
            String tagName = tm.group(1).toLowerCase(Locale.ROOT);
            out.append(rewriteTag(tm.group(0), tagName, tm.start(2) - tm.start(0), tm.group(2), base));
            pos = tm.end();

            if (tagName.equals("script") || tagName.equals("style"))
            {
                int close = indexOfIgnoreCase(html, "</" + tagName, pos);
                if (close < 0)
                    close = len;
                String body = html.substring(pos, close);
                out.append(tagName.equals("style") ? inlineCss(body, base, new HashSet<String>()) : body);
                pos = close;
            }
        }
        return out.toString();
    }

    private static class Attr
    {
        String name;
        String value;
        int start;
        int end;
    }

    private String rewriteTag(String tag, String tagName, int attrOffset, String attrText, String base)
    {
        List<Attr> attrs = new ArrayList<Attr>();
        Map<String, Attr> byName = new HashMap<String, Attr>();
        Matcher am = ATTR_PATTERN.matcher(attrText);
        while (am.find())
        {
            Attr a = new Attr();
            a.name = am.group(1).toLowerCase(Locale.ROOT);
            a.value = firstNonNull(am.group(2), am.group(3), am.group(4));
            a.start = attrOffset + am.start();
            a.end = attrOffset + am.end();
            attrs.add(a);
            if (!byName.containsKey(a.name))
                byName.put(a.name, a);
        }

        Map<Attr, String> replacements = new HashMap<Attr, String>();
        boolean changedResource = false;

        switch (tagName)
        {
            case "img":
            case "source":
                changedResource |= replaceUrlAttr(byName.get("src"), base, null, replacements);
                changedResource |= replaceSrcsetAttr(byName.get("srcset"), base, replacements);
                break;
            case "video":
                changedResource |= replaceUrlAttr(byName.get("src"), base, null, replacements);
                changedResource |= replaceUrlAttr(byName.get("poster"), base, null, replacements);
                break;
            case "audio":
            case "track":
            case "embed":
            case "input":
                changedResource |= replaceUrlAttr(byName.get("src"), base, null, replacements);
                break;
            case "object":
                changedResource |= replaceUrlAttr(byName.get("data"), base, null, replacements);
                break;
            case "image":
                changedResource |= replaceUrlAttr(byName.get("href"), base, null, replacements);
                changedResource |= replaceUrlAttr(byName.get("xlink:href"), base, null, replacements);
                break;
            case "script":
                changedResource |= replaceUrlAttr(byName.get("src"), base, "text/javascript", replacements);
                break;
            case "link":
                String kind = linkKind(byName);
                if (kind != null)
                    changedResource |= replaceUrlAttr(byName.get("href"), base, kind.isEmpty() ? null : kind, replacements);
                break;
            case "body":
            case "table":
            case "td":
            case "th":
                changedResource |= replaceUrlAttr(byName.get("background"), base, null, replacements);
                break;
            default:
                break;
        }

        Attr style = byName.get("style");
        if (style != null && style.value != null)
        {
            String decoded = decodeEntities(style.value);
            if (decoded.toLowerCase(Locale.ROOT).contains("url("))
            {
                String css = inlineCss(decoded, base, new HashSet<String>());
                if (!css.equals(decoded))
                    replacements.put(style, "style=\"" + css.replace("&", "&amp;").replace("\"", "&quot;") + "\"");
            }
        }

        if (replacements.isEmpty())
            return tag;

        StringBuilder sb = new StringBuilder(tag.length() + 1024);
        int pos = 0;
        for (Attr a : attrs)
        {
            boolean dropIntegrity = changedResource && a.name.equals("integrity");
            String rep = replacements.get(a);
            if (rep != null || dropIntegrity)
            {
                sb.append(tag, pos, a.start);
                if (rep != null)
                    sb.append(rep);
                pos = a.end;
            }
        }
        sb.append(tag, pos, tag.length());
        return sb.toString();
    }

    /** Returns the mime hint for a &lt;link&gt; that should be inlined, "" for no hint, or null to leave it alone. */
    private static String linkKind(Map<String, Attr> byName)
    {
        Attr relAttr = byName.get("rel");
        if (relAttr == null || relAttr.value == null)
            return null;
        Set<String> rel = new HashSet<String>();
        for (String r : relAttr.value.toLowerCase(Locale.ROOT).trim().split("\\s+"))
            rel.add(r);
        if (rel.contains("stylesheet"))
            return "text/css";
        if (rel.contains("preload") || rel.contains("modulepreload"))
        {
            Attr asAttr = byName.get("as");
            String as = (asAttr == null || asAttr.value == null) ? "" : asAttr.value.trim().toLowerCase(Locale.ROOT);
            if (as.equals("style"))
                return "text/css";
            if (as.equals("script") || rel.contains("modulepreload"))
                return "text/javascript";
            return "";
        }
        for (String r : rel)
        {
            if (r.contains("icon"))
                return "";
        }
        return null;
    }

    private boolean replaceUrlAttr(Attr attr, String base, String mimeHint, Map<Attr, String> replacements)
    {
        if (attr == null || attr.value == null)
            return false;
        String dataUri = dataUriFor(decodeEntities(attr.value).trim(), base, mimeHint, "html");
        if (dataUri == null)
            return false;
        replacements.put(attr, attr.name + "=\"" + dataUri + "\"");
        return true;
    }

    private boolean replaceSrcsetAttr(Attr attr, String base, Map<Attr, String> replacements)
    {
        if (attr == null || attr.value == null)
            return false;
        String value = decodeEntities(attr.value);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        int i = 0;
        int n = value.length();
        while (i < n)
        {
            while (i < n && (Character.isWhitespace(value.charAt(i)) || value.charAt(i) == ','))
                i++;
            if (i >= n)
                break;
            int urlStart = i;
            while (i < n && !Character.isWhitespace(value.charAt(i)))
                i++;
            String url = value.substring(urlStart, i);
            String descriptor = "";
            if (url.endsWith(","))
            {
                url = url.replaceAll(",+$", "");
            } else {
                int descStart = i;
                int depth = 0;
                while (i < n && (depth > 0 || value.charAt(i) != ','))
                {
                    char c = value.charAt(i);
                    if (c == '(')
                        depth++;
                    else if (c == ')' && depth > 0)
                        depth--;
                    i++;
                }
                descriptor = value.substring(descStart, i).trim();
            }
            String dataUri = dataUriFor(url, base, null, "html");
            if (dataUri != null)
            {
                url = dataUri;
                changed = true;
            }
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(url);
            if (!descriptor.isEmpty())
                sb.append(' ').append(descriptor);
        }
        if (!changed)
            return false;
        replacements.put(attr, attr.name + "=\"" + sb.toString().replace("\"", "&quot;") + "\"");
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* CSS                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Rewrites url() and @import references in a stylesheet. importChain holds the
     * stylesheets currently being inlined so circular @imports are not followed.
     */
    public String inlineCss(String css, String base, Set<String> importChain)
    {
        StringBuilder out = new StringBuilder(css.length());
        Matcher m = CSS_PATTERN.matcher(css);
        int pos = 0;
        while (m.find())
        {
            out.append(css, pos, m.start());
            pos = m.end();
            if (m.group(1) != null)
            {
                out.append(m.group(0));
                continue;
            }
            String importUrl = firstNonNull(m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
            if (importUrl != null || m.group(7) != null)
            {
                String dataUri = inlineStylesheet(importUrl, base, importChain);
                if (dataUri == null)
                    out.append(m.group(0));
                else
                    out.append("@import url(").append(dataUri).append(")").append(m.group(7)).append(';');
                continue;
            }
            String ref = firstNonNull(m.group(8), m.group(9), m.group(10));
            String dataUri = dataUriFor(ref.trim(), base, null, "css");
            out.append(dataUri == null ? m.group(0) : "url(" + dataUri + ")");
        }
        out.append(css, pos, css.length());
        return out.toString();
    }

    private String inlineStylesheet(String ref, String base, Set<String> importChain)
    {
        String resolved = resolve(base, ref == null ? "" : ref.trim());
        if (resolved == null)
            return null;
        if (importChain.contains(resolved))
        {
            ImageArrayTool.debugMessage("[css] Skipping circular @import: " + resolved);
            return null;
        }
        Set<String> chain = new HashSet<String>(importChain);
        chain.add(resolved);
        return dataUriForResolved(resolved, "text/css", "css", chain);
    }

    /* ------------------------------------------------------------------ */
    /* Markdown                                                            */
    /* ------------------------------------------------------------------ */

    public String inlineMarkdown(String md, String documentLocation)
    {
        // Gather the reference labels used by images so their definitions can be inlined
        Set<String> imageLabels = new HashSet<String>();
        StringBuilder out = new StringBuilder(md.length());
        List<String[]> segments = splitMarkdownCode(md);
        for (String[] segment : segments)
        {
            if (segment[1] == null)
                continue;
            Matcher rm = MD_REF_IMAGE_PATTERN.matcher(segment[0]);
            while (rm.find())
            {
                int after = rm.end();
                if (rm.group(2) == null && after < segment[0].length() && segment[0].charAt(after) == '(')
                    continue;
                String label = (rm.group(2) == null || rm.group(2).trim().isEmpty()) ? rm.group(1) : rm.group(2);
                imageLabels.add(normalizeLabel(label));
            }
        }

        for (String[] segment : segments)
        {
            if (segment[1] == null)
            {
                out.append(segment[0]);
                continue;
            }
            String text = inlineHtml(segment[0], documentLocation);
            text = replaceMarkdownImages(text, documentLocation);
            text = replaceMarkdownDefinitions(text, documentLocation, imageLabels);
            out.append(text);
        }
        return out.toString();
    }

    /** Splits markdown into {text, "1"} segments to process and {code, null} segments to keep. */
    private static List<String[]> splitMarkdownCode(String md)
    {
        List<String[]> segments = new ArrayList<String[]>();
        Matcher m = MD_CODE_PATTERN.matcher(md);
        int pos = 0;
        while (m.find())
        {
            if (m.start() > pos)
                segments.add(new String[] { md.substring(pos, m.start()), "1" });
            segments.add(new String[] { m.group(0), null });
            pos = m.end();
        }
        if (pos < md.length())
            segments.add(new String[] { md.substring(pos), "1" });
        return segments;
    }

    private String replaceMarkdownImages(String text, String base)
    {
        StringBuilder out = new StringBuilder(text.length());
        Matcher m = MD_INLINE_IMAGE_PATTERN.matcher(text);
        int pos = 0;
        while (m.find())
        {
            String dataUri = dataUriFor(markdownUrl(m.group(2)), base, null, "md");
            if (dataUri != null)
            {
                out.append(text, pos, m.start());
                out.append(m.group(1)).append(dataUri).append(m.group(3));
                pos = m.end();
            }
        }
        out.append(text, pos, text.length());
        return out.toString();
    }

    private String replaceMarkdownDefinitions(String text, String base, Set<String> imageLabels)
    {
        if (imageLabels.isEmpty())
            return text;
        StringBuilder out = new StringBuilder(text.length());
        Matcher m = MD_REF_DEFINITION_PATTERN.matcher(text);
        int pos = 0;
        while (m.find())
        {
            if (!imageLabels.contains(normalizeLabel(m.group(2))))
                continue;
            String dataUri = dataUriFor(markdownUrl(m.group(3)), base, null, "md");
            if (dataUri != null)
            {
                out.append(text, pos, m.start());
                out.append(m.group(1)).append(dataUri);
                pos = m.end();
            }
        }
        out.append(text, pos, text.length());
        return out.toString();
    }

    private static String markdownUrl(String raw)
    {
        String url = raw.trim();
        if (url.startsWith("<") && url.endsWith(">"))
            url = url.substring(1, url.length() - 1);
        return url.replaceAll("\\\\([!-/:-@\\[-`{-~])", "$1");
    }

    private static String normalizeLabel(String label)
    {
        return label.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /* ------------------------------------------------------------------ */
    /* Resource loading                                                    */
    /* ------------------------------------------------------------------ */

    /** Returns a data URI for a reference, or null if it should be left untouched. */
    private String dataUriFor(String ref, String base, String mimeHint, String context)
    {
        if (ref == null)
            return null;
        String resolved = resolve(base, ref);
        if (resolved == null)
            return null;
        if ("text/css".equals(mimeHint))
            return inlineStylesheet(ref, base, new HashSet<String>());
        return dataUriForResolved(resolved, mimeHint, context, null);
    }

    private String dataUriForResolved(String resolved, String mimeHint, String context, Set<String> importChain)
    {
        String key = (mimeHint == null ? "" : mimeHint) + "|" + resolved;
        if (cache.containsKey(key))
            return cache.get(key);

        String dataUri = null;
        Resource res = fetch(resolved);
        if (res == null || res.data.length == 0)
        {
            ImageArrayTool.debugMessage("[" + context + "] Skipping (No Data): " + resolved);
            skippedCount++;
        } else {
            String mime = guessMime(res.location, res.data, res.contentType, mimeHint);
            byte[] data = res.data;
            if (mime.equals("text/css"))
            {
                String css = decodeText(data, res.contentType);
                css = inlineCss(css, res.location, importChain == null ? new HashSet<String>(Set.of(res.location)) : importChain);
                data = css.getBytes(StandardCharsets.UTF_8);
                mime = "text/css;charset=utf-8";
            }
            dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
            ImageArrayTool.debugMessage("[" + context + "] Inlined (" + mime + ", " + humanSize(res.data.length) + "): " + resolved);
            inlinedCount++;
        }
        cache.put(key, dataUri);
        return dataUri;
    }

    /** Loads a url or local path, following redirects. Returns null on failure. */
    public static Resource fetch(String location)
    {
        try
        {
            if (isHttp(location))
            {
                String current = location;
                for (int i = 0; i <= MAX_REDIRECTS; i++)
                {
                    HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setRequestProperty("Accept", "*/*");
                    int code = conn.getResponseCode();
                    if (code >= 300 && code < 400 && conn.getHeaderField("Location") != null)
                    {
                        current = new URL(new URL(current), conn.getHeaderField("Location")).toString();
                        conn.disconnect();
                        continue;
                    }
                    if (code >= 400)
                    {
                        ImageArrayTool.debugMessage("HTTP " + code + " for " + current);
                        conn.disconnect();
                        return null;
                    }
                    try (InputStream in = decodedStream(conn))
                    {
                        return new Resource(current, in.readAllBytes(), conn.getContentType());
                    }
                }
                ImageArrayTool.debugMessage("Too many redirects for " + location);
                return null;
            }
            Path path = location.toLowerCase(Locale.ROOT).startsWith("file:") ? Paths.get(new URI(location)) : Paths.get(location);
            return new Resource(path.toAbsolutePath().normalize().toString(), Files.readAllBytes(path), null);
        } catch (Exception e) {
            ImageArrayTool.debugMessage("Failed to load " + location + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static InputStream decodedStream(URLConnection conn) throws IOException
    {
        InputStream in = conn.getInputStream();
        String encoding = conn.getContentEncoding();
        if (encoding != null)
        {
            encoding = encoding.trim().toLowerCase(Locale.ROOT);
            if (encoding.equals("gzip") || encoding.equals("x-gzip"))
                return new GZIPInputStream(in);
            if (encoding.equals("deflate"))
                return new InflaterInputStream(in);
        }
        return in;
    }

    /** Resolves a reference against a base url or file path. Returns null for refs that must not be inlined. */
    public static String resolve(String base, String ref)
    {
        if (ref == null)
            return null;
        ref = ref.trim();
        if (ref.isEmpty() || ref.startsWith("#"))
            return null;
        String lc = ref.toLowerCase(Locale.ROOT);
        if (lc.matches("^(data|javascript|mailto|about|blob|tel|sms|cid|chrome|moz-extension|chrome-extension):.*"))
            return null;
        try
        {
            if (isHttp(ref))
                return encodeUnsafe(ref);
            if (isHttp(base))
                return new URI(encodeUnsafe(base)).resolve(new URI(encodeUnsafe(ref))).toString();
            if (ref.startsWith("//"))
                return "https:" + encodeUnsafe(ref);
            if (lc.startsWith("file:"))
                return Paths.get(new URI(encodeUnsafe(ref))).toString();
            if (lc.matches("^[a-z][a-z0-9+.-]*:.*") && !lc.matches("^[a-z]:[\\\\/].*"))
                return null;

            String path = percentDecode(ref.replaceAll("[?#].*$", ""));
            if (path.isEmpty())
                return null;
            Path basePath = Paths.get(base).toAbsolutePath();
            Path baseDir = Files.isDirectory(basePath) ? basePath : basePath.getParent();
            Path candidate = Paths.get(path);
            if (candidate.isAbsolute() && Files.exists(candidate))
                return candidate.normalize().toString();
            if (path.startsWith("/") || path.startsWith("\\"))
                path = path.substring(1);
            return baseDir.resolve(path).normalize().toString();
        } catch (Exception e) {
            ImageArrayTool.debugMessage("Could not resolve '" + ref + "' against " + base + " (" + e.getMessage() + ")");
            return null;
        }
    }

    public static String guessMime(String location, byte[] data, String headerType, String hint)
    {
        if (hint != null)
            return hint;
        String mime = baseMime(headerType);
        boolean generic = mime == null || mime.equals("application/octet-stream") || mime.equals("binary/octet-stream")
            || mime.equals("text/plain") || mime.equals("application/unknown") || mime.equals("application/x-unknown");
        if (!generic)
            return mime;

        String sniffed = sniffMime(data);
        if (sniffed != null)
            return sniffed;

        switch (extensionOf(location))
        {
            case "png": return "image/png";
            case "apng": return "image/apng";
            case "jpg": case "jpeg": case "jfif": case "pjpeg": case "pjp": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            case "bmp": return "image/bmp";
            case "ico": case "cur": return "image/x-icon";
            case "svg": case "svgz": return "image/svg+xml";
            case "css": return "text/css";
            case "js": case "mjs": case "cjs": return "text/javascript";
            case "json": case "map": return "application/json";
            case "webmanifest": return "application/manifest+json";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            case "ttf": return "font/ttf";
            case "otf": return "font/otf";
            case "eot": return "application/vnd.ms-fontobject";
            case "mp4": case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "ogv": return "video/ogg";
            case "mp3": return "audio/mpeg";
            case "ogg": case "oga": return "audio/ogg";
            case "wav": return "audio/wav";
            case "m4a": return "audio/mp4";
            case "flac": return "audio/flac";
            case "vtt": return "text/vtt";
            case "pdf": return "application/pdf";
            default: break;
        }
        return (mime != null) ? mime : "application/octet-stream";
    }

    private static String sniffMime(byte[] d)
    {
        if (startsWith(d, 0x89, 'P', 'N', 'G'))
            return "image/png";
        if (startsWith(d, 0xFF, 0xD8, 0xFF))
            return "image/jpeg";
        if (startsWith(d, 'G', 'I', 'F', '8'))
            return "image/gif";
        if (startsWith(d, 'R', 'I', 'F', 'F') && d.length > 11 && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P')
            return "image/webp";
        if (startsWith(d, 'B', 'M') && d.length > 14)
            return "image/bmp";
        if (startsWith(d, 0x00, 0x00, 0x01, 0x00))
            return "image/x-icon";
        if (startsWith(d, 'w', 'O', 'F', 'F'))
            return "font/woff";
        if (startsWith(d, 'w', 'O', 'F', '2'))
            return "font/woff2";
        if (startsWith(d, 'O', 'T', 'T', 'O'))
            return "font/otf";
        if (startsWith(d, 0x00, 0x01, 0x00, 0x00) || startsWith(d, 't', 'r', 'u', 'e'))
            return "font/ttf";
        if (startsWith(d, '%', 'P', 'D', 'F'))
            return "application/pdf";
        if (ImageArrayTool.isSVG(d))
            return "image/svg+xml";
        try
        {
            String guess = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(d));
            if (guess != null && guess.startsWith("image/"))
                return guess;
        } catch (Exception e) {}
        return null;
    }

    private static boolean startsWith(byte[] data, int... prefix)
    {
        if (data.length < prefix.length)
            return false;
        for (int i = 0; i < prefix.length; i++)
        {
            if ((data[i] & 0xFF) != prefix[i])
                return false;
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    public static String decodeText(byte[] data, String contentType)
    {
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null)
        {
            Matcher m = Pattern.compile("charset\\s*=\\s*\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (m.find())
            {
                try { charset = Charset.forName(m.group(1)); } catch (Exception e) {}
            }
        } else {
            String head = new String(data, 0, Math.min(data.length, 128), StandardCharsets.ISO_8859_1);
            Matcher m = CSS_CHARSET_PATTERN.matcher(head);
            if (m.find())
            {
                try { charset = Charset.forName(m.group(1)); } catch (Exception e) {}
            }
        }
        String text = new String(data, charset);
        if (text.startsWith("\uFEFF"))
            text = text.substring(1);
        return text;
    }

    private static boolean isHttp(String s)
    {
        if (s == null)
            return false;
        String lc = s.toLowerCase(Locale.ROOT);
        return lc.startsWith("http://") || lc.startsWith("https://");
    }

    private static String baseMime(String contentType)
    {
        if (contentType == null)
            return null;
        String mime = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return mime.isEmpty() ? null : mime;
    }

    private static String extensionOf(String location)
    {
        if (location == null)
            return "";
        String path = location.replaceAll("[?#].*$", "");
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return (dot > slash) ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** Percent-encodes characters that java.net.URI rejects while leaving existing escapes intact. */
    private static String encodeUnsafe(String s)
    {
        StringBuilder sb = new StringBuilder(s.length());
        for (byte b : s.getBytes(StandardCharsets.UTF_8))
        {
            int c = b & 0xFF;
            if (c <= 0x20 || c >= 0x7F || "\"<>\\^`{|}".indexOf(c) >= 0)
                sb.append('%').append(String.format("%02X", c));
            else
                sb.append((char) c);
        }
        return sb.toString();
    }

    private static String percentDecode(String s)
    {
        if (s.indexOf('%') < 0)
            return s;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2)))
            {
                out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                byte[] b = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                out.write(b, 0, b.length);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isHex(char c)
    {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static final Pattern ENTITY_PATTERN = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|amp|quot|apos|lt|gt|nbsp);");

    static String decodeEntities(String s)
    {
        if (s == null || s.indexOf('&') < 0)
            return s;
        Matcher m = ENTITY_PATTERN.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        int pos = 0;
        while (m.find())
        {
            sb.append(s, pos, m.start());
            String e = m.group(1);
            switch (e)
            {
                case "amp": sb.append('&'); break;
                case "quot": sb.append('"'); break;
                case "apos": sb.append('\''); break;
                case "lt": sb.append('<'); break;
                case "gt": sb.append('>'); break;
                case "nbsp": sb.append('\u00A0'); break;
                default:
                    int cp = (e.charAt(1) == 'x' || e.charAt(1) == 'X') ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                    sb.appendCodePoint(cp);
                    break;
            }
            pos = m.end();
        }
        sb.append(s, pos, s.length());
        return sb.toString();
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from)
    {
        int n = needle.length();
        for (int i = from; i <= haystack.length() - n; i++)
        {
            if (haystack.regionMatches(true, i, needle, 0, n))
                return i;
        }
        return -1;
    }

    private static String firstNonNull(String... values)
    {
        for (String v : values)
        {
            if (v != null)
                return v;
        }
        return null;
    }

    private static String humanSize(long bytes)
    {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
