package org.kontalk.xmppserver.pgp;


/**
 * A PGP user id.
 * @author Daniele Ricci
 */
public class PGPUserID {

    private final String name;
    private final String comment;
    private final String email;

    public PGPUserID(String name) {
        this(name, null, null);
    }

    public PGPUserID(String name, String email) {
        this(name, email, null);
    }

    public PGPUserID(String name, String comment, String email) {
        this.name = name;
        this.comment = comment;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(name);

        if (comment != null)
            out.append(" (").append(comment).append(')');

        if (email != null)
            out.append(" <").append(email).append('>');

        return out.toString();
    }

}
