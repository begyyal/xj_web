package begyyal.web.html.object;

import java.util.List;

import begyyal.commons.object.collection.XMap;
import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.HtmlTag;
import begyyal.web.html.constant.RecType;

public class TagRecord implements TIRecord {

    public final HtmlTag type;
    public final boolean isEnclosure;
    public final XMap<String, String> props;

    private TagRecord(
	HtmlTag type,
	boolean isEnclosure,
	XMap<String, String> props) {
	this.type = type;
	this.isEnclosure = isEnclosure;
	this.props = props;
    }

    public static TagRecord newi(HtmlTag type, XMap<String, String> props) {
	return new TagRecord(type, false, props);
    }

    public static TagRecord newEnclosure(HtmlTag type) {
	return new TagRecord(type, true, null);
    }

    @Override
    public RecType getType() {
	return RecType.Tag;
    }

    @Override
    public List<String> getAsContents() {
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
