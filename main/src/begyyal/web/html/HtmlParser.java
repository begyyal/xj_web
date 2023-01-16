package begyyal.web.html;

import java.util.Map;

import begyyal.commons.constant.Strs;
import begyyal.commons.object.Pair;
import begyyal.commons.object.collection.XList;
import begyyal.commons.object.collection.XList.XListGen;
import begyyal.commons.object.collection.XMap;
import begyyal.commons.object.collection.XMap.XMapGen;
import begyyal.commons.util.function.XStrings;
import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.HtmlTag;
import begyyal.web.html.object.HtmlObject;
import begyyal.web.html.object.HtmlObject.RootHtmlObject;

public class HtmlParser {

    private static final String tagEnclosurePrefix = "</";
    private static final String xmlDeclarationPrefix = "<?xml";
    private static final String xmlDeclarationSuffix = "?>";
    private static final String docTypePrefix = "<!doctype";
    private static final String docTypeElement1 = "html";
    private static final String docTypeElement2 = "PUBLIC";
    private static final String commentPrefix = "<!--";
    private static final String commentSuffix = "-->";
    private static final String commentSuffixForScript = "//-->";

    public static RootHtmlObject process(XList<String> resource) {
	return resource == null || resource.isEmpty()
		? createFailedRoot()
		: new Generator(resource.stream()
		    .filter(s -> s != null)
		    .collect(XListGen.collect()))
			.process();
    }

    public static XList<String> process(HtmlObject o) {
	return new Decoder().process(o);
    }

    private static RootHtmlObject createFailedRoot() {
	RootHtmlObject o = HtmlObject.newRoot(null);
	o.markFailure();
	return o;
    }

    private static class Generator {

	private XList<String> resource;
	private HtmlObject focusObj;
	private String focusLine;

	private Generator(XList<String> resource) {
	    this.resource = resource;
	}

	private RootHtmlObject process() {
	    RootHtmlObject o = null;
	    try {
		focusLine = resource.next().trim();
		if (Strs.empty.equals(focusLine))
		    removeAndNext();
		skipXmlTagIfNeeded();
		if ((focusObj = o = distinctDocType()) != null)
		    while (!recursiveExtraction())
			;
	    } catch (UnfinishedStatement e) {
	    }
	    return o == null ? createFailedRoot() : o;
	}

	private void skipXmlTagIfNeeded() throws UnfinishedStatement {

	    if (!focusLine.startsWith(xmlDeclarationPrefix))
		return;
	    substringAndNext(5);

	    int brk;
	    while ((brk = focusLine.indexOf(xmlDeclarationSuffix)) == -1)
		removeAndNext();
	    substringAndNext(brk + 2);
	}

	private RootHtmlObject distinctDocType() throws UnfinishedStatement {

	    if (!XStrings.startsWithIgnoreCase(focusLine, docTypePrefix))
		return HtmlObject.newRoot(HtmlDocType.None);
	    substringAndNext(9);

	    if (!XStrings.startsWithIgnoreCase(focusLine, docTypeElement1))
		return null;
	    substringAndNext(docTypeElement1.length());

	    if (focusLine.startsWith(Strs.bracket2end)) {
		substringAndNext(1);
		return HtmlObject.newRoot(HtmlDocType.V5);
	    } else if (!XStrings.startsWithIgnoreCase(focusLine, docTypeElement2))
		return null;
	    substringAndNext(docTypeElement2.length());

	    HtmlDocType type = HtmlDocType.parseByPublicIdentifier(extractQuotedValue());
	    if (type == null)
		return null;

	    if (!type.needSystemIdentifier())
		if (focusLine.startsWith(Strs.bracket2end)) {
		    substringAndNext(1);
		    return HtmlObject.newRoot(type);
		} else
		    return null;

	    extractQuotedValue();
	    if (focusLine.startsWith(Strs.bracket2end)) {
		substringAndNext(1);
		return HtmlObject.newRoot(type);
	    } else
		return null;
	}

