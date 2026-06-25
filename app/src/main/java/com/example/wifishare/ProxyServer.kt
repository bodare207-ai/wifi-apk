package com.example.wifishare

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProxyServer(private val port: Int) {

    companion object {
        private const val TAG = "ProxyServer"
        private const val BUFFER_SIZE = 64 * 1024
        private const val SOCKS5_VERSION = 0x05
        private const val ATYP_IPV4 = 0x01
        private const val ATYP_DOMAIN = 0x03
        private const val ATYP_IPV6 = 0x04
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()

    @Throws(IOException::class)
    fun start() {
        if (isRunning) return

        serverSocket = ServerSocket(port)
        isRunning = true
        Log.d(TAG, "SOCKS5 proxy listening on port $port")

        threadPool.execute {
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d(TAG, "New client connected: ${clientSocket.inetAddress.hostAddress}")
                    threadPool.execute { handleClient(clientSocket) }
                } catch (e: SocketException) {
                    if (isRunning) {
                        Log.e(TAG, "Socket error: ${e.message}")
                    }
                    break
                } catch (e: IOException) {
                    if (isRunning) {
                        Log.e(TAG, "IO error: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        threadPool.shutdown()
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow()
            }
        } catch (e: InterruptedException) {
            threadPool.shutdownNow()
            Thread.currentThread().interrupt()
        }
        Log.d(TAG, "SOCKS5 proxy stopped")
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 300000

            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            val version = input.read()
            if (version != SOCKS5_VERSION) {
                clientSocket.close()
                return
            }

            val authMethodCount = input.read()
            if (authMethodCount <= 0) {
                clientSocket.close()
                return
            }

            for (i in 0 until authMethodCount) {
                input.read()
            }

            output.write(byteArrayOf(SOCKS5_VERSION.toByte(), 0x00))
            output.flush()

            while (true) {
                val reqVersion = input.read()
                if (reqVersion == -1) break
                if (reqVersion != SOCKS5_VERSION) break

                val cmd = input.read()
                if (cmd == -1) break

                input.read()

                val atyp = input.read()
                if (atyp == -1) break

                val destAddress: String
                val destPort: Int

                when (atyp) {
                    ATYP_IPV4 -> {
                        val ipBytes = ByteArray(4)
                        input.read(ipBytes)
                        destAddress = ipBytes.joinToString(".") { it.toInt() and 0xFF toString() }
                    }

                    ATYP_DOMAIN -> {
                        val domainLen = input.read()
                        if (domainLen == -1) break
                        val domainBytes = ByteArray(domainLen)
                        input.read(domainBytes)
                        destAddress = String(domainBytes)
                    }

                    ATYP_IPV6 -> {
                        val ipBytes = ByteArray(16)
                        input.read(ipBytes)
                        destAddress = ipBytes.joinToString(":") { "%02x".format(it) }
                    }

                    else -> {
                        Log.e(TAG, "Unsupported address type: $atyp")
                        break
                    }
                }

                val portHigh = input.read()
                val portLow = input.read()
                destPort = (portHigh shl 8) or portLow

                Log.d(TAG, "CONNECT to $destAddress:$destPort")

                var remoteSocket: Socket? = null
                try {
                    remoteSocket = Socket()
                    remoteSocket.connect(InetSocketAddress(destAddress, destPort), 15000)
                    remoteSocket.soTimeout = 300000

                    val response = byteArrayOf(
                        SOCKS5_VERSION.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        ATYP_IPV4.toByte(),
                        0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00
                    )
                    output.write(response)
                    output.flush()

                    relayData(clientSocket, remoteSocket)

                } catch (e: IOException) {
                    Log.e(TAG, "Failed to connect to $destAddress:$destPort: ${e.message}")
                    val response = byteArrayOf(
                        SOCKS5_VERSION.toByte(),
                        0x04.toByte(),
                        0x00.toByte(),
                        ATYP_IPV4.toByte(),
                        0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00
                    )
                    output.write(response)
                    output.flush()
                } finally {
                    remoteSocket?.close()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error handling client: ${e.message}")
        } finally {
            try {
                clientSocket.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    private fun relayData(client: Socket, remote: Socket) {
        val clientInput = client.getInputStream()
        val clientOutput = client.getOutputStream()
        val remoteInput = remote.getInputStream()
        val remoteOutput = remote.getOutputStream()

        val buffer1 = ByteArray(BUFFER_SIZE)
        val buffer2 = ByteArray(BUFFER_SIZE)

        val thread1 = Thread {
            try {
                var bytesRead: Int
                while (client.isConnected && !client.isClosed &&
                    remote.isConnected && !remote.isClosed
                ) {
                    bytesRead = clientInput.read(buffer1)
                    if (bytesRead == -1) break
                    remoteOutput.write(buffer1, 0, bytesRead)
                    remoteOutput.flush()
                }
            } catch (e: IOException) {
                // Connection closed
            }
        }

        val thread2 = Thread {
            try {
                var bytesRead: Int
                while (client.isConnected && !client.isClosed &&
                    remote.isConnected && !remote.isClosed
                ) {
                    bytesRead = remoteInput.read(buffer2)
                    if (bytesRead == -1) break
                    clientOutput.write(buffer2, 0, bytesRead)
                    clientOutput.flush()
                }
            } catch (e: IOException) {
                // Connection closed
            }
        }

        thread1.start()
        thread2.start()

        try {
            thread1.join()
            thread2.join()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}