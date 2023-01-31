package begyyal.web.html.processor;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import begyyal.commons.constant.Strs;
import begyyal.commons.object.Pair;
import begyyal.commons.object.collection.PairList.PairListGen;
import begyyal.commons.object.collection.XGen;
import begyyal.commons.object.collection.XMap;
import begyyal.commons.util.function.XStrings;
import begyyal.web.exception.IllegalFormatException;
import begyyal.web.exception.UnfinishedStatementException;
import begyyal.web.html.constant.Const;
import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.HtmlTag;
import begyyal.web.html.constant.RecType;
import begyyal.web.html.object.TIRecord;
import begyyal.web.html.object.TagRecord;

public class TagInterpreter {

    private static final int[] c0space = new int[33];
    private static final int[] tagDelimiter1 = new int[34];
    {
	for (int i = 0; i <= 32; i++) {
	    c0space[i] = i;
	    tagDelimiter1[i] = i;
	}
	tagDelimiter1[33] = Strs.bracket2end.toCharArray()[0];
    }
    private static final TIRecord finRec = new TIRecord() {
	@Override
	public RecType getType() {
	    return RecType.Fin;
	}

	@Override
	public List<String> getAsContents() {
	    return null;
	}

	@Override
	public TagRecord getAsTag() {
	    return null;
	}

	@Override
	public HtmlDocType getAsDoctype() {
	    return null;
	}
    };
    private static final Set<HtmlTag> notNestedTags = XGen.newHashSet(
	HtmlTag.Script,
	HtmlTag.Svg);

    private final BlockingQueue<TIRecord> q;

    TagInterpreter(BlockingQueue<TIRecord> q) {
	this.q = q;
    }

    public void process(LinkedList<String> res) {
	try {
	    new LineProcessor(res, q, new PreProcessor(res, q).exe()).exe();
	} catch (UnfinishedStatementException | IllegalFormatException e) {
	    System.out.println(e.getMessage());
	    e.printStackTrace();
	} finally {
	    q.add(finRec);
	}
    }

    private class InterpreterBase {
	protected final BlockingQueue<TIRecord> q;
	protected final LinkedList<String> res;
	protected String line;

	private InterpreterBase(LinkedList<String> res, BlockingQueue<TIRecord> q, String l) {
	    this.q = q;
	    this.res = res;
	    this.line = l;
	}

	protected List<String> extractQuotedValue() throws UnfinishedStatementException {
	    if (this.line.startsWith(Strs.dblQuotation))
		return this.extractQuotedValue(Strs.dblQuotation);
	    if (this.line.startsWith(Strs.quotation))
		return this.extractQuotedValue(Strs.quotation);
	    else
		return Collections.emptyList();
	}

	protected String extractQuotedValueAsLine() throws UnfinishedStatementException {
	    return this.extractQuotedValue().stream()
		.reduce(Strs.empty, (v1, v2) -> v1 + Strs.space + v2).trim();
	}

	private List<String> extractQuotedValue(String quatation)
	    throws UnfinishedStatementException {
	    this.substringAndNext(1);
	    int brk;
	    List<String> values = XGen.newArrayList();
	    while ((brk = this.line.indexOf(quatation)) == -1) {
		values.add(this.line);
		this.line = this.res.poll();
		if (this.line == null)
		    throw new UnfinishedStatementException("Quatation is not closed correctly.");
	    }
	    values.add(this.line.substring(0, brk));
	    this.substringAndNext(brk + 1);
	    return values;
	}

	protected void substringAndNext(int begin) {
	    this.line = begin < this.line.length()
		    ? this.line.substring(begin).trim() : Strs.empty;
	    if (this.line.isEmpty())
		this.removeAndNext();
	}

	protected void removeAndNext() {
	    while ((this.line = this.res.poll()).isEmpty())
		;
	}
    }

    private class PreProcessor extends InterpreterBase {

	private PreProcessor(LinkedList<String> res, BlockingQueue<TIRecord> q) {
	    super(res, q, res.poll());
	}

	private String exe() throws UnfinishedStatementException {
	    this.skipXmlTagIfNeeded();
	    this.distinctDocType();
	    return this.line;
	}

