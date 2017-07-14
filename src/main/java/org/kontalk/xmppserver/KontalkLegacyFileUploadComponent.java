/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmppserver;

import org.kontalk.xmppserver.upload.FileUploadProvider;
import org.kontalk.xmppserver.upload.KontalkDropboxProvider;
import tigase.conf.ConfigurationException;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Kontalk legacy file upload component.
 * https://github.com/kontalk/specs/wiki/XMPP-extensions#file-upload
 * @author Daniele Ricci
 */
public class KontalkLegacyFileUploadComponent extends AbstractMessageReceiver {
    //private static Logger log = Logger.getLogger(KontalkLegacyFileUploadComponent.class.getName());

    private static final String DISCO_DESCRIPTION = "Legacy Kontalk file upload";
    private static final String NODE = "http://kontalk.org/extensions/message#upload";
    private static final String XMLNS = NODE;
    private static final Element top_feature = new Element("feature", new String[] { "var" },  new String[] { NODE });
    private static final List<Element> DISCO_FEATURES = Arrays.asList(top_feature);

    private static final int NUM_THREADS = 20;

    private FileUploadProvider provider = new KontalkDropboxProvider();

    @Override
    public void processPacket(Packet packet) {
        // upload info request
        if (packet.getElemName().equals(Iq.ELEM_NAME) && packet.getType() == StanzaType.get) {
            Element upload = packet.getElement().getChild("upload", XMLNS);
            if (upload != null && provider.getName().equals(upload.getAttributeStaticStr("node"))) {
                packet.processedBy(getComponentInfo().getName());

                Element info = provider.getServiceInfo();
                Packet result = packet.okResult(info, 1);
                // workaround for okResult
                result.getElement().getChild("upload", XMLNS)
                        .setAttribute("node", upload.getAttributeStaticStr("node"));
                addOutPacket(result);
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
                    new String[]{ provider.getNode(),
                            provider.getNode() + "@" + getDefVHostItem().getDomain(),
                            provider.getDescription() }));
            return list;
        }

        return null;
    }

    @Override
    public List<Element> getDiscoFeatures(JID from) {
        return DISCO_FEATURES;
    }

}
