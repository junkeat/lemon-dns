package com.junkeat.dnsswitcher.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads raw IPv4 DNS query packets from the TUN interface, forwards them over protected UDP sockets
 * to the currently active upstream DNS server, and writes raw IPv4 UDP responses back to the TUN interface.
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
  private val queryExecutor = Executors.newCachedThreadPool()
  private val writeLock = Any()
  private var readerThread: Thread? = null

  /**
   * Atomically hot-swaps the upstream DNS server for all subsequent DNS queries.
   * The TUN interface and existing in-flight queries remain completely untouched.
   */
  fun setUpstream(upstream: DnsUpstream) {
    activeUpstream.set(upstream)
  }

  fun getUpstream(): DnsUpstream = activeUpstream.get()

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

    var socket: DatagramSocket? = null
    try {
      val createdSocket = DatagramSocket()
      socket = createdSocket
      if (!vpnService.protect(createdSocket)) {
        return
      }
      createdSocket.soTimeout = UPSTREAM_TIMEOUT_MS

      val targetInetAddress = InetAddress.getByName(upstream.address)
      val outboundPacket = DatagramPacket(
        query.dnsPayload,
        query.dnsPayload.size,
        targetInetAddress,
        upstream.port,
      )
      createdSocket.send(outboundPacket)

      val responseBuffer = ByteArray(MAX_DNS_PAYLOAD_SIZE)
      val inboundPacket = DatagramPacket(responseBuffer, responseBuffer.size)
      createdSocket.receive(inboundPacket)

      if (!running.get() || inboundPacket.length <= 0) {
        return
      }

      val rawIpResponse = UdpPacketCodec.buildDnsResponse(
        request = query,
        responsePayload = responseBuffer,
        responseLength = inboundPacket.length,
      )

      synchronized(writeLock) {
        if (running.get()) {
          outputStream.write(rawIpResponse)
          outputStream.flush()
        }
      }
    } catch (_: Exception) {
      // Drop timed out or failed packet without logging query content
    } finally {
      socket?.close()
    }
  }

  companion object {
    private const val MAX_PACKET_SIZE = 32767
    private const val MAX_DNS_PAYLOAD_SIZE = 4096
    private const val UPSTREAM_TIMEOUT_MS = 5000
  }
}
