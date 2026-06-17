package ru.autotestgen.parser;

import ru.autotestgen.model.Association;
import ru.autotestgen.model.EntityObject;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static ru.autotestgen.parser.StaxUtils.attr;
import static ru.autotestgen.parser.XmlNamespaces.NS_E;
import static ru.autotestgen.parser.XmlNamespaces.NS_E3;

/**
 * Parses one {@code <Object>} XML block into an {@link EntityObject},
 * delegating nested {@code <Properties>} blocks to {@link PropertyGroupParser}
 * and {@code <AssociationObjectA>} blocks to {@link #parseAssociation}.
 */
public class EntityParser {

    private final PropertyGroupParser pgParser;

    public EntityParser() {
        this(new PropertyGroupParser());
    }

    public EntityParser(PropertyGroupParser pgParser) {
        this.pgParser = pgParser;
    }

    public EntityObject parseObject(XMLStreamReader reader) throws XMLStreamException {
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
                    entity.getPropertyGroups().add(pgParser.parsePropertyGroup(reader));
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
        assoc.setAddFromTree("1".equals(attr(reader, "addFromTree")));

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
}
