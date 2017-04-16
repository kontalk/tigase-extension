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

import org.kontalk.xmppserver.probe.DataServerlistRepository;
import org.kontalk.xmppserver.probe.ServerlistRepository;
import tigase.component.AbstractComponent;
import tigase.component.AbstractContext;
import tigase.component.modules.Module;
import tigase.component.modules.impl.AdHocCommandModule;
import tigase.component.modules.impl.DiscoveryModule;
import tigase.component.modules.impl.JabberVersionModule;
import tigase.component.modules.impl.XmppPingModule;
import tigase.conf.ConfigurationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Component that handles commands for the network (e.g. server list).
 * @author Daniele Ricci
 */
public class NetworkComponent extends AbstractComponent<NetworkContext> {

    private DataServerlistRepository serverListRepo;

    private class NetworkContextImpl extends AbstractContext implements NetworkContext {
        public NetworkContextImpl(AbstractComponent<?> component) {
            super(component);
        }

        @Override
        public List<ServerlistRepository.ServerInfo> getServerList() {
            return serverListRepo.getList();
        }
    }

    @Override
    protected NetworkContext createContext() {
        return new NetworkContextImpl(this);
    }

    @Override
    public String getComponentVersion() {
        String version = this.getClass().getPackage().getImplementationVersion();
        return version == null ? "0.0.0" : version;
    }

    @Override
    protected Map<String, Class<? extends Module>> getDefaultModulesList() {
        final Map<String, Class<? extends Module>> result = new HashMap<String, Class<? extends Module>>();
        result.put(XmppPingModule.ID, XmppPingModule.class);
        result.put(JabberVersionModule.ID, JabberVersionModule.class);
        result.put(AdHocCommandModule.ID, AdHocCommandModule.class);
        result.put(DiscoveryModule.ID, DiscoveryModule.class);
        return result;
    }

    @Override
    public String getDiscoCategory() {
        return "component";
    }

    @Override
    public String getDiscoCategoryType() {
        return "generic";
    }

    @Override
    public String getDiscoDescription() {
        return "Network information";
    }

    @Override
    public boolean isDiscoNonAdmin() {
        return true;
    }

    @Override
    public void setProperties(Map<String, Object> props) throws ConfigurationException {
        super.setProperties(props);

        AdHocCommandModule<?> adHocCommandModule = getModuleProvider().getModule(AdHocCommandModule.ID);
        if (adHocCommandModule != null) {
            adHocCommandModule.register(new ServerListCommand(context));
        }

        if (serverListRepo == null) {
            try {
                serverListRepo = new DataServerlistRepository();
                serverListRepo.init(props);
                serverListRepo.reload();
            }
            catch (Exception e) {
                throw new ConfigurationException("error loading server list", e);
            }
        }
    }

}
