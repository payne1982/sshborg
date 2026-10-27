package com.sshborg.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    /** Null = password auth; non-null = ID of the key to use */
    val keyId: Long? = null,
    val password: String? = null,
    val encryptedPassword: String? = null,
    val knownHostsEntry: String? = null,
    val agentForwarding: Boolean = false,
    val lastConnected: Long? = null,
    /** Comma-separated jump hosts: "host1:port,host2:port,..." — only used when agentForwarding=true */
    val jumpHosts: String? = null,
    /** Newline-separated known_hosts lines for each jump host, persisted after first connect */
    val jumpHostKeys: String? = null,
    /**
     * Newline-separated local port-forwarding rules in SSH -L syntax:
     * [bindAddr:]localPort:remoteHost:remotePort
     * e.g. "8080:localhost:8080"
     */
    val portForwardings: String? = null,
    /**
     * Jump host mode: "simple" uses the [jumpHosts] text string, "host_list" uses
     * [jumpHostIdList] (comma-separated host entity IDs).
     *
     * `host_list` is the default for a host created from now on, because it is the one that can
     * carry credentials: a hop picked from the host list brings its own key or password, where a
     * hop typed into the text field has none of its own. Hosts that already exist keep whatever
     * they were saved with — the column has been written on every save since it appeared — and a
     * backup that predates the choice is still read as "simple", which is what it meant then.
     */
    val jumpMode: String = "host_list",
    /**
     * Ordered comma-separated IDs of host entities to use as jump hosts.
     * Only used when [jumpMode] == "host_list".
     */
    val jumpHostIdList: String? = null,
    /**
     * SFTP starting directory mode: "last" = auto-save last visited path,
     * "fixed" = always use [sftpStartDir], "home" = always use server home.
     */
    val sftpStartMode: String = "last",
    /** Persisted last-visited path ("last" mode) or user-specified path ("fixed" mode). */
    val sftpStartDir: String? = null,
    /** If true, the SFTP browser shows dotfiles (names starting with "."). Default hides them. */
    val sftpShowHidden: Boolean = false,
    /** If true, legacy/weak cipher algorithms are appended to the negotiation list (for old servers). */
    val allowLegacyCiphers: Boolean = false,
    /** Null = ungrouped; otherwise ID of the [GroupEntity] this host belongs to. */
    val groupId: Long? = null,
    /** Optional per-host ARGB color; overrides the group color. Null = group color or default. */
    val color: Int? = null,
    /**
     * Position inside its section (ungrouped block or group) for the manual list order.
     * Null until the manual order is first seeded, and on every host created afterwards,
     * which is what makes new hosts land at the end of their section. See HostSort.
     */
    val position: Int? = null,
    /** How many terminal sessions were opened to this host; drives the "most used" order. */
    val connectCount: Int = 0,
    /**
     * If true, the terminal for this host rides mosh instead of the SSH shell channel: SSH still
     * logs in, starts `mosh-server` and stays open for SFTP, port forwarding and the agent, but
     * the screen is synchronised over UDP and survives a change of network.
     *
     * Off by default, per host and explicit — there is no detection and no silent fall back to
     * SSH, because somebody who asked for mosh and did not get it is owed the reason.
     *
     * A jump chain is almost excluded. SSH would get through one, but the UDP session afterwards
     * could not: a bastion forwards TCP and the datagrams have nowhere to go. The exception is a
     * chain whose first hop is a mosh host of yours, which is not tunnelled through at all — the
     * session is made *to* that hop over mosh and the rest of the way is typed into it as one
     * `ssh` command. See `BastionHop`. Only the terminal takes that road; SFTP still opens its
     * own session through the whole chain, because that one is SSH.
     */
    val mosh: Boolean = false,
    /**
     * Which address family a mosh session should be made over: `auto`, `ipv4` or `ipv6`.
     *
     * It matters more than it looks. A mosh session is bound to the family it was born on —
     * `mosh-server -s` listens on the address the SSH connection arrived on, one address of one
     * family — so roaming works *within* a family and not across one. A session made over IPv6 on
     * Wi-Fi cannot follow the phone onto a carrier that has no IPv6, and the reverse is just as
     * true on an IPv6-only carrier. Neither can be guessed for somebody else's networks, which is
     * why this is a choice and its default is to let the resolver decide as it always has.
     *
     * A preference, not a demand: if the chosen family has no address, the other is used.
     */
    val moshFamily: String = "auto",
    /**
     * The UDP port range `mosh-server` may choose from, as `60001-60999` or a single port. Null
     * means mosh's own default, which is that whole range — a thousand ports for a firewall rule
     * to open, which is why it can be narrowed. One port per session open at once on that server.
     */
    val moshPorts: String? = null,
    /**
     * What to run on the server, when `mosh-server` is not what a non-interactive shell finds on
     * its PATH. Null means `mosh-server`.
     *
     * Without this, a server that has mosh installed somewhere unusual reports itself as not
     * having it at all, and there is nothing the person can do about a diagnosis that is simply
     * wrong. The stock client has `--server=` for the same reason.
     */
    val moshCommand: String? = null,
)

/**
 * True when this host would log in with a password we kept for it, rather than with a key or by
 * asking at the time.
 *
 * An extension and not a property of the entity, so that Room has nothing to wonder about.
 *
 * It is the one credential that cannot be lent: a key is lent by the agent and a question is
 * asked wherever the session happens to be, but a password we hold can only be given to a server
 * we are talking to ourselves. That is what decides whether a jump chain may be walked from a
 * mosh bastion, where the login past the bastion is run by the bastion's own ssh.
 */
val HostEntity.hasSavedPassword: Boolean
    get() = keyId == null && (!encryptedPassword.isNullOrEmpty() || !password.isNullOrEmpty())
