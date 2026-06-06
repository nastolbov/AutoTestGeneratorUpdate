package ru.autotestgen.parser;

import ru.autotestgen.model.*;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static ru.autotestgen.parser.StaxUtils.attr;
import static ru.autotestgen.parser.StaxUtils.parseInt;
import static ru.autotestgen.parser.StaxUtils.skipToEnd;
import static ru.autotestgen.parser.XmlNamespaces.NS_E;
import static ru.autotestgen.parser.XmlNamespaces.NS_E3;

/**
 * Parses one {@code <Properties>} XML block into a {@link PropertyGroup},
 * including nested {@link Property} entries and a (possibly absent) {@link Operation}.
 */
public class PropertyGroupParser {

    public PropertyGroup parsePropertyGroup(XMLStreamReader reader) throws XMLStreamException {
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
        prop.setDefValueSource(attr(reader, "defValueSource"));
        prop.setComment(attr(reader, "comment"));
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
}
