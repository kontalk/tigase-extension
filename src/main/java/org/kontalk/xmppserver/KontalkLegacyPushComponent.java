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

import org.kontalk.xmppserver.push.*;
import tigase.annotations.TODO;
import tigase.conf.ConfigurationException;
import tigase.db.DBInitException;
import tigase.db.TigaseDBException;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Kontalk legacy push component.
 * Supports only GCM.
 * @author Daniele Ricci
 */
@TODO(note="Support for multiple registrations per user")
public class KontalkLegacyPushComponent extends AbstractMessageReceiver {

    private static Logger log = Logger.getLogger(KontalkLegacyPushComponent.class.getName());

    private static final String DISCO_DESCRIPTION = "Legacy Kontalk push notifications";
    private static final String NODE = "http://kontalk.org/extensions/presence#push";
    private static final String XMLNS = NODE;
    private static final Element top_feature = new Element("feature", new String[] { "var" },  new String[] { NODE });
    private static final List<Element> DISCO_FEATURES = Collections.singletonList(top_feature);

    private static final int NUM_THREADS = 4;
    private static final int NUM_WORKERS = 50;
    private static final int TIMEOUT_DELAY = 10;
    private static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

    private ScheduledExecutorService executor;
    private PushProvider provider;
    private PushRepository repository = new DataPushRepository();

    @Override
    public void processPacket(Packet packet) {
        // registration
        if (packet.getElemName().equals(Iq.ELEM_NAME) && packet.getType() == StanzaType.set) {
            Element register = packet.getElement().getChild("register", XMLNS);
            if (register != null && provider.getName().equals(register.getAttributeStaticStr("provider"))) {
                String regId = register.getCData();
                if (regId != null && regId.length() > 0) {
                    BareJID user = packet.getStanzaFrom().getBareJID();
                    log.log(Level.FINE, "Registering user {0} to push notifications", user);
                    provider.register(user, regId);

                    try {
                        repository.register(user, provider.getName(), regId);
                    } catch (TigaseDBException e) {
                        log.log(Level.INFO, "Database error", e);
                    }

                    packet.processedBy(getName());
                    addOutPacket(packet.okResult((String) null, 0));
                }
                return;
            }

            Element unregister = packet.getElement().getChild("unregister", XMLNS);
            if (unregister != null && provider.getName().equals(unregister.getAttributeStaticStr("provider"))) {
                BareJID user = packet.getStanzaFrom().getBareJID();
                log.log(Level.FINE, "Unregistering user {0} from push notifications", user);
                provider.unregister(user);

                try {
                    repository.unregister(user, provider.getName());
                } catch (TigaseDBException e) {
                    log.log(Level.INFO, "Database error", e);
                }

                packet.processedBy(getName());
                addOutPacket(packet.okResult((String) null, 0));
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
                            BareJID user = BareJID.bareJIDInstanceNS(jid);
                            List<PushRegistrationInfo> infoList = repository.getRegistrationInfo(user);
                            for (PushRegistrationInfo info : infoList) {
                                if (info.getProvider().equals(provider.getName())) {
                                    sendPushNotification(user, info);
                                }
                            }
                        }
                        catch (TigaseDBException e) {
                            log.log(Level.INFO, "Database error", e);
                        }
                    }
                }

            }

        }
    }

    private void sendPushNotification(BareJID user, PushRegistrationInfo info) {
        final Future<?> task = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    provider.sendPushNotification(user, info);
                }
                catch (IOException e) {
                    log.log(Level.INFO, "Push provider error", e);
                }
            }
        });
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                task.cancel(true);
            }
        }, TIMEOUT_DELAY, TIMEOUT_UNIT);
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
    public void initializationCompleted() {
        super.initializationCompleted();
        executor = Executors.newScheduledThreadPool(NUM_WORKERS);
    }

    @Override
    public void setProperties(Map<String, Object> props) throws ConfigurationException {
        super.setProperties(props);

        if (provider == null) {
            String providerClassName = (String) props.get("provider");
            if (providerClassName != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<? extends PushProvider> providerClass =
                            (Class<? extends PushProvider>) Class.forName(providerClassName);
                    provider = providerClass.newInstance();
                }
                catch (ClassNotFoundException e) {
                    throw new ConfigurationException("Provider class not found: " + providerClassName);
                }
                catch (InstantiationException | IllegalAccessException e) {
                    throw new ConfigurationException("Unable to create provider instance for " + providerClassName);
                }
            }
        }

        // instantiate a dummy provider
        if (provider == null)
            provider = new DummyProvider();

        provider.init(props);

        try {
            repository.init(props);
        }
        catch (DBInitException e) {
            throw new ConfigurationException("unable to initialize push data repository", e);
        }
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
