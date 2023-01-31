package begyyal.web.html.object;

import java.util.List;

import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.RecType;

public interface TIRecord {
    public RecType getType();

    public List<String> getAsContents();

    public HtmlDocType getAsDoctype();

    public TagRecord getAsTag();
}
