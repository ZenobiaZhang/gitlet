package gitlet;

import java.io.Serializable;
import java.util.TreeMap;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import static gitlet.Utils.sha1;

/**
 * The core structure of .gitlet. Represents an individual commit as well as
 * its relationship in the tree (i.e., its parent commit). Indicates which
 * branch it is on, and remembers the other branches in the tree. Interacts
 * with the Blob class when committing. Interacts with the Main class for
 * every command called.
 *
 * @author Brian Xiao, Max Tang, Melody Yan, Yuying (Anna) Zhang
 */
public class Commit implements Serializable {

    // ========== Fields ==========

    /**
     * My SHA-1 ID (generated from the commit message and time of commit).
     */
    private final String myID;

    /**
     * The SHA-1 ID of my parent, or null if I am the initial commit.
     */
    private final String parentID;

    /**
     * My log message.
     */
    private final String message;

    /**
     * My timestamp.
     */
    private final String commitDate;

    /**
     * A mapping of file names to the SHA-1’s of their blobs for the tracked files.
     */
    private TreeMap<String, String> tracked = new TreeMap<>();

    // Constructor - Melody
    public Commit(String parentID, String message, TreeMap<String, String> myStagingArea) {
        this.parentID = parentID;
        this.message = message;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        this.commitDate = dtf.format(now);
        this.tracked.putAll(myStagingArea);
        myID = sha1(myStagingArea.toString(), this.parentID, this.message, this.commitDate);
        Main.serialize(this, "Commit/" + myID);
    }

    // ========== Methods ==========

    // Anna
    public String toString(Object o) {
        return o.toString();
    }

    /**
     * Return my SHA-1 ID. tracked.get(branchName)
     */
    // Anna
    String getCommitID() {
        return myID;
    }

    /**
     * Return the SHA-1 ID of my parent. return getHead()
     */
    // Anna
    String getParentID() {
        return this.parentID;
    }

    /**
     * Return the commit message.
     */
    // Anna
    String getMessage() {
        return this.message;
    }

    /**
     * Return the commit time.
     */
    // Max
    String getCommitDate() {
        return this.commitDate;
    }

    /**
     * Returns the Map of file names to SHA-1’s of the Blobs we are tracking.
     */
    // Max
    TreeMap<String, String> getTracked() {
        return this.tracked;
    }

}
