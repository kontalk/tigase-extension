/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */

/*

4.23 Send Announcement to *All* Users as described in XEP-0133:
http://xmpp.org/extensions/xep-0133.html#announce

AS:Description: Send announcement to all local users
AS:CommandId: http://jabber.org/protocol/admin#announce
AS:Component: sess-man
AS:Group: Notifications
*/

package tigase.admin

import org.kontalk.xmppserver.KontalkKeyring
import tigase.cluster.strategy.ClusteringStrategyIfc
import tigase.db.UserRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Message
import tigase.server.Permissions
import tigase.util.Base64
import tigase.xml.Element
import tigase.xmpp.JID
import tigase.xmpp.StanzaType;


def FROM_JID = "from-jid"
def SUBJECT = "subject"
def MSG_TYPE = "msg-type"
def MSG_BODY = "announcement"

def p = (Iq)packet

def fromJid = Command.getFieldValue(p, FROM_JID)
def subject = Command.getFieldValue(p, SUBJECT)
def msg_type = Command.getFieldValue(p, MSG_TYPE)
def body = Command.getFieldValues(p, MSG_BODY)

def NOTIFY_CLUSTER = "notify-cluster"
boolean clusterMode =  Boolean.valueOf( System.getProperty("cluster-mode", false.toString()) );
boolean notifyCluster = Boolean.valueOf( Command.getFieldValue(packet, NOTIFY_CLUSTER) )

if (fromJid == null || subject == null || msg_type == null || body == null) {
    def res = (Iq)p.commandResult(Command.DataType.form);
    Command.addTitle(res, "Message to all local users")
    Command.addInstructions(res, "Fill out this form to make an announcement to all local users.")

    Command.addFieldValue(res, "FORM_TYPE", "http://jabber.org/protocol/admin", "hidden")

    Command.addFieldValue(res, FROM_JID, fromJid ?: p.getStanzaFrom().getDomain().toString(), "jid-single", "From address")

    Command.addFieldValue(res, SUBJECT, subject ?: "Message from administrators", "text-single", "Subject")

    def msg_types = ["normal", "headline", "chat" ]
    Command.addFieldValue(res, MSG_TYPE, msg_type ?: msg_types[0], "Type", (String[])msg_types, (String[])msg_types)

    if (body == null) {
        body = [""]
    }

    Command.addFieldMultiValue(res, MSG_BODY, body as List)

    if 	( clusterMode  ) {
        Command.addHiddenField(res, NOTIFY_CLUSTER, true.toString())
    }


    return res
}

Queue results = new LinkedList()
if 	( clusterMode && notifyCluster ) {
    if ( null != clusterStrategy ) {
        def cluster = (ClusteringStrategyIfc) clusterStrategy
        List<JID> cl_conns = cluster.getNodesConnected()
        if (cl_conns && cl_conns.size() > 0) {
            cl_conns.each { node ->

                def forward = p.copyElementOnly();
                Command.removeFieldValue(forward, NOTIFY_CLUSTER)
                Command.addHiddenField(forward, NOTIFY_CLUSTER, false.toString())
                forward.setPacketTo( node );
                forward.setPermissions( Permissions.ADMIN );

                results.offer(forward)
            }
        }
    }
}


def jidFrom = JID.jidInstanceNS(fromJid)
def type = StanzaType.valueOf(msg_type)
def msg_body = body.join('\n')

def result = p.commandResult(Command.DataType.result)
Command.addTextField(result, "Note", "Operation successful");
results += result

def msg

try {
    def keyring = KontalkKeyring.getInstance(jidFrom.getDomain())
    def signed_body = Base64.encode(keyring.signData(msg_body.getBytes()))
    msg = Message.getMessage(null, null, type, null, subject, null, "admin")
    def signed_elem = new Element("x", signed_body);
    signed_elem.setXMLNS("jabber:x:signed")
    msg.getElement().addChild(signed_elem)
}
catch (Exception e) {
    msg = Message.getMessage(null, null, type, msg_body, subject, null, "admin")
}

def user_repo = (UserRepository)userRepository
def users = user_repo.getUsers()
users.each { value ->
    def jid = JID.jidInstanceNS(value);
    if (jid.getDomain() == jidFrom.getDomain()) {
        def res = msg.copyElementOnly()
        res.initVars(jidFrom, jid)
        results += res
    }

}

return (Queue)results
