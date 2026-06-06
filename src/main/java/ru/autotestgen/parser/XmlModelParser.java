package ru.autotestgen.parser;

import ru.autotestgen.common.ParserException;
import ru.autotestgen.model.AppModel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static ru.autotestgen.parser.StaxUtils.attr;
import static ru.autotestgen.parser.XmlNamespaces.NS_E;
import static ru.autotestgen.parser.XmlNamespaces.NS_E3;

/**
 * Facade for parsing E3Core XML metadata into an {@link AppModel}.
 * Dispatches each top-level element to the matching specialised parser:
 * {@code <Object>} → {@link EntityParser}, {@code <Searches>} → {@link SearchParser}.
 */
public class XmlModelParser {

    private final EntityParser entityParser;
    private final SearchParser searchParser;

    public XmlModelParser() {
        this(new EntityParser(), new SearchParser());
    }

    public XmlModelParser(EntityParser entityParser, SearchParser searchParser) {
        this.entityParser = entityParser;
        this.searchParser = searchParser;
    }

    public AppModel parse(File xmlFile) throws ParserException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);

        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(fis, "UTF-8");
            AppModel model = new AppModel();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ns = reader.getNamespaceURI();
                    String local = reader.getLocalName();

                    if ("Category".equals(local) && NS_E3.equals(ns)) {
                        model.setCategoryName(attr(reader, "CategoryName"));
                        model.setGuid(attr(reader, "GUID"));
                    } else if ("Object".equals(local) && NS_E.equals(ns)) {
                        model.getEntities().add(entityParser.parseObject(reader));
                    } else if ("Searches".equals(local) && NS_E3.equals(ns)) {
                        model.setSearches(searchParser.parseSearches(reader));
                    }
                }
            }
            reader.close();
            return model;
        } catch (IOException | XMLStreamException e) {
            throw new ParserException("Failed to parse XML model: " + e.getMessage(), e);
        }
    }
}
