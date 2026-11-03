package com.junkeat.dnsswitcher.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads raw IPv4 and IPv6 DNS query packets from the TUN interface, forwards them over protected UDP sockets
 * (with automatic RFC 1035 TCP fallback on truncated TC=1 responses) to the currently active
 * upstream DNS server, and writes raw UDP responses back to the TUN interface.
 *
 * Supports atomic hot-swapping of the upstream endpoint without closing the TUN interface or dropping
 * in-flight queries. Strictly never logs or stores DNS queries or domain names.
 */
class DnsPacketLoop(
  private val vpnService: VpnService,
  private val tunInterface: ParcelFileDescriptor,
  initialUpstream: DnsUpstream,
) {

  private val running = AtomicBoolean(false)
  private val activeUpstream = AtomicReference(initialUpstream)
  private val queryExecutor = ThreadPoolExecutor(
    CORE_POOL_SIZE,
    MAX_POOL_SIZE,
    KEEP_ALIVE_SECONDS,
    TimeUnit.SECONDS,
    ArrayBlockingQueue(MAX_QUEUED_QUERIES),
    ThreadPoolExecutor.DiscardOldestPolicy(),
  )
  private val writeLock = Any()
  private var readerThread: Thread? = null

  /**
   * Atomically hot-swaps the upstream DNS server for all subsequent DNS queries.
   * The TUN interface and existing in-flight queries remain completely untouched.
   */
  fun setUpstream(upstream: DnsUpstream) {
    activeUpstream.set(upstream)
  }

  fun start() {
    if (!running.compareAndSet(false, true)) {
      return
    }
    val thread = Thread({ runReadLoop() }, "DnsTunReader")
    readerThread = thread
    thread.start()
  }

  fun stop() {
    if (!running.compareAndSet(true, false)) {
      return
    }
    try {
      tunInterface.close()
    } catch (_: IOException) {
      // Closing descriptor wakes up blocking read
    }
    readerThread?.interrupt()
    queryExecutor.shutdownNow()
    try {
      queryExecutor.awaitTermination(1, TimeUnit.SECONDS)
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  private fun runReadLoop() {
    val inputStream = FileInputStream(tunInterface.fileDescriptor)
    val outputStream = FileOutputStream(tunInterface.fileDescriptor)
    val readBuffer = ByteArray(MAX_PACKET_SIZE)

    while (running.get()) {
      val bytesRead = try {
        inputStream.read(readBuffer)
      } catch (_: IOException) {
        break
      }

      if (bytesRead <= 0) {
        if (!running.get()) {
          break
        }
        continue
      }

      val parsedQuery = UdpPacketCodec.parseDnsQuery(readBuffer, bytesRead) ?: continue
      if (!running.get()) {
        break
      }

      // Snapshot the active upstream at the moment the query arrives
      val targetUpstream = activeUpstream.get()

      try {
        queryExecutor.execute {
          forwardQueryAndRespond(parsedQuery, targetUpstream, outputStream)
        }
      } catch (_: Exception) {
        // Executor rejected task during shutdown
      }
    }
  }

  private fun forwardQueryAndRespond(
    query: DnsQueryPacket,
    upstream: DnsUpstream,
    outputStream: FileOutputStream,
  ) {
    if (!running.get()) {
      return
    }

    val responseBytes = querySingleServer(query.dnsPayload, upstream.address, upstream.port)
      ?: upstream.secondaryAddress?.let { secondary ->
        if (running.get()) querySingleServer(query.dnsPayload, secondary, upstream.port) else null
      }
      ?: return

    if (!running.get() || responseBytes.isEmpty()) {
      return
    }

    try {
      val rawIpResponse = UdpPacketCodec.buildDnsResponse(
        request = query,
        responsePayload = responseBytes,
        responseLength = responseBytes.size,
      )

      synchronized(writeLock) {
        if (running.get()) {
          outputStream.write(rawIpResponse)
          outputStream.flush()
        }
      }
    } catch (_: Exception) {
      // Drop failed TUN write without logging query content
    }
  }

  private fun querySingleServer(dnsPayload: ByteArray, address: String, port: Int): ByteArray? {
    val udpResponse = queryUdp(dnsPayload, address, port)
      ?: (if (port == 53 && isAdGuardAddress(address)) queryUdp(dnsPayload, address, 5353) else null)
      ?: return null

    if (isTruncatedDnsResponse(udpResponse)) {
      return queryTcp(dnsPayload, address, port)
        ?: (if (port == 53 && isAdGuardAddress(address)) queryTcp(dnsPayload, address, 5353) else null)
        ?: udpResponse
    }
    return udpResponse
  }

  private fun isAdGuardAddress(address: String): Boolean =
    address == "94.140.14.14" || address == "94.140.15.15"

  private fun queryUdp(dnsPayload: ByteArray, address: String, port: Int): ByteArray? {
    var socket: DatagramSocket? = null
    try {
      val createdSocket = DatagramSocket()
      socket = createdSocket
      if (!vpnService.protect(createdSocket)) {
        return null
      }
      createdSocket.soTimeout = UPSTREAM_TIMEOUT_MS

      val targetInetAddress = InetAddress.getByName(address)
      val outboundPacket = DatagramPacket(
        dnsPayload,
        dnsPayload.size,
        targetInetAddress,
        port,
      )
      createdSocket.send(outboundPacket)

      val responseBuffer = ByteArray(MAX_DNS_PAYLOAD_SIZE)
      val inboundPacket = DatagramPacket(responseBuffer, responseBuffer.size)
      createdSocket.receive(inboundPacket)

      if (inboundPacket.length <= 0) {
        return null
      }
      return responseBuffer.copyOfRange(0, inboundPacket.length)
    } catch (_: Exception) {
      return null
    } finally {
      socket?.close()
    }
  }

  private fun queryTcp(dnsPayload: ByteArray, address: String, port: Int): ByteArray? {
    var socket: Socket? = null
    try {
      val createdSocket = Socket()
      socket = createdSocket
      if (!vpnService.protect(createdSocket)) {
        return null
      }
      createdSocket.soTimeout = UPSTREAM_TIMEOUT_MS
      createdSocket.connect(InetSocketAddress(InetAddress.getByName(address), port), UPSTREAM_TIMEOUT_MS)

      val out = DataOutputStream(createdSocket.getOutputStream())
      out.writeShort(dnsPayload.size)
      out.write(dnsPayload)
      out.flush()

      val input = DataInputStream(createdSocket.getInputStream())
      val responseLength = input.readUnsignedShort()
      if (responseLength <= 0 || responseLength > MAX_DNS_PAYLOAD_SIZE) {
        return null
      }
      val responseBytes = ByteArray(responseLength)
      input.readFully(responseBytes)
      return responseBytes
    } catch (_: Exception) {
      return null
    } finally {
      try {
        socket?.close()
      } catch (_: IOException) {
        // Ignore close error
      }
    }
  }

  /**
   * Checks the TC (TrunCation) bit (bit 1 of byte 2 in the 12-byte DNS header, RFC 1035 Section 4.1.1).
   */
  private fun isTruncatedDnsResponse(dnsPayload: ByteArray): Boolean {
    if (dnsPayload.size < 12) {
      return false
    }
    val flagsHighByte = dnsPayload[2].toInt() and 0xFF
    return (flagsHighByte and 0x02) != 0
  }

  companion object {
    private const val CORE_POOL_SIZE = 4
    private const val MAX_POOL_SIZE = 32
    private const val KEEP_ALIVE_SECONDS = 60L
    private const val MAX_QUEUED_QUERIES = 128
    private const val MAX_PACKET_SIZE = 32767
    private const val MAX_DNS_PAYLOAD_SIZE = 4096
    private const val UPSTREAM_TIMEOUT_MS = 3500
  }
}
