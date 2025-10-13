package ai.wanaku.core.capabilities.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.jboss.logging.Logger;

/**
 * Writes service instance data to binary data files.
 * <p>
 * This class provides low-level access to write service instance data files that can be
 * read by {@link InstanceDataReader}. It uses NIO channels and direct byte buffers for
 * efficient I/O operations. The writer automatically manages buffering and flushing of
 * data to ensure proper persistence.
 * <p>
 * The file format consists of a {@link FileHeader} followed by one or more {@link ServiceEntry}
 * records. The header is written automatically during construction, and entries are buffered
 * and written when the buffer fills or when the writer is closed.
 * <p>
 * Usage example:
 * <pre>{@code
 * try (InstanceDataWriter writer = new InstanceDataWriter(dataFile, fileHeader)) {
 *     writer.write(serviceEntry);
 *     // Additional entries can be written if needed
 * } // Automatically flushed and closed
 * }</pre>
 *
 * @see InstanceDataReader
 * @see FileHeader
 * @see ServiceEntry
 */
public class InstanceDataWriter implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(InstanceDataWriter.class);

    private final FileChannel fileChannel;

    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(FileHeader.BYTES + ServiceEntry.BYTES);

    /**
     * Constructor
     * @param file the file to write to
     * @param fileHeader the file header
     * @throws IOException in case of I/O errors
     */
    InstanceDataWriter(final File file, final FileHeader fileHeader) throws IOException {
        fileChannel = new FileOutputStream(file).getChannel();

        writeHeader(fileHeader);
    }

    private void write() throws IOException {
        byteBuffer.flip();

        while (byteBuffer.hasRemaining()) {
            fileChannel.write(byteBuffer);
        }

        byteBuffer.flip();
        byteBuffer.clear();
    }

    private void writeHeader(final FileHeader header) throws IOException {
        byteBuffer.clear();
        byteBuffer.put(header.getFormatName().getBytes());
        byteBuffer.putInt(header.getFileVersion());
        byteBuffer.putInt(header.getServiceType().intValue());

        write();
    }

    /**
     * Writes an entry to the file
     * @param entry the service entry
     * @throws IOException for multiple types of I/O errors
     */
    public void write(ServiceEntry entry) throws IOException {
        checkBufferCapacity();

        byteBuffer.put(entry.getId().getBytes());
    }

    private void checkBufferCapacity() throws IOException {
        final int remaining = byteBuffer.remaining();

        if (remaining < ServiceEntry.BYTES) {
            if (LOG.isTraceEnabled()) {
                LOG.tracef("There is not enough space on the buffer for a new entry: %s", remaining);
            }

            write();
        }
    }

    /**
     * Flushes the data to disk
     * @throws IOException in case of I/O errors
     */
    public void flush() throws IOException {
        write();
        fileChannel.force(true);
    }

    @Override
    public void close() {
        try {
            flush();
            fileChannel.close();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