	private boolean recursiveExtraction() throws UnfinishedStatement {

	    if (!focusLine.startsWith(Strs.bracket2start)
		    || focusLine.startsWith(tagEnclosurePrefix)
		    || this.focusObj.tag == HtmlTag.Svg) {
		if (focusObj.isRoot()) {
		    this.focusObj.markFailure();
		    return true;
		}
		appendContents();
		return this.resource.isEmpty();
	    } else if (focusLine.startsWith(commentPrefix)) {
		searchCommentEnclosure(focusObj, focusObj.tag == HtmlTag.Script);
		return this.resource.isEmpty();
	    }

	    Pair<String, Integer> brk = //
		    XStrings.firstIndexOf(focusLine, Strs.space, Strs.bracket2end);
	    HtmlTag tag = HtmlTag.parse(brk == null
		    ? focusLine.substring(1)
		    : focusLine.substring(1, brk.v2));
	    if (tag == null) {
		this.focusObj.markFailure();
		return true;
	    }

	    if (brk != null)
		substringAndNext(brk.v2 + 1);
	    else
		removeAndNext();

	    XMap<String, String> properties = null;
	    if (brk == null || brk.v1 != Strs.bracket2end) {
		properties = XMapGen.newi();
		if (!extractProperties(properties)) {
		    this.focusObj.markFailure();
		    return true;
		}
	    }

	    HtmlObject child = focusObj = HtmlObject.newi(tag, focusObj, properties);

	    if (!tag.unneedEnclosure)
		while (!searchTagEnclosure()) {
		    if (recursiveExtraction())
			return true;
		    focusObj = child;
		}

	    return resource.isEmpty();
	}

	private void appendContents() throws UnfinishedStatement {

	    boolean isSvg = this.focusObj.tag == HtmlTag.Svg;
	    int brk, brk2;
	    while ((brk = XStrings.indexOfIgnoreCase(focusLine,
		tagEnclosurePrefix + focusObj.tag.str)) == -1) {

		if (!isSvg
			&& (brk2 = focusLine.indexOf(Strs.bracket2start)) != -1
			&& tryToExtractMixedChild(brk2)) {
		    appendContents();
		    return;
		}

		focusObj.append(focusLine);
		focusLine = resource.removeAndNext();
		if (focusLine == null)
		    throw new UnfinishedStatement();
	    }

	    if (!isSvg
		    && (brk2 = focusLine.substring(0, brk).indexOf(Strs.bracket2start)) != -1
		    && tryToExtractMixedChild(brk2)) {
		appendContents();
		return;
	    }

	    String temp2 = focusLine.substring(brk + 2 + focusObj.tag.str.length()).trim();
	    if (Strs.empty.equals(temp2)) {
		while (resource.hasNext() && (temp2 = resource.next()).isBlank())
		    ;
		if (temp2.isBlank())
		    throw new UnfinishedStatement();
		temp2 = temp2.trim();
		resource.resetFocus();
		resource.next();
	    }

	    if (temp2.startsWith(Strs.bracket2end) || isSvg) {
		if (brk != 0) {
		    focusObj.append(focusLine.substring(0, brk));
		    substringAndNext(brk);
		}
	    } else {
		focusObj.append(focusLine);
		focusLine = resource.removeAndNext();
		appendContents();
	    }
	}

	private boolean tryToExtractMixedChild(int brk) {

	    if (focusLine.substring(brk).startsWith(tagEnclosurePrefix))
		return false;

	    Generator gen = new Generator(XListGen.of(resource));
	    HtmlObject copy = focusObj.clone();
	    if (brk != 0)
		copy.append(focusLine.substring(0, brk));
	    gen.focusObj = copy;
	    gen.focusLine = focusLine.substring(brk);
	    gen.resource.next();

	    try {
		if (gen.recursiveExtraction())
		    return false;
	    } catch (UnfinishedStatement e) {
		return false;
	    }

	    this.resource = gen.resource;
	    this.focusLine = gen.focusLine;
	    focusObj.update(copy);
	    return true;
	}

	private boolean searchTagEnclosure() throws UnfinishedStatement {

	    if (focusLine.length() < 2 + focusObj.tag.str.length()
		    || !XStrings.startsWithIgnoreCase(focusLine,
			tagEnclosurePrefix + focusObj.tag.str))
		return false;

	    String temp = focusLine.substring(2 + focusObj.tag.str.length()).trim();
	    if (Strs.empty.equals(focusLine)) {
		while (resource.hasNext() && (temp = resource.next()).isBlank())
		    ;
		if (temp == null)
		    throw new UnfinishedStatement();
		temp = temp.trim();
		resource.resetFocus();
		resource.next();
	    }

	    if (!temp.startsWith(Strs.bracket2end))
		return false;

	    substringAndNext(2 + focusObj.tag.str.length());
	    substringAndNext(1);
	    return true;
	}

