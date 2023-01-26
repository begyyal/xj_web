package begyyal.web.html.processor;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import begyyal.commons.constant.Strs;
import begyyal.commons.object.Pair;
import begyyal.commons.object.collection.XList;
import begyyal.commons.object.collection.XList.XListGen;
import begyyal.commons.object.collection.XMap;
import begyyal.commons.object.collection.XMap.XMapGen;
import begyyal.commons.util.function.XStrings;
import begyyal.web.html.HtmlParser;
import begyyal.web.html.constant.Const;
import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.HtmlTag;
import begyyal.web.html.object.HtmlObject;
import begyyal.web.html.object.HtmlObject.RootHtmlObject;

public class SequentialHtmlParser implements HtmlParser {

    public SequentialHtmlParser() {
    }

    @Override
    public RootHtmlObject process(List<String> resource) {
	var res = resource == null || resource.isEmpty()
		? HtmlObject.createFailedRoot()
		: new Generator(resource.stream()
		    .filter(s -> s != null)
		    .collect(Collectors.toCollection(LinkedList::new)))
			.process();
	return res;
    }

    private static class Generator {

	private LinkedList<String> resource;
	private HtmlObject focusObj;
	private String focusLine;

	private Generator(LinkedList<String> resource) {
	    this.resource = resource;
	}

	private RootHtmlObject process() {
	    RootHtmlObject o = null;
	    try {
		focusLine = resource.poll().trim();
		if (focusLine.isEmpty())
		    removeAndNext();
		skipXmlTagIfNeeded();
		if ((focusObj = o = distinctDocType()) != null)
		    while (!recursiveExtraction())
			;
	    } catch (UnfinishedStatement e) {
	    }
	    return o == null ? HtmlObject.createFailedRoot() : o;
	}

	private void skipXmlTagIfNeeded() throws UnfinishedStatement {

	    if (!focusLine.startsWith(Const.xmlDeclarationPrefix))
		return;
	    substringAndNext(5);

	    int brk;
	    while ((brk = focusLine.indexOf(Const.xmlDeclarationSuffix)) == -1)
		removeAndNext();
	    substringAndNext(brk + 2);
	}

	private RootHtmlObject distinctDocType() throws UnfinishedStatement {

	    if (!XStrings.startsWithIgnoreCase(focusLine, Const.docTypePrefix))
		return HtmlObject.newRoot(HtmlDocType.None);
	    substringAndNext(9);

	    if (!XStrings.startsWithIgnoreCase(focusLine, Const.docTypeElement1))
		return null;
	    substringAndNext(Const.docTypeElement1.length());

	    if (focusLine.startsWith(Strs.bracket2end)) {
		substringAndNext(1);
		return HtmlObject.newRoot(HtmlDocType.V5);
	    } else if (!XStrings.startsWithIgnoreCase(focusLine, Const.docTypeElement2))
		return null;
	    substringAndNext(Const.docTypeElement2.length());

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
		    || focusLine.startsWith(Const.tagEnclosurePrefix)
		    || this.focusObj.tag == HtmlTag.Svg) {
		if (focusObj.isRoot()) {
		    this.focusObj.markFailure();
		    return true;
		}
		appendContents();
		return this.resource.isEmpty();
	    } else if (focusLine.startsWith(Const.commentPrefix)) {
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
		Const.tagEnclosurePrefix + focusObj.tag.str)) == -1) {

		if (!isSvg
			&& (brk2 = focusLine.indexOf(Strs.bracket2start)) != -1
			&& tryToExtractMixedChild(brk2)) {
		    appendContents();
		    return;
		}

		focusObj.append(focusLine);
		focusLine = resource.poll();
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
	    if (temp2.isEmpty()) {
		for (int i = 1; i < resource.size(); i++)
		    if (!(temp2 = resource.get(i)).isBlank())
			break;
		if (temp2.isBlank())
		    throw new UnfinishedStatement();
		temp2 = temp2.trim();
	    }

	    if (temp2.startsWith(Strs.bracket2end) || isSvg) {
		if (brk != 0) {
		    focusObj.append(focusLine.substring(0, brk));
		    substringAndNext(brk);
		}
	    } else {
		focusObj.append(focusLine);
		focusLine = resource.poll();
		appendContents();
	    }
	}

	private boolean tryToExtractMixedChild(int brk) {

	    if (focusLine.substring(brk).startsWith(Const.tagEnclosurePrefix))
		return false;

	    Generator gen = new Generator(new LinkedList<>(resource));
	    HtmlObject copy = focusObj.clone();
	    if (brk != 0)
		copy.append(focusLine.substring(0, brk));
	    gen.focusObj = copy;
	    gen.focusLine = focusLine.substring(brk);

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
			Const.tagEnclosurePrefix + focusObj.tag.str))
		return false;

	    String temp = focusLine.substring(2 + focusObj.tag.str.length()).trim();
	    if (focusLine.isEmpty())
		for (int i = 1; i < resource.size(); i++)
		    if (!(temp = resource.get(i)).isBlank()) {
			temp = temp.trim();
			break;
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

	    String suffix = isScript ? Const.commentSuffixForScript : Const.commentSuffix;
	    int brk;
	    while ((brk = focusLine.indexOf(suffix)) == -1) {
		focusObj.append(focusLine);
		focusLine = resource.poll();
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
	    if (propertyKey.isEmpty())
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

	    String propertyValue = this.extractQuotedValue();
	    if (propertyValue == null)
		if ((brk = XStrings.firstIndexOf(focusLine, Strs.space,
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
	    while (!resource.isEmpty() && (focusLine = resource.poll()).isBlank())
		;
	    focusLine = focusLine.trim();
	}

	private void substringAndNext(int begin) throws UnfinishedStatement {
	    if (begin <= focusLine.length()) {
		focusLine = focusLine.substring(begin).trim();
		if (focusLine.isEmpty())
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
		focusLine = resource.poll();
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
}
