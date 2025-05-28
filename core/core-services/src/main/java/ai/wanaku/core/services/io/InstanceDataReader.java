package ai.wanaku.core.services.io;

import ai.wanaku.api.types.providers.ServiceType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class InstanceDataReader implements AutoCloseable {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(InstanceDataReader.class);

    private final FileChannel fileChannel;
    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(FileHeader.BYTES + ServiceEntry.BYTES);

    private final FileHeader fileHeader;

    static {
        assert (((FileHeader.BYTES + ServiceEntry.BYTES) % 20) == 0):
                "File header and the rate entries must be aligned on a 20 bytes boundary";
    }


    /**
     * Constructor
     * @param fileName the report file name
     * @throws IOException in case of I/O errors
     */
    InstanceDataReader(final File fileName) throws IOException {
        fileChannel = new FileInputStream(fileName).getChannel();

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
     * @return A rate entry from the file or null on end-of-file
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
