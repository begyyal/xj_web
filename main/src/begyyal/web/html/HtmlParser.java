package begyyal.web.html;

import java.util.List;

import begyyal.web.html.object.HtmlObject;

public interface HtmlParser {
    public HtmlObject process(List<String> resource);
}
