package no.difi.asic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

abstract class AsicAbstractManifest {

    protected MessageDigestAlgorithm messageDigestAlgorithm;
    protected MessageDigest messageDigest;

    public AsicAbstractManifest(MessageDigestAlgorithm messageDigestAlgorithm) {
        this.messageDigestAlgorithm = messageDigestAlgorithm;

        // Create message digest
        try {
            messageDigest = MessageDigest.getInstance(messageDigestAlgorithm.getAlgorithm());
            messageDigest.reset();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(String.format("Algorithm %s not supported", messageDigestAlgorithm.getAlgorithm()));
        }
    }

    /**
     * @inheritDoc
     */
    public MessageDigest getMessageDigest() {
        messageDigest.reset();
        return messageDigest;
    }

    /**
     * @inheritDoc
     */
    public abstract void add(String filename, String mimeType);
}
