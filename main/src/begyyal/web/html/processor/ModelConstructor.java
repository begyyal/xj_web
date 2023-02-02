package begyyal.web.html.processor;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;

import begyyal.commons.util.function.XUtils;
import begyyal.web.exception.IllegalFormatException;
import begyyal.web.html.constant.RecType;
import begyyal.web.html.object.HtmlObject;
import begyyal.web.html.object.TIRecord;
import begyyal.web.html.object.TagRecord;

public class ModelConstructor {

    private static final int drainInterval = 100;
    private final LinkedList<TIRecord> records;
    private final BlockingQueue<TIRecord> q;

    ModelConstructor(BlockingQueue<TIRecord> q) {
	this.records = new LinkedList<>();
	this.q = q;
    }

    public HtmlObject process() {

	HtmlObject ho = null;
	try {
	    var first = this.q.take();
	    if (first.getType() == RecType.Fin)
		return HtmlObject.createFailedRoot();
	    ho = HtmlObject.newRoot(first.getAsDoctype());
	} catch (InterruptedException e) {
	    return HtmlObject.createFailedRoot();
	}

	var cst = new ByRecConstructor(ho);
	this.q.drainTo(this.records);
	try {
	    while (this.records.isEmpty() || this.processDrainedRecs(cst)) {
		XUtils.sleep(drainInterval);
		this.q.drainTo(this.records);
	    }
	} catch (IllegalFormatException e) {
	}

	return ho;
    }

    private boolean processDrainedRecs(ByRecConstructor cst) throws IllegalFormatException {
	TIRecord rec = null;
	while ((rec = this.records.poll()) != null)
	    if (!cst.process(rec))
		return false;
	return true;
    }

    private class ByRecConstructor {
	private final LinkedList<HtmlObject> stack;

	private ByRecConstructor(HtmlObject ho) {
	    this.stack = new LinkedList<>();
	    this.stack.add(ho);
	}

	private boolean process(TIRecord rec) throws IllegalFormatException {

	    boolean cntne = true;
	    if (rec.getType() == RecType.Tag)
		this.processAsTag(rec.getAsTag());

	    else if (rec.getType() == RecType.Contents)
		this.stack.getLast().append(rec.getAsContents());

	    else if (rec.getType() == RecType.Comments) {
		var ho = HtmlObject.newComment(this.stack.getLast());
		ho.append(rec.getAsContents());

	    } else if (rec.getType() == RecType.Fin)
		cntne = false;

	    return cntne;
	}

	private void processAsTag(TagRecord rec) throws IllegalFormatException {
	    if (rec.isEnclosure) {
		var ho = this.stack.removeLast();
		if (ho.tag != rec.type) {
		    ho.markFailure();
		    throw new IllegalFormatException("A tag is enclosed correctly.");
		}
	    } else {
		var ho = HtmlObject.newi(rec.type, this.stack.getLast(), rec.props);
		this.stack.add(ho);
	    }
	}
    }
}
