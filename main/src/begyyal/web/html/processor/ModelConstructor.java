package begyyal.web.html.processor;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;

import begyyal.commons.util.function.XUtils;
import begyyal.web.html.constant.RecType;
import begyyal.web.html.object.HtmlObject;
import begyyal.web.html.object.TIRecord;

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
	
	this.q.drainTo(this.records);
	while (this.records.isEmpty() || this.process(ho)) {
	    XUtils.sleep(drainInterval);
	    this.q.drainTo(this.records);
	}
	return ho;
    }

    private boolean process(HtmlObject ho) {
	TIRecord rec = null;
	while ((rec = this.records.poll()) != null) {
	    if (rec.getType() == RecType.Fin)
		return false;

	}
	return true;
    }
}
