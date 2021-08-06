package com.codingbuffalo.aerialdream.services

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.math.min

class SmbDataSource: BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var userName: String = ""
    private var password: String = ""
    private var hostName: String = ""
    private var shareName: String = ""
    private val domainName: String = "WORKGROUP"
    private var path: String = ""

    private var smbClient: SMBClient? = null
    private var inputStream: InputStream? = null

    private var bytesRead: Long = 0
    private var bytesToRead: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        parseCredentials(dataSpec)
        bytesRead = dataSpec.position

        transferInitializing(dataSpec)

        val remoteFile: File = openNetworkFile()
        inputStream = remoteFile.inputStream

        val skipped = inputStream?.skip(bytesRead) ?: 0
        if (skipped < dataSpec.position) {
            throw EOFException()
        }

        bytesToRead = remoteFile.fileInformation.standardInformation.allocationSize
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return readInternal(buffer, offset, readLength)
    }

    override fun getUri() = dataSpec?.uri

    override fun close() {
        try {
            inputStream?.close()
            smbClient?.close()
        } catch (e: IOException) {
            throw IOException(e)
        } finally {
            transferEnded()
            inputStream = null
            smbClient = null
        }
    }

    private fun parseCredentials(dataSpec: DataSpec) {
        val uri = dataSpec.uri
        val userInfo = uri.userInfo?.split(":")!!
        userName = userInfo[0]
        password = userInfo[1]
        hostName = uri.host!!
        val pathSegments = uri.pathSegments.toMutableList()
        shareName = pathSegments.removeFirst()
        path = pathSegments.joinToString("/")
    }

    private fun openNetworkFile(): File {
        smbClient = SMBClient()
        val connection = smbClient?.connect(hostName)
        val authContext = AuthenticationContext(userName, password.toCharArray(), domainName)
        val session = connection?.authenticate(authContext)
        val share = session?.connectShare(shareName) as DiskShare

        val shareAccess = hashSetOf<SMB2ShareAccess>()
        shareAccess.add(SMB2ShareAccess.ALL.iterator().next())

        return share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ),
            null, shareAccess, SMB2CreateDisposition.FILE_OPEN, null)
    }

    @Suppress("NAME_SHADOWING")
    @Throws(IOException::class)
    private fun readInternal(buffer: ByteArray, offset: Int, readLength: Int): Int {
        var readLength = readLength

        if (readLength == 0) {
            return 0
        }

        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining: Long = bytesToRead - bytesRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            readLength = min(readLength.toLong(), bytesRemaining).toInt()
        }

        val read = inputStream!!.read(buffer, offset, readLength)
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET.toLong()) {
                throw EOFException()
            }
            return C.RESULT_END_OF_INPUT
        }

        bytesRead += read.toLong()
        bytesTransferred(read)
        return read
    }

    companion object {
        private const val TAG = "SmbDataSource"
    }
}