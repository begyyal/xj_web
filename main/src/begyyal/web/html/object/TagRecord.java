package begyyal.web.html.object;

import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.HtmlTag;
import begyyal.web.html.constant.RecType;

public class TagRecord implements TIRecord {

    public final HtmlTag type;

    private TagRecord(HtmlTag type) {
	this.type = type;
    }

    @Override
    public RecType getType() {
	return RecType.Tag;
    }

    @Override
    public String getAsContents() {
	return null;
    }

    @Override
    public TagRecord getAsTag() {
	return this;
    }

    @Override
    public HtmlDocType getAsDoctype() {
	return null;
    }
}
