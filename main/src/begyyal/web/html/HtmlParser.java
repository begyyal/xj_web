package begyyal.web.html;

import java.util.List;

import begyyal.web.html.object.HtmlObject.RootHtmlObject;

public interface HtmlParser {
    public RootHtmlObject process(List<String> resource);
}
