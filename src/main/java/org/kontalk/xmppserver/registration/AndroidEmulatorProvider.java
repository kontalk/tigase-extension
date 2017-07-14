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

package org.kontalk.xmppserver.registration;

import tigase.conf.ConfigurationException;
import tigase.db.TigaseDBException;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Verification provider for the Android emulator.
 * Sends an SMS via adb sms.
 * @author Daniele Ricci
 */
public class AndroidEmulatorProvider extends SMSDataStoreVerificationProvider {
    private static Logger log = Logger.getLogger(AndroidEmulatorProvider.class.getName());

    private String deviceId;
    private String ackInstructions;

    @Override
    public void init(Map<String, Object> settings) throws TigaseDBException, ConfigurationException {
        super.init(log, settings);
        deviceId = (String) settings.get("device");
        ackInstructions = "A SMS with a verification code will be sent to emulator " + deviceId + ".";
    }

    @Override
    public String getAckInstructions() {
        return ackInstructions;
    }

    @Override
    protected void sendVerificationCode(String phoneNumber, String code) throws IOException {
        Runtime.getRuntime().exec("adb -s " + deviceId + " emu sms send " + senderId + ' ' + code);
    }

}
