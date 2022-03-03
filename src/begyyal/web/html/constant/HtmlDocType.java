package begyyal.web.html.constant;

public enum HtmlDocType {

    V5,
    V4_1_Strict(
            "-//W3C//DTD HTML 4.01//EN",
            "http://www.w3.org/TR/html4/strict.dtd"),
    V4_1_Transitional(
            "-//W3C//DTD HTML 4.01 Transitional//EN",
            "http://www.w3.org/TR/html4/loose.dtd"),
    V4_1_Frameset(
            "-//W3C//DTD HTML 4.01 Frameset//EN",
            "http://www.w3.org/TR/html4/frameset.dtd"),
    X1_Strict(
            "-//W3C//DTD XHTML 1.0 Strict//EN",
            "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"),
    X1_Transitional(
            "-//W3C//DTD XHTML 1.0 Transitional//EN",
            "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"),
    X1_Frameset(
            "-//W3C//DTD XHTML 1.0 Frameset//EN",
            "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd"),
    V3_2("-//W3C//DTD HTML 3.2 Final//EN"),
    C1("-//W3C//DTD Compact HTML 1.0 Draft//EN"),

    None();

    public final String publicIdentifier;

    public final String systemIdentifier;

    private HtmlDocType(String publicIdentifier, String systemIdentifier) {
        this.publicIdentifier = publicIdentifier;
        this.systemIdentifier = systemIdentifier;
    }

    private HtmlDocType(String publicIdentifier) {
        this(publicIdentifier, null);
    }

    private HtmlDocType() {
        this(null, null);
    }

    public static HtmlDocType parseByPublicIdentifier(String str) {
        for (HtmlDocType type : values())
            if (type.needPublicIdentifier() && type.publicIdentifier.equals(str))
                return type;
        return null;
    }

    public boolean needPublicIdentifier() {
        return publicIdentifier != null;
    }

    public boolean needSystemIdentifier() {
        return systemIdentifier != null;
    }
}
