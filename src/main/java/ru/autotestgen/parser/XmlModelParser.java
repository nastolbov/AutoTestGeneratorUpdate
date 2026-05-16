package ru.autotestgen.parser;

import ru.autotestgen.common.ParserException;
import ru.autotestgen.model.*;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * StAX-based parser for E3Core XML metadata models.
 * Parses the XML into an AppModel containing entities, properties, operations, and searches.
 */
public class XmlModelParser {

    private static final String NS_E = "uuid:EDDBACC6-A83C-4937-9748-B7333C7C9272";
    private static final String NS_E3 = "uuid:EF6807BA-EBA2-42E5-9234-A24542B8791C";
    private static final String NS_MD = "urn:ruitsol-ru:E3";

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
                        model.getEntities().add(parseObject(reader));
                    } else if ("Searches".equals(local) && NS_E3.equals(ns)) {
                        model.setSearches(parseSearches(reader));
                    }
                }
            }
            reader.close();
            return model;
        } catch (IOException | XMLStreamException e) {
            throw new ParserException("Failed to parse XML model: " + e.getMessage(), e);
        }
    }

    private EntityObject parseObject(XMLStreamReader reader) throws XMLStreamException {
        EntityObject entity = new EntityObject();
        entity.setGuid(attr(reader, "GUID"));
        entity.setName(attr(reader, "name"));
        entity.setKeyName(attr(reader, "keyName"));
        entity.setFeatureName(attr(reader, "featureName"));
        entity.setNameValueMethod(attr(reader, "nameValueMethod"));

        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String ns = reader.getNamespaceURI();
                String local = reader.getLocalName();

                if ("AssociationObjectA".equals(local) && NS_E.equals(ns)) {
                    entity.getAssociations().add(parseAssociation(reader));
                    depth--;
                } else if ("Properties".equals(local) && NS_E.equals(ns)) {
                    entity.getPropertyGroups().add(parsePropertyGroup(reader));
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return entity;
    }

    private Association parseAssociation(XMLStreamReader reader) throws XMLStreamException {
        Association assoc = new Association();
        assoc.setGuid(attr(reader, "GUID"));
        assoc.setRoleA(attr(reader, "role_A"));
        assoc.setRoleB(attr(reader, "role_B"));
        assoc.setRoleACaption(attr(reader, "role_A_caption"));
        assoc.setFeatureName(attr(reader, "featureName"));
        assoc.setAssociationId(attr(reader, "associationID"));
        assoc.setFlagDisplay("1".equals(attr(reader, "flag_display")));

        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = reader.getLocalName();
                String ns = reader.getNamespaceURI();

                if ("Qualifier".equals(local) && NS_E3.equals(ns)) {
                    assoc.setSearchGuid(attr(reader, "searchGUID"));
                } else if ("AssociateItem".equals(local) && NS_E3.equals(ns)) {
                    assoc.setAssociateItemGuid(attr(reader, "GUID"));
                    assoc.setAssociateItemName(attr(reader, "name"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return assoc;
    }

    private PropertyGroup parsePropertyGroup(XMLStreamReader reader) throws XMLStreamException {
        PropertyGroup group = new PropertyGroup();
        group.setGuid(attr(reader, "GUID"));
        group.setStereoType(attr(reader, "stereoType"));
        group.setName(attr(reader, "name"));
        group.setDmodule(attr(reader, "dmodule"));
        group.setTypeLink(attr(reader, "type_link"));
        group.setFlagDisplay("1".equals(attr(reader, "flag_display")));
        group.setOrderNumber(parseInt(attr(reader, "order_number")));

        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String ns = reader.getNamespaceURI();
                String local = reader.getLocalName();

                if ("Property".equals(local) && NS_E.equals(ns)) {
                    group.getProperties().add(parseProperty(reader));
                    depth--;
                } else if ("Operation".equals(local) && NS_E3.equals(ns)) {
                    group.setOperation(parseOperation(reader));
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return group;
    }

    private Property parseProperty(XMLStreamReader reader) throws XMLStreamException {
        Property prop = new Property();
        prop.setGuid(attr(reader, "GUID"));
        prop.setName(attr(reader, "name"));
        prop.setStereoType(attr(reader, "stereoType"));
        prop.setDmodule(attr(reader, "dmodule"));
        prop.setAttrName(attr(reader, "attrName"));
        prop.setTableName(attr(reader, "table_name"));
        prop.setAttrType(AttrType.fromXml(attr(reader, "attrType")));
        prop.setRequired("1".equals(attr(reader, "necessarily")));
        prop.setMask(attr(reader, "mask"));
        prop.setOrderNumber(parseInt(attr(reader, "order_number")));
        prop.setFlagDisplay("1".equals(attr(reader, "flag_display")));
        skipToEnd(reader);
        return prop;
    }

    private Operation parseOperation(XMLStreamReader reader) throws XMLStreamException {
        Operation op = new Operation();
        op.setGuid(attr(reader, "GUID"));
        op.setOperationMethod(attr(reader, "operationMethod"));
        op.setOperationModule(attr(reader, "operationModule"));

        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String ns = reader.getNamespaceURI();
                String local = reader.getLocalName();

                if ("OperationParam".equals(local) && NS_E3.equals(ns)) {
                    OperationParam param = new OperationParam();
                    param.setName(attr(reader, "name"));
                    param.setParamType(attr(reader, "paramType"));
                    param.setValueType(attr(reader, "valueType"));
                    op.getParams().add(param);
                } else if ("Modifier".equals(local) && NS_E3.equals(ns)) {
                    Modifier mod = new Modifier();
                    mod.setTitle(attr(reader, "title"));
                    mod.setModifyType(ModifyType.fromCode(attr(reader, "modifyType")));
                    op.getModifiers().add(mod);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return op;
    }

    private List<Search> parseSearches(XMLStreamReader reader) throws XMLStreamException {
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

    private void skipToEnd(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            else if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }

    private String attr(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value != null ? value : "";
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
