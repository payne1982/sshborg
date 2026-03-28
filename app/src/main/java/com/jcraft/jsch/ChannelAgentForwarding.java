package com.jcraft.jsch;

import java.io.IOException;
import java.util.Vector;

/**
 * Replaces JSch's built-in ChannelAgentForwarding.
 *
 * Needed for two reasons:
 *  1. JSch mwiede 0.2.19 ships its own version that ends up in classes9.dex at runtime,
 *     shadowing any attempt to override via a subclass.  We ship a patched jsch JAR
 *     (ChannelAgentForwarding.class removed) so that only this copy is loaded.
 *  2. The original else-branch called rbuf.skip(n) which advances the *write* pointer
 *     of Buffer (index), not the read pointer (s), leaving stale bytes that corrupt the
 *     next message.  The fix is to advance rbuf.s directly.
 *
 * Handles the auth-agent@openssh.com reverse channel for SSH agent forwarding,
 * including the SSH2_AGENTC_EXTENSION (type 27) message added in OpenSSH 8.x.
 */
class ChannelAgentForwarding extends Channel {

    private static final byte SSH_AGENTC_REQUEST_RSA_IDENTITIES    = 1;
    private static final byte SSH_AGENT_RSA_IDENTITIES_ANSWER      = 2;
    private static final byte SSH_AGENT_FAILURE                    = 5;
    private static final byte SSH_AGENT_SUCCESS                    = 6;
    private static final byte SSH_AGENTC_REMOVE_ALL_RSA_IDENTITIES = 9;
    private static final byte SSH2_AGENTC_REQUEST_IDENTITIES    = 11;
    private static final byte SSH2_AGENT_IDENTITIES_ANSWER      = 12;
    private static final byte SSH2_AGENTC_SIGN_REQUEST          = 13;
    private static final byte SSH2_AGENT_SIGN_RESPONSE          = 14;
    private static final byte SSH2_AGENTC_ADD_IDENTITY          = 17;
    private static final byte SSH2_AGENTC_REMOVE_IDENTITY       = 18;
    private static final byte SSH2_AGENTC_REMOVE_ALL_IDENTITIES = 19;
    private static final int  SSH_AGENT_RSA_SHA2_256            = 2;
    private static final int  SSH_AGENT_RSA_SHA2_512            = 4;

    private Buffer rbuf;    // accumulates incoming channel data
    private Buffer wbuf;    // outgoing packet buffer (lazy-init)
    private Packet packet;  // outgoing packet (lazy-init)
    private Buffer mbuf;    // builds agent response

    ChannelAgentForwarding() {
        super();
        lwsize_max = 0x20000;
        lwsize     = 0x20000;
        lmpsize    = 0x4000;
        type       = Util.str2byte("auth-agent@openssh.com");
        rbuf       = new Buffer();
        rbuf.reset();
        mbuf       = new Buffer();
        connected  = true;
    }

    @Override
    void run() {
        try {
            sendOpenConfirmation();
        } catch (Exception e) {
            close = true;
            disconnect();
        }
    }

