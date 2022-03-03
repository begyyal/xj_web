package begyyal.web;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import begyyal.commons.constant.web.HttpHeader;
import begyyal.commons.object.collection.XList;
import begyyal.commons.object.collection.XList.XListGen;
import begyyal.web.constant.MimeType;
import begyyal.web.html.HtmlParser;
import begyyal.web.html.object.HtmlObject;

public class WebResourceGetter {

    // Chrome v69
    private static final String pseudoUserAgent = //
	    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36";

    private static final String chunked = "chunked";

    private static final String gzip = "gzip";

    private static final int bufChunkSize = 1000;

    public static HtmlObject getHtmlObject(String urlStr) {
	return HtmlParser.process(get(urlStr));
    }

    public static XList<String> get(String urlStr) {
	try {
	    return process(urlStr);
	} catch (IOException e) {
	    return XListGen.empty();
	}
    }

    private static XList<String> process(String urlStr) throws IOException {

	URL url = new URL(urlStr);
	URLConnection con = url.openConnection();

	con.setRequestProperty(HttpHeader.UserAgent.str, pseudoUserAgent);

	String contentType = con.getContentType();
	if (contentType == null || !MimeType.HTML.match(contentType)) // 暫定
	    return XListGen.empty();

	String charset = MimeType.getContentTypeProperties(contentType).get("charset");
	if (charset == null || !Charset.isSupported(charset))
	    charset = StandardCharsets.UTF_8.displayName();

	boolean isChunked = con.getHeaderFields().entrySet().stream()
	    .anyMatch(e -> HttpHeader.TransferEncoding.symbolizes(e.getKey())
		    && chunked.equalsIgnoreCase(e.getValue().get(0)));
	boolean isGzip = con.getHeaderFields().entrySet().stream()
	    .anyMatch(e -> HttpHeader.ContentEncoding.symbolizes(e.getKey())
		    && gzip.equalsIgnoreCase(e.getValue().get(0)));

	XList<String> resource = XListGen.newi();

	try (InputStream is = isGzip ? new GZIPInputStream(con.getInputStream())
		: con.getInputStream()) {

	    if (isChunked) {
		BytePool pool = new BytePool();
		byte[] bufChunk = new byte[bufChunkSize];
		int readCount;
		while ((readCount = is.read(bufChunk, 0, bufChunkSize)) != -1)
		    pool.add(bufChunk, readCount);
		read(resource, new ByteArrayInputStream(pool.getTrimmedPool()), charset);

	    } else
		read(resource, is, charset);
	}

	return resource;
    }

    private static void read(XList<String> resource, InputStream is, String charset)
	throws IOException {
	try (BufferedReader reader = new BufferedReader(
	    new InputStreamReader(skipBom(is, charset), charset))) {
	    while (reader.ready())
		resource.add(reader.readLine());
	}
    }

    private static class BytePool {

	private byte[] pool = new byte[bufChunkSize];

	private int pos = 0;

	private BytePool() {
	}

	void add(byte[] chunk, int count) {
	    if (count > pool.length - pos)
		pool = Arrays.copyOf(pool, pool.length + bufChunkSize);
	    System.arraycopy(chunk, 0, pool, pos, count);
	    pos += count;
	}

	byte[] getTrimmedPool() {
	    return pos % bufChunkSize == 0 ? pos == 0 ? new byte[0] : pool
		    : Arrays.copyOf(pool, pos);
	}
    }

    private static InputStream skipBom(InputStream is, String charset) throws IOException {

	if (!StandardCharsets.UTF_8.displayName().equalsIgnoreCase(charset))
	    return is;
	if (!is.markSupported())
	    is = new BufferedInputStream(is);

	is.mark(3);
	if (is.available() >= 3) {
	    byte[] b = new byte[3];
	    is.read(b, 0, 3);
	    if (b[0] != 0xEF || b[1] != 0xBB || b[2] != 0xBF)
		is.reset();
	}

	return is;
    }
}