	private void searchCommentEnclosure(HtmlObject parent, boolean isScript)
	    throws UnfinishedStatement {

	    if (!isScript)
		focusObj = HtmlObject.newComment(parent);
	    focusLine = focusLine.substring(4);

	    String suffix = isScript ? commentSuffixForScript : commentSuffix;
	    int brk;
	    while ((brk = focusLine.indexOf(suffix)) == -1) {
		focusObj.append(focusLine);
		focusLine = resource.removeAndNext();
		if (focusLine == null)
		    throw new UnfinishedStatement();
	    }

	    if (brk != 0)
		focusObj.append(focusLine.substring(0, brk));

	    substringAndNext(brk + suffix.length());
	}

	private boolean extractProperties(XMap<String, String> properties)
	    throws UnfinishedStatement {

	    Pair<String, Integer> brk;
	    XList<String> pLines = null;
	    while ((brk = XStrings.firstIndexOf(focusLine, Strs.equal,
		Strs.bracket2end)) == null) {
		if (pLines == null)
		    pLines = XListGen.newi();
		pLines.add(focusLine);
		removeAndNext();
	    }

	    if (brk.v1 == Strs.bracket2end) {
		if (pLines != null)
		    pLines.forEach(
			l -> XStrings.applyEachToken(l, k -> properties.put(k, null)));
		substringAndNext(brk.v2 + 1);
		return true;
	    }

	    String propertyKey = focusLine.substring(0, brk.v2).trim();
	    if (Strs.empty.equals(propertyKey))
		if (pLines != null) {
		    propertyKey = pLines.getTip();
		    pLines.removeTip();
		} else
		    return false;

	    int brk2;
	    if ((brk2 = propertyKey.lastIndexOf(Strs.space)) != -1) {
		if (pLines == null)
		    pLines = XListGen.newi();
		pLines.add(propertyKey.substring(0, brk2));
		propertyKey = propertyKey.substring(brk2 + 1);
	    }

	    if (pLines != null)
		pLines.forEach(l -> XStrings.applyEachToken(l, k -> properties.put(k, null)));
	    substringAndNext(brk.v2 + 1);

	    String propertyValue = null;
	    if (focusLine.startsWith(Strs.dblQuotation))
		propertyValue = extractQuotedValue(Strs.dblQuotation);
	    else if (focusLine.startsWith(Strs.quotation))
		propertyValue = extractQuotedValue(Strs.quotation);
	    else if ((brk = XStrings.firstIndexOf(focusLine, Strs.space,
		Strs.bracket2end)) == null) {
		propertyValue = focusLine;
		removeAndNext();
	    } else {
		propertyValue = focusLine.substring(0, brk.v2);
		substringAndNext(brk.v2);
	    }

	    if (propertyValue == null)
		return false;

	    properties.put(propertyKey, propertyValue);
	    return extractProperties(properties);
	}

	private void removeAndNext() throws UnfinishedStatement {
	    while (resource.hasNext() && (focusLine = resource.removeAndNext()).isBlank())
		;
	    if (Strs.empty.equals(focusLine))
		resource.remove();
	    else if (focusLine == null)
		throw new UnfinishedStatement();
	    else
		focusLine = focusLine.trim();
	}

	private void substringAndNext(int begin) throws UnfinishedStatement {
	    if (begin <= focusLine.length()) {
		focusLine = focusLine.substring(begin).trim();
		if (Strs.empty.equals(focusLine))
		    removeAndNext();
	    } else
		removeAndNext();
	}

	private String extractQuotedValue() throws UnfinishedStatement {
	    if (focusLine.startsWith(Strs.dblQuotation))
		return extractQuotedValue(Strs.dblQuotation);
	    if (focusLine.startsWith(Strs.quotation))
		return extractQuotedValue(Strs.quotation);
	    else
		return null;
	}