    @Override
    void write(byte[] buf, int start, int len) throws IOException {
        // Lazy-init outgoing packet
        if (packet == null) {
            wbuf   = new Buffer(rmpsize > 0 ? rmpsize : 32768);
            packet = new Packet(wbuf);
        }

        // Accumulate incoming bytes
        rbuf.shift();
        if (rbuf.buffer.length < rbuf.index + len) {
            byte[] newbuf = new byte[rbuf.s + len];
            System.arraycopy(rbuf.buffer, 0, newbuf, 0, rbuf.buffer.length);
            rbuf.buffer = newbuf;
        }
        rbuf.putByte(buf, start, len);

        // Wait for a complete message (4-byte length header + body)
        int msgLen = rbuf.getInt();
        if (msgLen > rbuf.getLength()) {
            rbuf.s -= 4;   // put the length bytes back
            return;
        }

        int type = rbuf.getByte();

        Session _session;
        try {
            _session = getSession();
        } catch (JSchException e) {
            throw new IOException(e.toString(), e);
        }

        IdentityRepository repo     = _session.getIdentityRepository();
        UserInfo           userInfo = _session.getUserInfo();

        mbuf.reset();

        if (type == SSH2_AGENTC_REQUEST_IDENTITIES) {
            mbuf.putByte(SSH2_AGENT_IDENTITIES_ANSWER);
            @SuppressWarnings("unchecked")
            Vector<Identity> ids = repo.getIdentities();
            synchronized (ids) {
                int count = 0;
                for (Identity id : ids) {
                    if (id.getPublicKeyBlob() != null) count++;
                }
                mbuf.putInt(count);
                for (Identity id : ids) {
                    byte[] blob = id.getPublicKeyBlob();
                    if (blob == null) continue;
                    mbuf.putString(blob);
                    mbuf.putString(Util.empty);
                }
            }

        } else if (type == SSH_AGENTC_REQUEST_RSA_IDENTITIES) {
            mbuf.putByte(SSH_AGENT_RSA_IDENTITIES_ANSWER);
            mbuf.putInt(0);

        } else if (type == SSH2_AGENTC_SIGN_REQUEST) {
            byte[] keyBlob = rbuf.getString();
            byte[] data    = rbuf.getString();
            int    flags   = rbuf.getInt();

            @SuppressWarnings("unchecked")
            Vector<Identity> ids     = repo.getIdentities();
            Identity         found   = null;
            synchronized (ids) {
                for (Identity id : ids) {
                    byte[] blob = id.getPublicKeyBlob();
                    if (blob == null || !Util.array_equals(keyBlob, blob)) continue;
                    if (id.isEncrypted()) {
                        if (userInfo == null) continue;
                        while (id.isEncrypted()) {
                            if (!userInfo.promptPassphrase("Passphrase for " + id.getName())) break;
                            String pp = userInfo.getPassphrase();
                            if (pp == null) break;
                            try {
                                if (id.setPassphrase(Util.str2byte(pp))) break;
                            } catch (JSchException ex) { break; }
                        }
                    }
                    if (!id.isEncrypted()) { found = id; break; }
                }
            }

            byte[] sig = null;
            if (found != null) {
                try {
                    Buffer keyBuf = new Buffer(keyBlob);
                    String alg    = Util.byte2str(keyBuf.getString());
                    if ("ssh-rsa".equals(alg)) {
                        if ((flags & SSH_AGENT_RSA_SHA2_512) != 0) {
                            sig = found.getSignature(data, "rsa-sha2-512");
                        } else if ((flags & SSH_AGENT_RSA_SHA2_256) != 0) {
                            sig = found.getSignature(data, "rsa-sha2-256");
                        } else {
                            sig = found.getSignature(data, "ssh-rsa");
                        }
                    } else {
                        sig = found.getSignature(data);
                    }
                } catch (Exception e) {
                    // signing failed; sig remains null → failure response below
                }
            }

            if (sig == null) {
                mbuf.putByte(SSH_AGENT_FAILURE);
            } else {
                mbuf.putByte(SSH2_AGENT_SIGN_RESPONSE);
                mbuf.putString(sig);
            }

        } else if (type == SSH2_AGENTC_REMOVE_IDENTITY) {
            repo.remove(rbuf.getString());
            mbuf.putByte(SSH_AGENT_SUCCESS);

        } else if (type == SSH_AGENTC_REMOVE_ALL_RSA_IDENTITIES) {
            // SSHv1 remove-all: no-op (we don't manage RSA1 identities), reply SUCCESS
            mbuf.putByte(SSH_AGENT_SUCCESS);

        } else if (type == SSH2_AGENTC_REMOVE_ALL_IDENTITIES) {
            repo.removeAll();
            mbuf.putByte(SSH_AGENT_SUCCESS);

        } else if (type == SSH2_AGENTC_ADD_IDENTITY) {
            int    rem  = rbuf.getLength();
            byte[] data = new byte[rem];
            rbuf.getByte(data);
            mbuf.putByte(repo.add(data) ? SSH_AGENT_SUCCESS : SSH_AGENT_FAILURE);

        } else {
            // Unknown / unsupported message type (e.g. SSH2_AGENTC_EXTENSION = 27).
            // Advance the read pointer past the remaining body bytes so the next
            // message is not corrupted.  Do NOT use Buffer.skip() here — it advances
            // the write pointer (index), not the read pointer (s).
            rbuf.s += rbuf.getLength();
            mbuf.putByte(SSH_AGENT_FAILURE);
        }

        int    responseLen = mbuf.getLength();
        byte[] response    = new byte[responseLen];
        mbuf.getByte(response);
        send(response);
    }

    private void send(byte[] data) {
        packet.reset();
        wbuf.putByte((byte) 94);          // SSH_MSG_CHANNEL_DATA
        wbuf.putInt(recipient);
        wbuf.putInt(4 + data.length);
        wbuf.putString(data);
        try {
            getSession().write(packet, this, 4 + data.length);
        } catch (Exception e) {
            // channel may already be closing; nothing to do
        }
    }

    @Override
    void eof_remote() {
        super.eof_remote();
        eof();
    }
}
