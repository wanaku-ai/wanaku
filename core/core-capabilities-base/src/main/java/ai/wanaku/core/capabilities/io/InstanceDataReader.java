package ai.wanaku.core.capabilities.io;

import ai.wanaku.api.types.providers.ServiceType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Reads service instance data from binary data files.
 * <p>
 * This class provides low-level access to read service instance data files created by
 * {@link InstanceDataWriter}. It uses NIO channels and direct byte buffers for efficient
 * I/O operations. The reader automatically parses the {@link FileHeader} upon construction
 * and validates the file format.
 * <p>
 * Usage example:
 * <pre>{@code
 * try (InstanceDataReader reader = new InstanceDataReader(dataFile)) {
 *     FileHeader header = reader.getHeader();
 *     ServiceEntry entry = reader.readEntry();
 *     // Process entry...
 * }
 * }</pre>
 *
 * @see InstanceDataWriter
 * @see FileHeader
 * @see ServiceEntry
 */
public class InstanceDataReader implements AutoCloseable {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(InstanceDataReader.class);

    private final FileChannel fileChannel;
    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(FileHeader.BYTES + ServiceEntry.BYTES);

    private final FileHeader fileHeader;

    static {
        assert (((FileHeader.BYTES + ServiceEntry.BYTES) % 20) == 0)
                : "File header and the rate entries must be aligned on a 20 bytes boundary";
    }

    /**
     * Constructor
     * @param file the file to read from
     * @throws IOException in case of I/O errors
     */
    InstanceDataReader(final File file) throws IOException {
        fileChannel = new FileInputStream(file).getChannel();

        fileHeader = readHeader();
    }

    /**
     * Gets the file header
     * @return the file header
     */
    public FileHeader getHeader() {
        return fileHeader;
    }

    private FileHeader readHeader() throws IOException {
        byteBuffer.clear();
        int bytesRead = fileChannel.read(byteBuffer);
        if (bytesRead <= 0) {
            throw new IllegalArgumentException("The file does not contain a valid header");
        }

        LOG.tracef("Read %s bytes from the file channel", bytesRead);
        byteBuffer.flip();

        byte[] name = new byte[FileHeader.FORMAT_NAME_SIZE];
        byteBuffer.get(name, 0, FileHeader.FORMAT_NAME_SIZE);
        LOG.tracef("File format name: '%s'", new String(name));

        int fileVersion = byteBuffer.getInt();
        LOG.tracef("File version: '%s'", fileVersion);

        ServiceType serviceType = ServiceType.fromIntValue(byteBuffer.getInt());
        LOG.tracef("Role: '%s'", serviceType.intValue());

        return new FileHeader(new String(name), serviceType, fileVersion);
    }

    /**
     * Read an entry from the file
     * @return An entry from the file or null on end-of-file
     * @throws IOException if unable to read the entry
     */
    public ServiceEntry readEntry() throws IOException {
        logBufferInfo();

        if (byteBuffer.hasRemaining()) {
            byte[] idBytes = new byte[ServiceEntry.ID_LENGTH];
            byteBuffer.get(idBytes, 0, ServiceEntry.ID_LENGTH);

            return new ServiceEntry(new String(idBytes));
        }

        byteBuffer.compact();
        int read = fileChannel.read(byteBuffer);
        if (read <= 0) {
            return null;
        }
        byteBuffer.flip();

        byte[] idBytes = new byte[ServiceEntry.ID_LENGTH];
        byteBuffer.get(idBytes, 0, ServiceEntry.ID_LENGTH);

        return new ServiceEntry(new String(idBytes));
    }

    private void logBufferInfo() {
        LOG.tracef("Remaining: %s", byteBuffer.remaining());
        LOG.tracef("Position: %s", byteBuffer.position());
        LOG.tracef("Has Remaining: %s", byteBuffer.hasRemaining());
    }

    /**
     * Close the reader and release resources
     */
    @Override
    public void close() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            LOG.errorf(e, "Error closing the reader: %s", e.getMessage());
        }
    }
}
