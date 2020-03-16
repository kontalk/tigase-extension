package org.kontalk.xmppserver.pgp;

import fm.last.commons.kyoto.ReadOnlyVisitor;

public class BerkeleyKeyringMigration {

    /**
     * Utility main for migrating from a Kyoto Cabinet keyring.
     * @param args [kyoto cabinet path, berkeley db environment]
     */
    public static void main(String[] args) throws Exception {
        KyotoPGPLocalKeyring oldDb = new KyotoPGPLocalKeyring(args[0]);
        BerkeleyPGPLocalKeyring newDb = new BerkeleyPGPLocalKeyring(args[1]);

        oldDb.db.iterate(new ReadOnlyVisitor() {
            @Override
            public void record(byte[] key, byte[] value) {
                try {
                    newDb.importKey(value);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void emptyRecord(byte[] key) {
            }
        });

        long oldCount = oldDb.db.recordCount();
        long newCount = newDb.db.count();
        System.out.println("Old count: " + oldCount + ", new count: " + newCount);
    }

}
