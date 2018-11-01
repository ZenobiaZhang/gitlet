package gitlet;

import java.io.Serializable;

import static gitlet.Utils.sha1;

/**
 * Represents a single file. Interacts with the Main class when adding to
 * the staging area. Interacts with the Commit class when committing or
 * removing. Remembers the file name and file contents.
 *
 * @author Brian Xiao, Max Tang, Melody Yan, Yuying (Anna) Zhang
 */
public class Blob implements Serializable {

    // ========== Fields ==========

    /**
     * My SHA-1 ID (generated from my file name and file contents).
     */
    private final String myID;

    /**
     * The name of this file.
     */
    private String fileName;

    /**
     * The content of this file.
     */
    private byte[] content;

    // Constructor - Anna
    public Blob(String name, byte[] text) {
        this.fileName = name;
        this.content = text;
        myID = sha1(this.fileName, this.content);
        // MAX
        Main.serialize(this, "Blob/" + myID);

    }

    // ========== Methods ==========

    /**
     * Returns the ID of the file.
     */
    // Anna
    String getFileID() {
        return myID;
    }

    /**
     * Returns the name of the file.
     */
    // Anna
    String getFileName() {
        return fileName;
    }

    /**
     * Returns the content of the file.
     */
    // Anna
    byte[] getContent() {
        return content;
    }
}

