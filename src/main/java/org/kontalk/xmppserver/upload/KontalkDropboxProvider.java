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

package org.kontalk.xmppserver.upload;

import tigase.conf.ConfigurationException;
import tigase.xml.Element;

import java.util.Map;


/**
 * Kontalk Dropbox (fileserver) file upload provider.
 * @author Daniele Ricci
 */
public class KontalkDropboxProvider implements FileUploadProvider {

    public static final String PROVIDER_NAME = "kontalkbox";
    private static final String NODE_NAME = PROVIDER_NAME;
    private static final String DESCRIPTION = "Kontalk dropbox service";

    String uri;

    @Override
    public void init(Map<String, Object> props) throws ConfigurationException {
        uri = (String) props.get("uri");
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getNode() {
        return NODE_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Element getServiceInfo() {
        return new Element("uri", uri);
    }

}
