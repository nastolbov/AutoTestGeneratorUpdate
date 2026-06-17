package ru.autotestgen.parser;

import ru.autotestgen.model.Search;
import ru.autotestgen.model.SearchParam;
import ru.autotestgen.model.SearchResult;
import ru.autotestgen.model.SearchResultProperty;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;

import static ru.autotestgen.parser.StaxUtils.attr;
import static ru.autotestgen.parser.StaxUtils.parseInt;
import static ru.autotestgen.parser.XmlNamespaces.NS_E3;

/**
 * Разбирает XML-блок {@code <Searches>} в список объектов {@link Search}
 * вместе с их параметрами и описанием грида результатов.
 */
public class SearchParser {

    public List<Search> parseSearches(XMLStreamReader reader) throws XMLStreamException {
        List<Search> searches = new ArrayList<>();
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String ns = reader.getNamespaceURI();
                String local = reader.getLocalName();

                if ("Search".equals(local) && NS_E3.equals(ns)) {
                    searches.add(parseSingleSearch(reader));
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return searches;
    }

    private Search parseSingleSearch(XMLStreamReader reader) throws XMLStreamException {
        Search search = new Search();
        search.setGuid(attr(reader, "GUID"));
        search.setName(attr(reader, "name"));
        search.setSearchObjectGuid(attr(reader, "searchObjectGUID"));

        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String ns = reader.getNamespaceURI();
                String local = reader.getLocalName();

                if ("SearchQuery".equals(local) && NS_E3.equals(ns)) {
                    search.setQuery(attr(reader, "query"));
                } else if ("SearchParams".equals(local) && NS_E3.equals(ns)) {
                    search.setMinParamCount(parseInt(attr(reader, "minParamCount")));
                } else if ("SearchParam".equals(local) && NS_E3.equals(ns)) {
                    SearchParam param = new SearchParam();
                    param.setName(attr(reader, "name"));
                    param.setTitle(attr(reader, "title"));
                    param.setValueType(attr(reader, "valueType"));
                    param.setMask(attr(reader, "mask"));
                    param.setRequired("true".equals(attr(reader, "required")));
                    param.setOrderNumber(parseInt(attr(reader, "orderNumber")));
                    param.setSearchGuid(attr(reader, "searchGUID"));
                    search.getParams().add(param);
                } else if ("SearchResult".equals(local) && NS_E3.equals(ns)) {
                    SearchResult result = new SearchResult();
                    result.setIdObjectName(attr(reader, "idObjectName"));
                    search.setResult(result);
                } else if ("SearchResultProperty".equals(local) && NS_E3.equals(ns)) {
                    SearchResultProperty prop = new SearchResultProperty();
                    prop.setName(attr(reader, "name"));
                    prop.setTitle(attr(reader, "title"));
                    prop.setValueType(attr(reader, "valueType"));
                    prop.setVisible("true".equals(attr(reader, "visible")));
                    prop.setOrderNumber(parseInt(attr(reader, "orderNumber")));
                    if (search.getResult() != null) {
                        search.getResult().getProperties().add(prop);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return search;
    }
}
