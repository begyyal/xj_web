package begyyal.web.constant;

import java.util.Objects;

import begyyal.commons.constant.Strs;
import begyyal.commons.object.collection.XMap;
import begyyal.commons.object.collection.XMap.XMapGen;

public enum MimeType {

    HTML("text/html");

    private final String str;

    private MimeType(String str) {
	this.str = str;
    }

    /**
     * @return 対象文字列が、このMIMEが定義する文字列で開始されていた場合にtrue
     */
    public boolean match(String headerValue) {
	Objects.requireNonNull(headerValue);
	return headerValue.trim().startsWith(str);
    }

    /**
     * 対象のContent-Type文字列からプロパティを抜粋する。
     */
    public static XMap<String, String> getContentTypeProperties(String contentType) {

	Objects.requireNonNull(contentType);

	String v = contentType.trim();
	if (v.isEmpty() || !v.contains(Strs.semiColon))
	    return XMapGen.empty();

	XMap<String, String> properties = XMapGen.newi();
	v = v.substring(v.indexOf(Strs.semiColon) + 1);
	for (String p : v.split(Strs.semiColon)) {
	    int boundary = p.indexOf(Strs.equal);
	    if (boundary != -1 && boundary != p.length() - 1)
		properties.put(p.substring(0, boundary), p.substring(boundary + 1));
	}

	return properties;
    }
}
