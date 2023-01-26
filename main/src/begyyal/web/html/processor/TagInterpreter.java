package begyyal.web.html.processor;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;

import begyyal.commons.constant.Strs;
import begyyal.commons.util.function.XStrings;
import begyyal.web.html.constant.Const;
import begyyal.web.html.constant.HtmlDocType;
import begyyal.web.html.constant.RecType;
import begyyal.web.html.object.TIRecord;
import begyyal.web.html.object.TagRecord;

public class TagInterpreter {

    private final BlockingQueue<TIRecord> q;

    TagInterpreter(BlockingQueue<TIRecord> q) {
	this.q = q;
    }

    public void process(LinkedList<String> res) {
	try {
	    new LineProcessor(res, q, new PreProcessor(res, q).exe()).exe();
	} catch (UnfinishedStatement e) {
	    System.out.println(e.getMessage());
	    e.printStackTrace();
	} finally {
	    q.add(new TIRecord() {
		@Override
		public RecType getType() {
		    return RecType.Fin;
		}

		@Override
		public String getAsContents() {
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
	    });
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

	protected String extractQuotedValue() throws UnfinishedStatement {
	    if (this.line.startsWith(Strs.dblQuotation))
		return this.extractQuotedValue(Strs.dblQuotation);
	    if (this.line.startsWith(Strs.quotation))
		return this.extractQuotedValue(Strs.quotation);
	    else
		return null;
	}

	private String extractQuotedValue(String quatation) throws UnfinishedStatement {
	    this.substringAndNext(1);
	    int brk;
	    String value = Strs.empty;
	    while ((brk = this.line.indexOf(quatation)) == -1) {
		value += (this.line + Strs.lineFeed);
		this.line = this.res.poll();
		if (this.line == null)
		    throw new UnfinishedStatement("Quatation is not closed correctly.");
	    }
	    value += this.line.substring(0, brk);
	    this.substringAndNext(brk + 1);
	    return value;
	}

	protected void substringAndNext(int begin) {
	    this.line = begin <= this.line.length()
		    ? this.line.substring(begin).trim() : Strs.empty;
	    if (this.line.isEmpty())
		while ((this.line = this.res.poll()).isEmpty())
		    ;
	}
    }

    private class PreProcessor extends InterpreterBase {

	private PreProcessor(LinkedList<String> res, BlockingQueue<TIRecord> q) {
	    super(res, q, res.poll());
	}

	private String exe() throws UnfinishedStatement {
	    this.skipXmlTagIfNeeded();
	    this.distinctDocType();
	    return this.line;
	}

	private void skipXmlTagIfNeeded() throws UnfinishedStatement {

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
		throw new UnfinishedStatement("Xml declaration is not closed.");
	    else
		this.substringAndNext(brk + 2);
	}

	private void distinctDocType() throws UnfinishedStatement {

	    if (!XStrings.startsWithIgnoreCase(this.line, Const.docTypePrefix)) {
		this.addDoctype2q(HtmlDocType.None);
		return;
	    }
	    this.substringAndNext(9);

	    if (!XStrings.startsWithIgnoreCase(this.line, Const.docTypeElement1))
		throw new UnfinishedStatement(
		    "Doctype is only supported html declaration. i.e. <!DOCTYPE html>");
	    this.substringAndNext(Const.docTypeElement1.length());

	    if (this.line.startsWith(Strs.bracket2end)) {
		this.substringAndNext(1);
		this.addDoctype2q(HtmlDocType.V5);
		return;
	    } else if (!XStrings.startsWithIgnoreCase(this.line, Const.docTypeElement2))
		throw new UnfinishedStatement(
		    "Distincting Doctype failed. It expects <!DOCTYPE html PUBLIC...");
	    this.substringAndNext(Const.docTypeElement2.length());

	    HtmlDocType type = HtmlDocType.parseByPublicIdentifier(this.extractQuotedValue());
	    if (type == null)
		throw new UnfinishedStatement("Public identifier in Doctype is not found.");

	    if (!type.needSystemIdentifier())
		if (this.line.startsWith(Strs.bracket2end)) {
		    this.substringAndNext(1);
		    this.addDoctype2q(type);
		    return;
		} else
		    throw new UnfinishedStatement("Public identifier in Doctype is not found.");

	    this.extractQuotedValue();
	    if (this.line.startsWith(Strs.bracket2end)) {
		substringAndNext(1);
		this.addDoctype2q(type);
	    } else
		throw new UnfinishedStatement("Public identifier in Doctype is not found.");
	}

	private void addDoctype2q(HtmlDocType type) {
	    this.q.add(new TIRecord() {

		@Override
		public RecType getType() {
		    return RecType.Doctype;
		}

		@Override
		public String getAsContents() {
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

	private LineProcessor(LinkedList<String> res, BlockingQueue<TIRecord> q, String line) {
	    super(res, q, line);
	}

	private String exe() {
	    while (!this.line.isEmpty())
		;
	    return this.line;
	}

	private void addContents2q(String contents) {
	    this.q.add(new TIRecord() {

		@Override
		public RecType getType() {
		    return RecType.Contents;
		}

		@Override
		public String getAsContents() {
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

    @SuppressWarnings("serial")
    private class UnfinishedStatement extends Exception {
	private UnfinishedStatement(String msg) {
	    super(msg);
	}

	private UnfinishedStatement() {
	    super("TagInterpreter failed to process.");
	}
    }
}
