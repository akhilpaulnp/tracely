package dev.tracely.core.traceprocessor

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Minimal protobuf encoder/decoder for trace_processor RPC.
 * Avoids pulling in the full protobuf-kotlin dependency.
 *
 * Wire format reference: https://protobuf.dev/programming-guides/encoding/
 */

// Wire types
private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5

// Encoder
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun writeString(fieldNumber: Int, value: String) {
        writeBytes(fieldNumber, value.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(fieldNumber: Int, value: ByteArray) {
        writeTag(fieldNumber, WIRE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        out.write(value)
    }

    fun writeVarintField(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, WIRE_VARINT)
        writeVarint(value)
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

// Decoder
class ProtoReader(private val bytes: ByteArray) {
    private var pos = 0

    fun hasMore(): Boolean = pos < bytes.size

    /** Returns Pair<fieldNumber, wireType> */
    fun readTag(): Pair<Int, Int> {
        val tag = readVarint().toInt()
        return Pair(tag ushr 3, tag and 0x7)
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= bytes.size) throw RuntimeException("Unexpected end of protobuf at pos $pos")
            val b = bytes[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    fun readDouble(): Double {
        if (pos + 8 > bytes.size) throw RuntimeException("Unexpected end")
        var bits = 0L
        for (i in 0 until 8) {
            bits = bits or ((bytes[pos + i].toLong() and 0xFF) shl (i * 8))
        }
        pos += 8
        return Double.fromBits(bits)
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        if (pos + length > bytes.size) throw RuntimeException("Length out of bounds")
        val result = bytes.copyOfRange(pos, pos + length)
        pos += length
        return result
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            WIRE_VARINT -> readVarint()
            WIRE_FIXED64 -> pos += 8
            WIRE_LENGTH_DELIMITED -> {
                val len = readVarint().toInt()
                pos += len
            }
            WIRE_FIXED32 -> pos += 4
            else -> throw RuntimeException("Unknown wire type: $wireType")
        }
    }

    /** Read a length-delimited submessage as a new ProtoReader */
    fun readMessage(): ProtoReader {
        val msgBytes = readBytes()
        return ProtoReader(msgBytes)
    }

    /** Read a packed repeated field (varints or fixed) */
    fun readPackedVarints(): List<Long> {
        val msgBytes = readBytes()
        val sub = ProtoReader(msgBytes)
        val result = mutableListOf<Long>()
        while (sub.hasMore()) {
            result.add(sub.readVarint())
        }
        return result
    }

    fun readPackedDoubles(): List<Double> {
        val msgBytes = readBytes()
        val sub = ProtoReader(msgBytes)
        val result = mutableListOf<Double>()
        while (sub.hasMore()) {
            result.add(sub.readDouble())
        }
        return result
    }
}

/** Read a length-prefixed protobuf message from a stream (used by stdiod). */
fun readLengthPrefixed(input: InputStream): ByteArray? {
    // Read varint length prefix
    var length = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b == -1) return null
        length = length or ((b and 0x7F).toLong() shl shift)
        if ((b and 0x80) == 0) break
        shift += 7
    }
    val result = ByteArray(length.toInt())
    var read = 0
    while (read < result.size) {
        val n = input.read(result, read, result.size - read)
        if (n == -1) return null
        read += n
    }
    return result
}

/** Write a length-prefixed protobuf message to a stream (used by stdiod). */
fun writeLengthPrefixed(output: java.io.OutputStream, message: ByteArray) {
    val writer = ProtoWriter()
    // Just encode the length as a raw varint
    var len = message.size.toLong()
    while (len and 0x7FL.inv() != 0L) {
        output.write(((len and 0x7FL) or 0x80L).toInt())
        len = len ushr 7
    }
    output.write(len.toInt())
    output.write(message)
}
