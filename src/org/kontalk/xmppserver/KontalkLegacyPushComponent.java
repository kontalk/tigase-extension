package org.kontalk.xmppserver;

import tigase.conf.ConfigurationException;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;

import java.util.*;


/**
 * Kontalk legacy push component.
 * Supports only GCM.
 * @author Daniele Ricci
 */
public class KontalkLegacyPushComponent extends AbstractMessageReceiver {

    private static final String DISCO_DESCRIPTION = "Legacy Kontalk push notifications";
    private static final String NODE = "http://kontalk.org/extensions/presence#push";
    private static final String XMLNS = NODE;
    private static final Element top_feature = new Element("feature", new String[] { "var" },  new String[] { NODE });
    private static final List<Element> DISCO_FEATURES = Arrays.asList(top_feature);

    public static final String GCM_PROVIDER_NAME = "gcm";
    private static final String GCM_JID_PREFIX = "gcm.push.";
    private static final String GCM_DESCRIPTION = "Google Cloud Messaging push notifications";

    private String gcmProjectId;
    private String gcmApiKey;

    private Map<String, String> storage = new HashMap<String, String>(100);

    @Override
    public void processPacket(Packet packet) {
        if (packet.getElemName().equals(Iq.ELEM_NAME)) {
            Element register = packet.getElement().getChild("register", XMLNS);
            if (register != null && GCM_PROVIDER_NAME.equals(register.getAttributeStaticStr("provider"))) {
                String regId = register.getCData();
                if (regId != null && regId.length() > 0) {
                    storage.put(packet.getStanzaFrom().getBareJID().toString(), regId);
                }
            }
        }

        else if (packet.getElemName().equals(Message.ELEM_NAME)) {
            if ("push".equals(packet.getAttributeStaticStr("type")) && packet
                    .getStanzaFrom().toString().equals(getDefVHostItem().getDomain())) {

                Element push = packet.getElement().getChild("push", XMLNS);
                if (push != null) {
                    String jid = push.getAttributeStaticStr("jid");
                    if (jid != null && jid.length() > 0) {
                        System.out.println("sending notification to " + jid);
                        // TODO send push notification
                    }
                }

            }

        }
    }

    @Override
    public Map<String, Object> getDefaults(Map<String, Object> params) {
        Map<String, Object> defs = super.getDefaults(params);
        defs.put("packet-types", new String[]{"iq"});
        return defs;
    }

    @Override
    public void setProperties(Map<String, Object> props) throws ConfigurationException {
        super.setProperties(props);

        gcmProjectId = (String) props.get("gcm-projectid");
        gcmApiKey = (String) props.get("gcm-apikey");
    }

    @Override
    public String getDiscoDescription() {
        return DISCO_DESCRIPTION;
    }

    @Override
    public String getDiscoCategoryType() {
        return "generic";
    }

    @Override
    public List<Element> getDiscoItems(String node, JID jid, JID from) {
        if (NODE.equals(node)) {
            List<Element> list = new ArrayList<Element>(1);
            list.add(new Element("item", new String[]{"node", "jid", "name"},
                    new String[]{ gcmProjectId, GCM_JID_PREFIX + getDefVHostItem().getDomain(), GCM_DESCRIPTION }));
            return list;
        }

        return null;
    }

    @Override
    public List<Element> getDiscoFeatures(JID from) {
        return DISCO_FEATURES;
    }

}
