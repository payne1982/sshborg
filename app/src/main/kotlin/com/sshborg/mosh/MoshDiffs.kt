package com.sshborg.mosh

/**
 * The two state diffs that travel inside an instruction (`docs/mosh-wire-format.md` §3).
 *
 * Both are a repeated `Instruction` in field 1, and each of those carries exactly one proto2
 * extension — on the wire simply a nested message under its own field number. Our client writes
 * the one going up and reads the one coming down; it never has to *compute* a screen diff, which
 * is the server's job and the larger half of mosh.
 */
object MoshDiffs {

    private const val FIELD_INSTRUCTION = 1

    // Client to server: ClientBuffers.
    private const val FIELD_KEYSTROKE = 2
    private const val FIELD_USER_RESIZE = 3
    private const val FIELD_KEYS = 4

    // Server to client: HostBuffers.
    private const val FIELD_HOSTBYTES = 2
    private const val FIELD_HOST_RESIZE = 3
    private const val FIELD_ECHO_ACK = 7
    private const val FIELD_HOSTSTRING = 4
    private const val FIELD_ECHO_ACK_NUM = 8

    // Shared by both resize messages.
    private const val FIELD_WIDTH = 5
    private const val FIELD_HEIGHT = 6

    /** One step of what the user did, in the order it happened. */
    sealed interface UserAction {
        /** Bytes typed, exactly as they should reach the pty — escape sequences included. */
        class Keystrokes(val keys: ByteArray) : UserAction

        class Resize(val width: Int, val height: Int) : UserAction
    }

    /** One step of what the host did. */
    sealed interface HostAction {
        /**
         * Terminal output to feed to the emulator. Applying it can produce a reply (device status
         * and identification answers); that reply has to go back as [UserAction.Keystrokes], not
         * into a discarded buffer.
         */
        class HostBytes(val bytes: ByteArray) : HostAction

        class Resize(val width: Int, val height: Int) : HostAction

        /** For predictive echo only; read and ignored until there is a prediction engine. */
        class EchoAck(val number: Long) : HostAction
    }

    fun encodeUserDiff(actions: List<UserAction>): ByteArray {
        val message = ProtoWriter()
        for (action in actions) {
            val instruction = ProtoWriter()
            when (action) {
                is UserAction.Keystrokes ->
                    instruction.bytes(FIELD_KEYSTROKE, ProtoWriter().bytes(FIELD_KEYS, action.keys).toByteArray())
                is UserAction.Resize ->
                    instruction.bytes(FIELD_USER_RESIZE, resize(action.width, action.height))
            }
            message.bytes(FIELD_INSTRUCTION, instruction.toByteArray())
        }
        return message.toByteArray()
    }

    fun decodeHostDiff(diff: ByteArray): List<HostAction> {
        val out = ArrayList<HostAction>()
        val outer = ProtoReader(diff)
        outer.forEachField { field, wireType ->
            if (field != FIELD_INSTRUCTION || wireType != WIRE_BYTES) {
                outer.skip(wireType)
                return@forEachField
            }
            val instruction = ProtoReader(outer.readBytes())
            instruction.forEachField { ext, extWire ->
                when {
                    ext == FIELD_HOSTBYTES && extWire == WIRE_BYTES ->
                        hostBytes(instruction.readBytes())?.let { out.add(it) }
                    ext == FIELD_HOST_RESIZE && extWire == WIRE_BYTES ->
                        out.add(readResize(instruction.readBytes()))
                    ext == FIELD_ECHO_ACK && extWire == WIRE_BYTES ->
                        out.add(HostAction.EchoAck(readEchoAck(instruction.readBytes())))
                    // A future mosh may add an extension; ignoring it is better than refusing the
                    // frame, which would lose the screen for the sake of a field we do not need.
                    else -> instruction.skip(extWire)
                }
            }
        }
        return out
    }

    private fun resize(width: Int, height: Int): ByteArray =
        ProtoWriter().varint(FIELD_WIDTH, width.toLong()).varint(FIELD_HEIGHT, height.toLong()).toByteArray()

    private fun hostBytes(body: ByteArray): HostAction.HostBytes? {
        var bytes: ByteArray? = null
        val r = ProtoReader(body)
        r.forEachField { field, wireType ->
            if (field == FIELD_HOSTSTRING && wireType == WIRE_BYTES) bytes = r.readBytes() else r.skip(wireType)
        }
        return bytes?.let { HostAction.HostBytes(it) }
    }

    private fun readResize(body: ByteArray): HostAction.Resize {
        var width = 0
        var height = 0
        val r = ProtoReader(body)
        r.forEachField { field, wireType ->
            when {
                field == FIELD_WIDTH && wireType == WIRE_VARINT -> width = r.readVarint().toInt()
                field == FIELD_HEIGHT && wireType == WIRE_VARINT -> height = r.readVarint().toInt()
                else -> r.skip(wireType)
            }
        }
        return HostAction.Resize(width, height)
    }

    private fun readEchoAck(body: ByteArray): Long {
        var number = 0L
        val r = ProtoReader(body)
        r.forEachField { field, wireType ->
            if (field == FIELD_ECHO_ACK_NUM && wireType == WIRE_VARINT) number = r.readVarint() else r.skip(wireType)
        }
        return number
    }
}
