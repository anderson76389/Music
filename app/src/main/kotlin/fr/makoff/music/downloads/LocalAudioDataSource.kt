package fr.makoff.music.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

/** Local documents must never be copied back into the streaming cache. */
class LocalAudioDataSource(private val context: Context, private val network: DataSource.Factory) : DataSource {
    private var delegate: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()
    override fun addTransferListener(listener: TransferListener) { listeners += listener; delegate?.addTransferListener(listener) }
    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme in setOf("content", "file")) DefaultDataSource.Factory(context).createDataSource() else network.createDataSource()
        delegate = source
        listeners.forEach(source::addTransferListener)
        return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(delegate).read(buffer, offset, length)
    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
    override fun close() { try { delegate?.close() } finally { delegate = null } }
}