	private void skipXmlTagIfNeeded() throws UnfinishedStatementException {

	    if (!this.line.startsWith(Const.xmlDeclarationPrefix))
		return;
	    this.substringAndNext(5);

	    int brk = 0;
	    while (this.line != null) {
		if ((brk = this.line.indexOf(Const.xmlDeclarationSuffix)) == -1)
		    this.line = this.res.poll();
		else
		    break;
	    }

	    if (this.line == null)
		throw new UnfinishedStatementException("Xml declaration is not closed.");
	    else
		this.substringAndNext(brk + 2);
	}

	private void distinctDocType() throws UnfinishedStatementException {

	    if (!XStrings.startsWithIgnoreCase(this.line, Const.docTypePrefix)) {
		this.addDoctype2q(HtmlDocType.None);
		return;
	    }
	    this.substringAndNext(9);

	    if (!XStrings.startsWithIgnoreCase(this.line, Const.docTypeElement1))
		throw new UnfinishedStatementException(
		    "Doctype is only supported html declaration. i.e. <!DOCTYPE html>");
	    this.substringAndNext(Const.docTypeElement1.length());

	    if (this.line.startsWith(Strs.bracket2end)) {
		this.substringAndNext(1);
		this.addDoctype2q(HtmlDocType.V5);
		return;
	    } else if (!XStrings.startsWithIgnoreCase(this.line, Const.docTypeElement2))
		throw new UnfinishedStatementException(
		    "Distincting Doctype failed. It expects <!DOCTYPE html PUBLIC...");
	    this.substringAndNext(Const.docTypeElement2.length());

	    HtmlDocType type = HtmlDocType.parseByPublicIdentifier(this.extractQuotedValueAsLine());
	    if (type == null)
		throw new UnfinishedStatementException(
		    "Public identifier in Doctype is not found.");

	    if (!type.needSystemIdentifier())
		if (this.line.startsWith(Strs.bracket2end)) {
		    this.substringAndNext(1);
		    this.addDoctype2q(type);
		    return;
		} else
		    throw new UnfinishedStatementException(
			"Public identifier in Doctype is not found.");

	    this.extractQuotedValue();
	    if (this.line.startsWith(Strs.bracket2end)) {
		substringAndNext(1);
		this.addDoctype2q(type);
	    } else
		throw new UnfinishedStatementException(
		    "Public identifier in Doctype is not found.");
	}

	private void addDoctype2q(HtmlDocType type) {
	    this.q.add(new TIRecord() {

		@Override
		public RecType getType() {
		    return RecType.Doctype;
		}

		@Override
		public List<String> getAsContents() {
		    return null;
		}

		@Override
		public TagRecord getAsTag() {
		    return null;
		}

		@Override
		public HtmlDocType getAsDoctype() {
		    return type;
		}
	    });
	}
    }

    private class LineProcessor extends InterpreterBase {

	private HtmlTag notNested = null;

	private LineProcessor(LinkedList<String> res, BlockingQueue<TIRecord> q, String line) {
	    super(res, q, line);
	}

	private void exe() throws IllegalFormatException, UnfinishedStatementException {
	    while (this.line != null)
		this.r4processByRecord();
	}

	private void r4processByRecord()
	    throws IllegalFormatException, UnfinishedStatementException {

	    if (this.notNested != null)
		this.processAsNotNestedTag();

	    else if (this.line.startsWith(Strs.bracket2start)) {
		if (this.line.startsWith(Const.tagEnclosurePrefix))
		    this.processAsTagEnclosure();
		else if (this.line.startsWith(Const.commentPrefix))
		    this.processAsComments();
		else
		    this.processAsTag();

	    } else
		this.processAsContents();
	}

	private void processAsNotNestedTag() {

	    String tagEnclosure = Const.tagEnclosurePrefix + this.notNested.str;
	    boolean divided = false;
	    String keep = null;
	    Pair<String, Integer> brk = null;
	    List<String> contents = XGen.newArrayList();

	    // TODO 
	    
	    this.addContents2q(RecType.Contents, contents);
	    this.q.add(TagRecord.newEnclosure(notNested));
	    this.notNested = null;
	}

