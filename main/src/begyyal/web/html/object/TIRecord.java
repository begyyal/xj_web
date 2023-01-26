package begyyal.web.html.object;

import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.RecType;

public interface TIRecord {
    public RecType getType();

    public String getAsContents();

    public HtmlDocType getAsDoctype();

    public TagRecord getAsTag();
}
