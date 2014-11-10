package org.kontalk.xmppserver.registration;

import java.io.IOException;
import java.util.Map;


/**
 * Verification provider for the Android emulator.
 * Sends an SMS via adb sms.
 * @author Daniele Ricci
 */
public class AndroidEmulatorProvider extends AbstractSMSVerificationProvider {

    private String deviceId;
    private String ackInstructions;

    @Override
    public void init(Map<String, Object> settings) {
        super.init(settings);
        deviceId = (String) settings.get("device");
        ackInstructions = "A SMS with a verification code will be sent to emulator " + deviceId + ".";
    }

    @Override
    public String getAckInstructions() {
        return ackInstructions;
    }

    @Override
    public void sendVerificationCode(String code) throws IOException {
        StringBuilder cmd = new StringBuilder("adb -s ")
            .append(deviceId)
            .append(" emu sms send ")
            .append(senderId)
            .append(' ')
            .append(code);

        try {
            Runtime.getRuntime().exec(cmd.toString()).wait();
        }
        catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

}
