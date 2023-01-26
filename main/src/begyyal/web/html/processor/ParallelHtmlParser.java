package begyyal.web.html.processor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import begyyal.web.html.HtmlParser;
import begyyal.web.html.object.HtmlObject;
import begyyal.web.html.object.TIRecord;

public class ParallelHtmlParser implements HtmlParser {

    public ParallelHtmlParser() {
    }

    @Override
    public HtmlObject process(List<String> res) {
	if (res == null || res.isEmpty())
	    return HtmlObject.createFailedRoot();
	var q = new LinkedBlockingQueue<TIRecord>();
	Executors.newSingleThreadExecutor()
	    .execute(() -> new TagInterpreter(q)
		.process(res.stream()
		    .filter(l -> l != null)
		    .collect(Collectors.toCollection(LinkedList::new))));
	return new ModelConstructor(q).process();
    }
}
