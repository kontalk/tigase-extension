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

import org.kontalk.xmppserver.probe.ServerlistRepository;
import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.xml.Element;
import tigase.xmpp.JID;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Server list ad-hoc command.
 * @author Daniele Ricci
 */
public class ServerListCommand implements AdHocCommand {
    private static final Logger log = Logger.getLogger(ServerListCommand.class.getName());

    private static final String ELEM_NAME = "serverlist";
    private static final String NAMESPACE = "http://kontalk.org/extensions/serverlist";

    private final NetworkContext context;

    public ServerListCommand(NetworkContext context) {
        this.context = context;
    }

    @Override
    public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
        log.log(Level.FINEST, "executing command " + request);

        Element list = new Element(ELEM_NAME);
        list.setXMLNS(NAMESPACE);

        // retrieve server list
        List<ServerlistRepository.ServerInfo> slist = context.getServerList();
        if (slist != null) {
            for (ServerlistRepository.ServerInfo server : slist) {
                list.addChild(new Element("item",
                    new String[]{
                        "node",
                        "fingerprint"
                    },
                    new String[]{
                        server.getHost(),
                        server.getFingerprint()
                    }
                ));
            }
        }

        response.getElements().add(list);
        response.completeSession();
    }

    @Override
    public String getName() {
        return "Retrieve server list";
    }

    @Override
    public String getNode() {
        return "serverlist";
    }

    @Override
    public boolean isAllowedFor(JID jid) {
        return true;
    }
}
