/*
 * Kontalk XMPP Tigase extension
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmppserver.auth;

import tigase.auth.DefaultMechanismSelector;
import tigase.xmpp.XMPPResourceConnection;

import javax.security.sasl.SaslServerFactory;


/**
 * SASL mechanism selector for Kontalk legacy token.
 * @author Daniele Ricci
 */
public class KontalkMechanismSelector extends DefaultMechanismSelector {

    protected boolean match(SaslServerFactory factory, String mechanismName, XMPPResourceConnection session) {
        if (session.isTlsRequired() && !session.isEncrypted())
            return false;
        if (factory instanceof KontalkSaslServerFactory) {
            return (mechanismName.equals("EXTERNAL") ||
                    mechanismName.equals(SaslKontalkToken.MECHANISM) ||
                    mechanismName.equals(SaslKontalkPlainToken.MECHANISM));
        }
        return false;
    }

}
