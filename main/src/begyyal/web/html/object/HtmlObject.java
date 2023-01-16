package begyyal.web.html.object;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import begyyal.commons.constant.Strs;
import begyyal.commons.object.collection.VarList;
import begyyal.commons.object.collection.VarList.VarListGen;
import begyyal.commons.object.collection.XList;
import begyyal.commons.object.collection.XList.XListGen;
import begyyal.commons.object.collection.XMap;
import begyyal.commons.object.collection.XMap.XMapGen;
import begyyal.commons.util.function.ReflectionResolver;
import begyyal.commons.util.function.XStrings;
import begyyal.web.html.HtmlParser;
import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.HtmlTag;

/**
 * DOMライクにHTMLを構造表現したオブジェクト。<br>
 * 原則としてタグ1つ(閉じタグがあれば1対)が本オブジェクト1つで表現される。<br>
 * 開始タグから閉じタグの間で出現するタグを「配下オブジェクト」とし、文字列を「コンテンツ」とする。
 */
public class HtmlObject implements Cloneable {

    private static final AtomicInteger idGen = new AtomicInteger();
    private static final String idStr = "id";
    private static final String classStr = "class";

    public final int id;
    public final HtmlTag tag;
    public final HtmlObject parent;
    private final Type type;
    private XMap<String, String> properties;
    private VarList childrenAndContents;
    private boolean success = true;

    private HtmlObject(
	int id,
	HtmlTag tag,
	HtmlObject parent,
	Type type,
	XMap<String, String> properties,
	VarList cc,
	boolean needConvertingProperties) {

	this.id = id;
	this.parent = parent;
	this.type = type;
	this.tag = tag;
	this.properties = needConvertingProperties ? convert(properties) : properties;
	this.childrenAndContents = cc;
    }

    private HtmlObject(
	HtmlTag tag,
	HtmlObject parent,
	Type type,
	XMap<String, String> properties) {

	this(idGen.getAndIncrement(), tag, parent, type, properties, VarListGen.newi(), true);
	if (parent != null)
	    parent.childrenAndContents.add(this);
    }

    public static HtmlObject
	newi(HtmlTag tag, HtmlObject parent, XMap<String, String> properties) {
	return new HtmlObject(tag, parent, Type.Normal, properties);
    }

    public static RootHtmlObject newRoot(HtmlDocType docType) {
	return new RootHtmlObject(docType);
    }

    public static HtmlObject newComment(HtmlObject parent) {
	return new HtmlObject(null, parent, Type.Comment, null);
    }

    @Override
    public HtmlObject clone() {
	return new HtmlObject(id, tag, parent, type, XMapGen.of(properties),
	    childrenAndContents.stream()
		.map(o -> o instanceof HtmlObject ? ((HtmlObject) o).clone() : o)
		.collect(VarListGen.collect()),
	    false);
    }

    public HtmlObject append(String content) {
	childrenAndContents.add(content);
	return this;
    }

    public void update(HtmlObject o) {
	if (o.id == id) {
	    this.properties = o.properties;
	    this.childrenAndContents = o.childrenAndContents;
	}
    }

    public void markFailure() {
	success = false;
    }

    public boolean isSuccess() {
	return success && (isComment()
		|| childrenAndContents.stream(HtmlObject.class).allMatch(c -> c.isSuccess()));
    }

    public boolean isRoot() {
	return Type.Root == type;
    }

    public boolean isComment() {
	return Type.Comment == type;
    }

    public boolean isNormal() {
	return Type.Normal == type;
    }

    public XMap<String, String> getProperties() {
	return properties;
    }

    public XList<HtmlObject> getChildren() {
	return isComment() ? XListGen.empty()
		: childrenAndContents.isEmpty() ? XListGen.empty()
			: childrenAndContents.get(HtmlObject.class);
    }

    public XList<HtmlObject> getProgeny() {
	XList<HtmlObject> progenyList = XListGen.newi();
	aggregateProgeny(progenyList);
	return progenyList;

    }

    private void aggregateProgeny(XList<HtmlObject> list) {
	if (!isComment() && !childrenAndContents.isEmpty())
	    childrenAndContents.stream(HtmlObject.class).forEach(o -> {
		list.add(o);
		o.aggregateProgeny(list);
	    });
    }

    public XList<String> getContents() {
	return isNormal() && tag.unneedEnclosure
		? XListGen.empty() : childrenAndContents.getStr();
    }

    @SuppressWarnings("exports")
    public VarList getChildrenAndContents() {
	return childrenAndContents;
    }

    public HtmlObject getElementById(String id) {
	return findElementById(idStr, id);
    }

    private HtmlObject findElementById(String propertyKey, String propertyValue) {
	return !isNormal() || !XStrings.equals(properties.get(propertyKey), propertyValue)
		? childrenAndContents.stream(HtmlObject.class)
		    .map(o -> o.findElementById(propertyKey, propertyValue))
		    .filter(o -> o != null)
		    .findFirst().orElse(null)
		: this;
    }

    public XList<HtmlObject> getElementsByClass(String className) {
	XList<HtmlObject> result = XListGen.newi();
	aggregateElementsByClass(result, classStr, className);
	return result;
    }

    private void aggregateElementsByClass(
	XList<HtmlObject> result,
	String propertyKey,
	String className) {
	if (isNormal()) {
	    String temp = properties.get(propertyKey);
	    if (temp != null
		    && Arrays.stream(temp.split(Strs.space)).anyMatch(s -> s.equals(className)))
		result.add(this);
	}
	childrenAndContents.stream(HtmlObject.class)
	    .forEach(o -> o.aggregateElementsByClass(result, propertyKey, className));
    }

    public XList<HtmlObject> relatedBy(String keyword) {
	return select(o -> o.childrenAndContents.stream(String.class)
	    .anyMatch(str -> str.contains(keyword)));
    }

    public XList<HtmlObject> relatedBy(boolean isSuccess) {
	return select(o -> o.success == isSuccess);
    }

    public XList<HtmlObject> select(Predicate<HtmlObject> predicate) {
	XList<HtmlObject> result = getProgeny().stream()
	    .filter(predicate)
	    .collect(XListGen.collect());
	if (predicate.test(this))
	    result.add(this);
	return result;
    }

    private XMap<String, String> convert(XMap<String, String> properties) {
	return properties == null ? XMapGen.empty() : properties.entrySet().stream()
	    .collect(XMapGen.collect(e -> e.getKey().toLowerCase(), e -> e.getValue(),
		(v1, v2) -> {
		    markFailure();
		    return v2;
		}));
    }

    public XList<String> decode() {
	return HtmlParser.process(this);
    }

    private enum Type {
	Root,
	Normal,
	Comment;
    }

    public static class RootHtmlObject
	    extends
	    HtmlObject {

	public final HtmlDocType docType;

	private RootHtmlObject(HtmlDocType docType) {
	    super(null, null, Type.Root, null);
	    this.docType = docType;
	}

	@Override
	public XList<String> getContents() {
	    return XListGen.empty();
	}
    }

    @Override
    public String toString() {
	return (isNormal() ? tag.name() : type.name()) + Strs.slash
		+ (isSuccess() ? "success" : "failed");
    }

    @Override
    public boolean equals(Object o) {
	HtmlObject casted = ReflectionResolver.cast(HtmlObject.class, o);
	return casted != null
		&& casted.id == id && properties.equals(casted.properties)
		&& childrenAndContents.equals(casted.childrenAndContents);
    }

    @Override
    public int hashCode() {
	return this.id;
    }
}
