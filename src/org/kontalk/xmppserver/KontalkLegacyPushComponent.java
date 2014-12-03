package org.kontalk.xmppserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kontalk.xmppserver.push.GCMProvider;
import org.kontalk.xmppserver.push.PushProvider;

import tigase.conf.ConfigurationException;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;


/**
 * Kontalk legacy push component.
 * Supports only GCM.
 * @author Daniele Ricci
 */
public class KontalkLegacyPushComponent extends AbstractMessageReceiver {

    private static Logger log = Logger.getLogger(KontalkLegacyPushComponent.class.getName());

    private static final String DISCO_DESCRIPTION = "Legacy Kontalk push notifications";
    private static final String NODE = "http://kontalk.org/extensions/presence#push";
    private static final String XMLNS = NODE;
    private static final Element top_feature = new Element("feature", new String[] { "var" },  new String[] { NODE });
    private static final List<Element> DISCO_FEATURES = Arrays.asList(top_feature);

    private static final int NUM_THREADS = 20;

    private PushProvider provider = new GCMProvider();

    @Override
    public void processPacket(Packet packet) {
        // registration
        if (packet.getElemName().equals(Iq.ELEM_NAME)) {
            Element register = packet.getElement().getChild("register", XMLNS);
            if (register != null && provider.getName().equals(register.getAttributeStaticStr("provider"))) {
                String regId = register.getCData();
                if (regId != null && regId.length() > 0) {
                    BareJID user = packet.getStanzaFrom().getBareJID();
                    log.log(Level.FINE, "Registering user {0} to push notifications", user);
                    provider.register(user, regId);
                    packet.processedBy(getComponentInfo().getName());
                    addOutPacket(packet.okResult((String) null, 0));
                }
            }
        }

        // push notification
        else if (packet.getElemName().equals(Message.ELEM_NAME)) {
            if ("push".equals(packet.getAttributeStaticStr("type")) && packet
                    .getStanzaFrom().toString().equals(getDefVHostItem().getDomain())) {

                Element push = packet.getElement().getChild("push", XMLNS);
                if (push != null) {
                    String jid = push.getAttributeStaticStr("jid");
                    if (jid != null && jid.length() > 0) {
                        try {
                            log.log(Level.FINE, "Sending push notification to {0}", jid);
                            // send push notification
                            provider.sendPushNotification(BareJID.bareJIDInstanceNS(jid));
                        }
                        catch (IOException e) {
                            log.log(Level.INFO, "GCM connection error", e);
                        }
                    }
                }

            }

        }
    }

    @Override
    public int processingInThreads() {
        return NUM_THREADS;
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
        provider.init(props);
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
                    new String[]{ provider.getNode(), provider.getJidPrefix() + getDefVHostItem().getDomain(), provider.getDescription() }));
            return list;
        }

        return null;
    }

    @Override
    public List<Element> getDiscoFeatures(JID from) {
        return DISCO_FEATURES;
    }

}