	private String extractQuotedValue(String quatation)
	    throws UnfinishedStatement {

	    substringAndNext(1);
	    int brk;
	    String value = Strs.empty;
	    while ((brk = focusLine.indexOf(quatation)) == -1) {
		value += (focusLine + Strs.space);
		focusLine = resource.removeAndNext();
		if (focusLine == null)
		    throw new UnfinishedStatement();
	    }
	    value += focusLine.substring(0, brk);

	    substringAndNext(brk + 1);
	    return value;
	}

	@SuppressWarnings("serial")
	private class UnfinishedStatement
		extends
		Exception {
	    UnfinishedStatement() {
		if (focusObj != null)
		    focusObj.markFailure();
	    }
	}
    }

    private static class Decoder {

	private static final String indent = "    ";

	private final XList<String> resource = XListGen.newi();

	private Decoder() {
	}

	private XList<String> process(HtmlObject o) {

	    if (o instanceof RootHtmlObject) {
		decodeRoot((RootHtmlObject) o);
		for (HtmlObject child : o.getChildren())
		    recursiveDecode(child, 0);
	    } else
		recursiveDecode(o, 0);

	    return resource;
	}

	private void decodeRoot(RootHtmlObject casted) {

	    StringBuilder sb = new StringBuilder();
	    sb.append(docTypePrefix).append(Strs.space).append(docTypeElement1);
	    if (!casted.docType.needPublicIdentifier())
		resource.add(sb.append(Strs.bracket2end).toString());
	    else {
		sb.append(Strs.space).append(docTypeElement2).append(Strs.space)
		    .append(Strs.dblQuotation)
		    .append(casted.docType.publicIdentifier)
		    .append(Strs.dblQuotation);
		if (!casted.docType.needSystemIdentifier())
		    resource.add(sb.append(Strs.bracket2end).toString());
		else
		    resource.add(sb.append(Strs.space).append(Strs.dblQuotation)
			.append(casted.docType.systemIdentifier).append(Strs.dblQuotation)
			.append(Strs.bracket2end).toString());
	    }
	}

	private void recursiveDecode(HtmlObject o, int depth) {
	    if (o.isComment())
		decodeComment(o, depth);
	    else
		decodeNormal(o, depth);
	}

	private void decodeComment(HtmlObject o, int depth) {

	    StringBuilder sb = new StringBuilder();
	    XList<String> contents = o.getContents();

	    sb.append(commentPrefix).append(Strs.space);

	    if (contents.hasNext()) {
		addWithIndent(depth, sb.append(contents.next()).toString());
		while (contents.hasNext())
		    addWithIndent(depth, contents.next());
		resource.setTip(resource.getTip() + Strs.space + commentSuffix);

	    } else
		addWithIndent(depth, sb.append(commentSuffix).toString());
	}

	private void decodeNormal(HtmlObject o, int depth) {

	    StringBuilder sb = new StringBuilder();
	    sb.append(Strs.bracket2start).append(o.tag.str).append(Strs.space);
	    for (Map.Entry<String, String> entry : o.getProperties().entrySet()) {
		sb.append(entry.getKey());
		if (entry.getValue() != null)
		    sb.append(Strs.equal).append(Strs.dblQuotation).append(entry.getValue())
			.append(Strs.dblQuotation);
		sb.append(Strs.space);
	    }
	    addWithIndent(depth,
		sb.replace(sb.length() - 1, sb.length(), Strs.bracket2end).toString());

	    String tagEnclosure = null;
	    if (o.tag != null && !o.tag.unneedEnclosure)
		tagEnclosure = tagEnclosurePrefix + o.tag.str + Strs.bracket2end;

	    if (o.getChildrenAndContents().size() != 1 || o.getContents().isEmpty()) {
		for (Object cc : o.getChildrenAndContents())
		    if (cc instanceof HtmlObject)
			recursiveDecode((HtmlObject) cc, depth + 1);
		    else
			addWithIndent(depth, (String) cc);
		if (tagEnclosure != null)
		    if (o.getChildrenAndContents().isEmpty()) {
			resource.setTip(resource.getTip() + tagEnclosure);
		    } else
			addWithIndent(depth, tagEnclosure);

	    } else if (tagEnclosure != null)
		resource.setTip(resource.getTip() + o.getContents().getTip() + tagEnclosure);
	}

	private void addWithIndent(int depth, String str) {
	    String pre = Strs.empty;
	    for (int i = 0; i < depth; i++)
		pre += indent;
	    resource.add(pre + str);
	}
    }
}
