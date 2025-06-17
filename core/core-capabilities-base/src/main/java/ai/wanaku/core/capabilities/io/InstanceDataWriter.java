package ai.wanaku.core.capabilities.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.jboss.logging.Logger;

public class InstanceDataWriter implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(InstanceDataWriter.class);

    private final FileChannel fileChannel;

    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(FileHeader.BYTES + ServiceEntry.BYTES);

    /**
     * Constructor
     * @param reportFile the rate report file name
     * @param fileHeader the file header
     * @throws IOException in case of I/O errors
     */
    InstanceDataWriter(final File reportFile, final FileHeader fileHeader) throws IOException {
        fileChannel = new FileOutputStream(reportFile).getChannel();

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
                LOG.tracef("There is not enough space on the buffer for a rate entry: %s", remaining);
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