	private void processAsTag() throws IllegalFormatException, UnfinishedStatementException {

	    var brk = XStrings.firstIndexOf(this.line, tagDelimiter1);
	    var tagStr = brk == null ? this.line.substring(1) : this.line.substring(1, brk.v2);
	    HtmlTag tag = HtmlTag.parse(tagStr);
	    if (tag == null)
		throw new IllegalFormatException("Unexpected tag phrase is found. -> " + tagStr);

	    if (brk != null)
		this.substringAndNext(brk.v2 + 1);
	    else
		this.removeAndNext();

	    XMap<String, String> properties = null;
	    if (brk == null || brk.v1 != '>')
		if (this.extractProperties() == null)
		    throw new IllegalFormatException();

	    this.q.add(TagRecord.newi(tag, properties));
	    if (notNestedTags.contains(tag))
		this.notNested = tag;
	}

	private void processAsTagEnclosure() throws IllegalFormatException {

	    this.substringAndNext(2);
	    var brk = XStrings.firstIndexOf(this.line, Strs.bracket2end);
	    var tagStr = brk == null ? this.line : this.line.substring(0, brk.v2);
	    HtmlTag tag = HtmlTag.parse(tagStr);
	    if (tag == null)
		throw new IllegalFormatException("Unexpected tag phrase is found. -> " + tagStr);
	    if (brk == null) {
		this.removeAndNext();
		if (!this.line.startsWith(Strs.bracket2end))
		    throw new IllegalFormatException(
			"Unexpected format is found in tag enclosure.");
		this.substringAndNext(1);
	    } else
		this.substringAndNext(brk.v2 + 1);

	    this.q.add(TagRecord.newEnclosure(tag));
	}

	private void processAsContents() {
	    Pair<String, Integer> brk;
	    List<String> contents = XGen.newArrayList();
	    while ((brk = XStrings.firstIndexOf(this.line, Strs.bracket2start)) == null) {
		contents.add(this.line);
		this.removeAndNext();
	    }
	    contents.add(this.line.substring(0, brk.v2));
	    this.substringAndNext(brk.v2);
	    this.addContents2q(RecType.Contents, contents);
	}

	private void processAsComments() {
	    this.substringAndNext(Const.commentPrefix.length());
	    Pair<String, Integer> brk;
	    List<String> comments = XGen.newArrayList();
	    while ((brk = XStrings.firstIndexOf(this.line, Const.commentSuffix)) == null) {
		comments.add(this.line);
		this.removeAndNext();
	    }
	    comments.add(this.line.substring(0, brk.v2));
	    this.substringAndNext(brk.v2 + Const.commentSuffix.length());
	    this.addContents2q(RecType.Comments, comments);
	}

	private XMap<String, String> extractProperties()
	    throws UnfinishedStatementException, IllegalFormatException {

	    boolean done = false;
	    var props = PairListGen.<String, String>newi();
	    while (!done && this.line != null) {
		var brk = XStrings.firstIndexOf(this.line, Strs.equal, Strs.bracket2end);
		if (brk == null) {
		    XStrings.applyEachToken(this.line, k -> props.add(k, null));
		    this.removeAndNext();

		} else if (Strs.equal.equals(brk.v1)) {
		    XStrings.applyEachToken(
			this.line.substring(0, brk.v2), k -> props.add(k, null));
		    if (props.isEmpty())
			throw new IllegalFormatException(
			    "Tag property key is not found at left side of equal character.");
		    this.substringAndNext(brk.v2 + 1);
		    props.setV2(props.size() - 1, this.extractQuotedValueAsLine());

		} else {
		    XStrings.applyEachToken(
			this.line.substring(0, brk.v2), k -> props.add(k, null));
		    this.substringAndNext(brk.v2 + 1);
		    done = true;
		}
	    }

	    return props.toMapWithApplyingFirstValue();
	}

	private void addContents2q(RecType type, List<String> contents) {
	    this.q.add(new TIRecord() {

		@Override
		public RecType getType() {
		    return type;
		}

		@Override
		public List<String> getAsContents() {
		    return contents;
		}

		@Override
		public TagRecord getAsTag() {
		    return null;
		}

		@Override
		public HtmlDocType getAsDoctype() {
		    return null;
		}
	    });
	}
    }
}
