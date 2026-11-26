package com.fsck.k9.mailstore;


import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.fsck.k9.mail.internet.SizeAware;


/**
 * An attachment whose contents are held in memory, like the small images of a signature.
 */
public class MemoryAttachmentBody extends BinaryAttachmentBody implements SizeAware {
    private final byte[] data;

    public MemoryAttachmentBody(byte[] data) {
        this.data = data;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(data);
    }

    @Override
    public long getSize() {
        return data.length;
    }
}
