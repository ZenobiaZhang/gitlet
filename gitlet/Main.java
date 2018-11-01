package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;

import static gitlet.Utils.*;

/**
 * Driver class for Gitlet, the tiny stupid version-control system.
 *
 * @author Brian Xiao, Max Tang, Melody Yan, Yuying (Anna) Zhang
 */
public class Main {

    // ========== Fields ==========

    /**
     * The current branch that HEAD is pointing to.
     */
    private String currBranch = "master";

    /**
     * A set of file names that .gitlet should not be tracking.
     */
    private Set<String> untrack = new TreeSet<>();

    /**
     * A mapping of branch names to the SHA-1 ID of the most recent commit
     * in that branch. Includes a pointer to the head and a pointer to the
     * initial commit.
     */
    private TreeMap<String, String> allBranches = new TreeMap<>();

    /**
     * The name of the pointer referring to the head of this tree.
     * Used as a Key in allBranches.
     */
    private static final String HEAD = "Head";

    /**
     * The name of the pointer referring to the initial commit of this tree.
     * Used as a Key in allBranches.
     */
    private static final String INITIAL = "Initial";

    /**
     * The name of the file representing the serialized staging area.
     */
    private static final String STAGING_AREA = "stagingArea";

    /**
     * The name of the file representing the serialized allBranches map.
     */
    private static final String ALL_BRANCHES = "allBranches";

    /**
     * The name of the file representing the serialized untrack set.
     */
    private static final String UNTRACKED = "untrack";

    /**
     * The name of the file representing the serialized current branch.
     */
    private static final String CURR_BRANCH = "currBranch";

    /**
     * A mapping of the file names to the SHA-1’s of their blobs for the
     * staging area.
     */
    private TreeMap<String, String> stagingArea = new TreeMap<>();

    /**
     * An instance of allBlah for Blobs.
     */
    private AllBlah<Blob> allBlobs = new AllBlah<>("Blob/");

    /**
     * An instance of allBlah for Commits.
     */
    private AllBlah<Commit> allCommits = new AllBlah<>("Commit/");

    /**
     * A class containing a get method that deserializes the object with the given input ID.
     */
    private class AllBlah<Out> {

        private String myType;

        public AllBlah(String directory) {
            this.myType = directory;
        }

        public Out get(String iD) {
            return deserializeBlah(myType + iD);
        }

        public List<Out> values() {
            List<Out> vals = new ArrayList<>();
            File dir = new File(".gitlet/" + myType);
            for (File f : dir.listFiles()) {
                vals.add(get(f.getName()));
            }
            return vals;
        }

        public Set<String> keySet() {
            Set<String> names = new TreeSet<>();
            File dir = new File(".gitlet/" + myType);
            for (File f : dir.listFiles()) {
                names.add(f.getName());
            }
            return names;
        }
    }

    // ========== Methods ==========

    /**
     * Returns the SHA-1 ID of the Commit that head is pointing to.
     */
    public String getHeadID() {
        return allBranches.get(HEAD);
    }

    /**
     * Returns the Commit object that head is pointing to.
     */
    public Commit getHeadCommit() {
        return allCommits.get(getHeadID());
    }

    /**
     * Returns the SHA-1 ID of the most recent Commit in branchName.
     */
    // Anna
    public String getBranch(String branchName) {
        return allBranches.get(branchName);
    }

    /**
     * Returns a set of file names that .gitlet should not track.
     */
    // Melody
    public Set<String> getUntracked() {
        untrack = deserializeUntrack();
        return untrack;
    }

    // ========== Commands ==========

    /**
     * Check if a .gitlet directory already exists in our current directory.
     * Aborts if there is an existing gitlet version-control system in the
     * current directory, and outputs an error message.
     * Otherwise, create a new .gitlet directory.
     */
    // Anna
    public void init() {
        File testF = new File(".gitlet");
        File commitD = new File(".gitlet/Commit");
        File blobD = new File(".gitlet/Blob");
        if (!testF.exists()) {
            testF.mkdirs();
            commitD.mkdirs();
            blobD.mkdirs();
        } else {
            System.out.println("A gitlet version-control system already exists in the "
                    + "current directory.");
            return;
        }
        Commit initialCommit = new Commit("", "initial commit", new TreeMap<>());
        String initialID = initialCommit.getCommitID();
        allBranches.put(INITIAL, initialID);
        allBranches.put(HEAD, initialID);
        allBranches.put("master", initialID);
        serialize(allBranches, ALL_BRANCHES);
        serialize(currBranch, CURR_BRANCH);
        serialize(stagingArea, STAGING_AREA);
        serialize(untrack, UNTRACKED);
    }

    /**
     * Take in the name of the file and its SHA-1 ID. If the file is untracked
     * because of remove, delete its mark(move it out of the removed map) and
     * put it in the blob. Then iterate again and stage it. If the file is new or
     * modified, add it to the staging area. If the file is identical to previous
     * commit(has the same SHA-1 ID), don’t stage it.
     */
    // Anna
    public void add(String fileName) {
        stagingArea = deserializeStagingArea();
        untrack = getUntracked();
        allBranches = deserializeAllBranches();
        File f = new File(fileName);
        if (!f.exists()) {
            System.out.println("File does not exist.");
        } else {
            if (stagingArea.containsKey(fileName)) {
                return; //already staged
            }
            if (untrack.contains(fileName)) {
                untrack.remove(fileName); // remove mark
                serialize(untrack, UNTRACKED);
            }
            Path path = f.toPath();
            try {
                byte[] cont = Files.readAllBytes(path);
                Blob curr = new Blob(fileName, cont);
                // if file is tracked and has not been modified, then do not add the file
                if (getHeadCommit().getTracked().get(fileName) != null
                        && getHeadCommit().getTracked().get(fileName).equals(curr.getFileID())) {
                    return;
                }
                stagingArea.put(fileName, curr.getFileID());
                serialize(stagingArea, STAGING_AREA);
            } catch (IOException excp) {
                throw new IllegalArgumentException(excp.getMessage());
            }
        }
    }

    /**
     * Takes in a message and saves it as the log for that commit.
     * Saves a snapshot of files in the current commit and staging area, and creates
     * a new Commit, whose parent is the previous branch’s HEAD commit.
     * Only updates files it is tracking that have been staged at the time
     * of commit. The commit will save the updated version.
     * Clears the staging area after committing.
     */
    // Melody
    public void commit(String message) {
        stagingArea = deserializeStagingArea();
        allBranches = deserializeAllBranches();
        untrack = deserializeUntrack();
        currBranch = deserializeCurrBranch();

        // Failure cases
        if (stagingArea.isEmpty() && getUntracked().isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }
        if (message == null || message.equals("")) {
            System.out.println("Please enter a commit message.");
            return;
        }

        String parentID = getHeadID();
        TreeMap<String, String> parentTracked = getHeadCommit().getTracked();
        TreeMap<String, String> newStagingArea = new TreeMap<>();
        newStagingArea.putAll(parentTracked);
        for (String s : getUntracked()) {
            newStagingArea.remove(s);
        }
        newStagingArea.putAll(stagingArea);
        Commit currCommit = new Commit(parentID, message, newStagingArea);

        // update head: current branch's head pointer now points to this new commit
        allBranches.put(HEAD, currCommit.getCommitID());
        allBranches.put(currBranch, currCommit.getCommitID());
        serialize(allBranches, ALL_BRANCHES);
        stagingArea.clear();
        serialize(stagingArea, STAGING_AREA);
        untrack.clear();
        serialize(untrack, UNTRACKED);
    }

    /**
     * Removes Blobs corresponding to fileName from staging set. Also
     * removes fileName from the working directory, and marks it to be
     * untracked by commit.
     */
    // Melody
    public void rm(String fileName) {
        stagingArea = deserializeStagingArea();
        untrack = deserializeUntrack();
        allBranches = deserializeAllBranches();
        Commit headCommit = getHeadCommit();
        TreeMap<String, String> tracking = headCommit.getTracked();
        boolean tracked = tracking.containsKey(fileName);
        boolean staged = stagingArea.containsKey(fileName);
        if (tracked) {
            restrictedDelete(fileName);
            if (staged) {
                stagingArea.remove(fileName);
                serialize(stagingArea, STAGING_AREA);
            }
            untrack.add(fileName);
            serialize(untrack, UNTRACKED);
        } else if (staged) {
            stagingArea.remove(fileName);
            serialize(stagingArea, STAGING_AREA);
        } else {
            System.out.println("No reason to remove the file.");
        }
    }

    /**
     * Take in a string called currBranch where we iterate on. Use a
     * for loop to print each commit’s SHA-1 ID, timestamp and
     * commit message until there’s no parent ID on this branch.
     */
    // Anna
    public void log() {
        allBranches = deserializeAllBranches();
        Commit curr = getHeadCommit();
        while (curr != null) {
            System.out.println("===");
            System.out.println("Commit " + curr.getCommitID());
            System.out.println(curr.getCommitDate());
            System.out.println(curr.getMessage());
            System.out.println();
            curr = allCommits.get(curr.getParentID());
        }
    }

    /**
     * Iterate through allBranches and print out each log message.
     */
    // Anna
    public void globalLog() {
        Set keyBox = allCommits.keySet();
        for (Iterator i = keyBox.iterator(); i.hasNext(); ) {
            String currID = (String) i.next();
            Commit curr = allCommits.get(currID);
            System.out.println("===");
            System.out.println("Commit " + curr.getCommitID());
            System.out.println(curr.getCommitDate());
            System.out.println(curr.getMessage());
            System.out.println();
        }
    }

    /**
     * Prints out the ids of all commits that have the given commit
     * message, one per line. If there are multiple such commits, it
     * prints the ids out on separate lines. Prints every matching commit,
     * even those that are not on the current branch. Order does not
     * matter.
     */
    // Melody
    public void find(String commitMsg) {
        boolean found = false;
        for (Commit c : allCommits.values()) {
            if (c.getMessage().equals(commitMsg)) {
                found = true;
                System.out.println(c.getCommitID());
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * Take in the location of pointer to current Branch, and print out the
     * branch name with an asterisk in front of it. Print out fileName with the staged,
     * removed(untracked set), modified but unstaged, deleted and modified mark
     * through iteration.
     * For removed, track whether it’s in the untracked set.
     */
    // Anna
    public void status() {
        allBranches = deserializeAllBranches();
        untrack = deserializeUntrack();
        currBranch = deserializeCurrBranch();
        stagingArea = deserializeStagingArea();
        String tempHEAD = allBranches.remove(HEAD);
        String tempINITIAL = allBranches.remove(INITIAL);

        System.out.println("=== Branches ===");
        List<String> nameList = new ArrayList<String>(allBranches.keySet());
        Collections.sort(nameList);
        for (Iterator i = nameList.iterator(); i.hasNext(); ) {
            String name = (String) i.next();
            if (name.equals(currBranch)) {
                name = "*" + name;
            }
            System.out.println(name);
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        List<String> fileNameList = new ArrayList<String>(stagingArea.keySet());
        Collections.sort(fileNameList);
        for (Iterator i = fileNameList.iterator(); i.hasNext(); ) {
            String fileName = (String) i.next();
            System.out.println(fileName);
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        List<String> untrackList = new ArrayList<String>(untrack);
        Collections.sort(untrackList);
        for (Iterator i = untrackList.iterator(); i.hasNext(); ) {
            String untrackk = (String) i.next();
            System.out.println(untrackk);
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files ===");
        System.out.println();

        allBranches.put(INITIAL, tempINITIAL);
        allBranches.put(HEAD, tempHEAD);
        serialize(allBranches, ALL_BRANCHES);
    }

    /**
     * Takes the version of the file as it exists in the head commit,
     * the front of the current branch, and puts it in the working directory,
     * overwriting the version of the file that’s already there if there is one.
     * The new version of the file is not staged.
     */
    // Max
    public void checkout(String dash, String fileName) {
        allBranches = deserializeAllBranches();
        Commit cmt = getHeadCommit();
        writeFileOfCrtCommit(cmt, fileName);
    }

    /**
     * Takes the version of the file as it exists in the commit with the given id
     * and puts it in the working directory, overwriting the version of the file
     * that’s already there if there is one. The new version of the file is not staged.
     */
    // Max
    public void checkout(String cmtID, String dash, String fileName) {
        String shortUID; // allows for abbreviated commit ID comparison
        for (String commitID : allCommits.keySet()) {
            shortUID = commitID.substring(0, cmtID.length());
            if (shortUID.equals(cmtID)) {
                Commit cmt = allCommits.get(commitID);
                writeFileOfCrtCommit(cmt, fileName);
                return;
            }
        }
        System.out.println("No commit with that id exists.");
    }

    /**
     * Write file of given name in current commit to the directory.
     */
    //Max
    //TODO wtf is wrong here
    private void writeFileOfCrtCommit(Commit cmt, String fileName) {
        if (!cmt.getTracked().containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String blobID = cmt.getTracked().get(fileName);
        Blob headVersion = allBlobs.get(blobID);
        File f = new File(fileName);

        f.delete();

        writeContents(f, headVersion.getContent());
    }


    /**
     * Takes all files in the commit at the head of the given branch.
     * Puts them in the working directory.
     * Overwriting the versions of the files that are already there if
     * they exist. Also, at the end of this command, the given branch will now be
     * considered the current branch (HEAD). Any files that are tracked in the
     * current branch but are not present in the checked-out branch are deleted.
     * The staging area is cleared, unless the checked-out branch is the current
     * branch.
     */
    // Max
    public void checkout(String branchName) {
        allBranches = deserializeAllBranches();
        currBranch = deserializeCurrBranch();
        untrack = deserializeUntrack();
        stagingArea = deserializeStagingArea();
        if (!allBranches.containsKey(branchName)) {
            System.out.println("No such branch exists.");
            return;
        } else if (currBranch.equals(branchName)) {
            System.out.println("No need to checkout the current branch.");
            return;
        }
        String commitID = allBranches.get(branchName);
        checkoutHelper(commitID);
        currBranch = branchName;
        serialize(currBranch, CURR_BRANCH);
        allBranches.put(HEAD, commitID);
        serialize(allBranches, ALL_BRANCHES);
        stagingArea.clear();
        serialize(stagingArea, STAGING_AREA);
    }

    public void checkoutHelper(String commitID) {
        Commit cmt = allCommits.get(commitID);
        String currWorkingDirectory = System.getProperty("user.dir");
        Set<String> trackedFiles = cmt.getTracked().keySet();
        for (String fileName : plainFilenamesIn(currWorkingDirectory)) {
            if (!getHeadCommit().getTracked().containsKey(fileName)
                    && !stagingArea.containsKey(fileName)) {
                // this should not print when everything is tracked, but it is printing
                System.out.println("There is an untracked file in the way; "
                        + "delete it or add it first.");
                return;
            }
            if (!trackedFiles.contains(fileName)) {
                restrictedDelete(fileName);
            }
        }
        for (String f : trackedFiles) {
            writeFileOfCrtCommit(cmt, f);
        }
    }

    /**
     * Creates a new branch called branchName and points it at the current head node.
     */
    // Melody
    public void branch(String branchName) {
        allBranches = deserializeAllBranches();
        if (allBranches.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
        } else {
            allBranches.put(branchName, getHeadID());
            serialize(allBranches, ALL_BRANCHES);
        }
    }

    /**
     * Deletes the branch pointer called branchName from allBranches.
     * Does not delete all commits created under that branch.
     */
    // Melody
    public void rmBranch(String branchName) {
        currBranch = deserializeCurrBranch();
        allBranches = deserializeAllBranches();
        if (branchName.equals(currBranch)) {
            System.out.println("Cannot remove the current branch.");
        } else if (allBranches.containsKey(branchName)) {
            allBranches.remove(branchName);
            serialize(allBranches, ALL_BRANCHES);
        } else {
            System.out.println("A branch with that name does not exist.");
        }
    }

    /**
     * Take in the prevID that the user want to return to. Call checkout with
     * that ID to return to previous stage. Also change the pointer onto the new
     * location. Delete the files tracked map that are not in commit.
     */
    // Anna
    public void reset(String prevID) {
        allBranches = deserializeAllBranches();
        currBranch = deserializeCurrBranch();
        stagingArea = deserializeStagingArea();
        if (allCommits.get(prevID) == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        checkoutHelper(prevID);
        allBranches.put(HEAD, prevID);
        allBranches.put(currBranch, prevID);
        serialize(allBranches, ALL_BRANCHES);
        stagingArea.clear();
        serialize(stagingArea, STAGING_AREA);
    }

    /**
     * Traverses up the parents of the head until finding a commit that is on
     * both the same branch as head and the input branch name. Once reaching this
     * split point check the contents of all files in the split point, input branch,
     * and head. Based on the differences between the files, create a new commit
     * with corresponding changes and make its parent the head and input a
     * successful merge message, or a merge conflict message.
     */
    // Brian
    public void merge(String branchName) {
        currBranch = deserializeCurrBranch();
        if (currBranch.equals(branchName)) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }
        untrack = deserializeUntrack();
        stagingArea = deserializeStagingArea();
        if (!(stagingArea.isEmpty() && untrack.isEmpty())) {
            System.out.println("You have uncommited changes.");
            return;
        }
        allBranches = deserializeAllBranches();
        if (!allBranches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        String currWorkingDirectory = System.getProperty("user.dir");
        for (String fileName : plainFilenamesIn(currWorkingDirectory)) {
            if (!getHeadCommit().getTracked().containsKey(fileName)
                    && !stagingArea.containsKey(fileName)) {
                System.out.println("There is an untracked file in the way; "
                        + "delete it or add it first.");
                return;
            }
        }
        String branchID = allBranches.get(branchName);
        String currentID = allBranches.get(currBranch);
        Commit branchEnd = allCommits.get(branchID);
        Commit currentEnd = allCommits.get(currentID);
        Commit branchTemp = allCommits.get(branchID);
        Commit currentTemp = allCommits.get(currentID);
        Commit splitPoint;
        Set<String> branchTraversed = new TreeSet<>();
        Set<String> headTraversed = new TreeSet<>();
        branchTraversed.add(branchID);
        headTraversed.add(currentID);
        while (!(branchTraversed.contains(currentID) || headTraversed.contains(branchID))) {
            branchID = branchTemp.getParentID();
            currentID = currentTemp.getParentID();
            branchTraversed.add(branchID);
            headTraversed.add(currentID);
            branchTemp = allCommits.get(branchID);
            currentTemp = allCommits.get(currentID);
        }
        if (branchTraversed.contains(currentID)) {
            splitPoint = currentTemp;
        } else {
            splitPoint = branchTemp;
        }
        mergeHelper(branchName, splitPoint, currentEnd, branchEnd);
    }

    public void mergeHelper(String branchName, Commit splitPoint,
                            Commit currentEnd, Commit branchEnd) {
        if (splitPoint.getCommitID().equals(currentEnd.getCommitID())) {
            System.out.println("Current branch fast-forwarded.");
            allBranches.put(currBranch, branchEnd.getCommitID());
            serialize(allBranches, ALL_BRANCHES);
            return;
        } else if (splitPoint.getCommitID().equals(branchEnd.getCommitID())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        } else {
            TreeMap<String, String> splitTracked = splitPoint.getTracked();
            TreeMap<String, String> branchTracked = branchEnd.getTracked();
            TreeMap<String, String> currentTracked = currentEnd.getTracked();
            Set<String> fileNames = new TreeSet<>();
            fileNames.addAll(splitTracked.keySet());
            fileNames.addAll(branchTracked.keySet());
            fileNames.addAll(currentTracked.keySet());
            boolean mergeError = false;
            for (String f : fileNames) {
                String branchFile = branchTracked.get(f);
                String currentFile = currentTracked.get(f);
                String splitFile = splitTracked.get(f);
                Boolean branchCurrent;
                Boolean currentSplit;
                Boolean branchSplit;
                if (branchFile == null) {
                    branchCurrent = branchFile == currentFile;
                    branchSplit = branchFile == splitFile;
                } else {
                    branchCurrent = branchFile.equals(currentFile);
                    branchSplit = branchFile.equals(splitFile);
                }
                if (currentFile == null) {
                    currentSplit = currentFile == splitFile;
                } else {
                    currentSplit = currentFile.equals(splitFile);
                }
                if (!branchCurrent) {
                    if (currentSplit) {
                        if (branchTracked.containsKey(f)) {
                            checkout(allBranches.get(branchName), "", f);
                            add(f);
                        } else {
                            rm(f);
                        }
                    } else if (!branchSplit) {
                        mergeError = true;
                        String branchVersion = "";
                        String currentVersion = "";
                        File conflictFile = new File(f);
                        if (branchTracked.containsKey(f)) {
                            checkout(allBranches.get(branchName), "", f);
                            branchVersion = new String(readContents(conflictFile));
                        }
                        if (currentTracked.containsKey(f)) {
                            checkout(allBranches.get(currBranch), "", f);
                            currentVersion = new String(readContents(conflictFile));
                        }
                        byte [] conflictContents = ("<<<<<<< HEAD" + System.lineSeparator()
                                + currentVersion + "======="
                                + System.lineSeparator() + branchVersion + ">>>>>>>"
                                + System.lineSeparator()).getBytes();
                        conflictFile.delete();
                        try {
                            Files.write(conflictFile.toPath(), conflictContents);
                        } catch (IOException e) {
                            throw new IllegalArgumentException(e.getMessage());
                        }
                    }
                }
            }
            if (mergeError) {
                System.out.println("Encountered a merge conflict.");
            } else {
                commit("Merged " + currBranch + " with " + branchName + ".");
            }
        }
    }

    /* Usage: java gitlet.Main ARGS, where ARGS contains
       <COMMAND> <OPERAND> .... */
    //Max
    public static void main(String... args) {
        Main main = new Main();
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        } else if (args.length == 1) {
            if (args[0].equals("init")) {
                main.init();
            } else if (args[0].equals("log")) {
                main.log();
            } else if (args[0].equals("global-log")) {
                main.globalLog();
            } else if (args[0].equals("status")) {
                main.status();
            } else {
                System.out.println("No command with that name exists.");
                return;
            }
        } else if (args.length == 2) {
            if (args[0].equals("add")) {
                main.add(args[1]);
            } else if (args[0].equals("commit")) {
                main.commit(args[1]);
            } else if (args[0].equals("rm")) {
                main.rm(args[1]);
            } else if (args[0].equals("branch")) {
                main.branch(args[1]);
            } else if (args[0].equals("rm-branch")) {
                main.rmBranch(args[1]);
            } else if (args[0].equals("reset")) {
                main.reset(args[1]);
            } else if (args[0].equals("merge")) {
                main.merge(args[1]);
            } else if (args[0].equals("find")) {
                main.find(args[1]);
            } else if (args[0].equals("checkout")) {
                main.checkout(args[1]);
            } else {
                System.out.println("Incorrect operands.");
                return;
            }
        } else if (args.length == 3) {
            if (args[0].equals("checkout") && args[1].equals("--")) {
                main.checkout("--", args[2]);
            } else {
                System.out.println("Incorrect operands.");
                return;
            }
        } else if (args.length == 4) {
            if (args[0].equals("checkout") && args[2].equals("--")) {
                main.checkout(args[1], "--", args[3]);
            } else {
                System.out.println("Incorrect operands");
                return;
            }
        } else {
            System.out.println("Incorrect operands");
            return;
        }

    }

    // ========== Serialization methods ==========

    /**
     * Serialize OBJ and assign the file name to FILEID.
     * Source: https://cs61bl.org/su18/projects/gitlet/
     */
    protected static void serialize(Object obj, String fileID) {
        File outFile = new File(".gitlet/" + fileID);
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(outFile));
            out.writeObject(obj);
            out.close();
        } catch (IOException excp) {
            // Ignore
        }
    }

    /**
     * Deserialize and return the staging area.
     * Source: https://cs61bl.org/su18/projects/gitlet/
     */
    protected TreeMap<String, String> deserializeStagingArea() {
        TreeMap<String, String> treeMap;
        File inFile = new File(".gitlet/" + STAGING_AREA);
        try {
            ObjectInputStream inp = new ObjectInputStream(new FileInputStream(inFile));
            treeMap = (TreeMap<String, String>) inp.readObject();
            inp.close();
        } catch (IOException | ClassNotFoundException excp) {
            treeMap = null;
        }
        return treeMap;
    }

    /**
     * Deserialize and return the set of untracked files.
     * Source: https://cs61bl.org/su18/projects/gitlet/
     */
    protected Set<String> deserializeUntrack() {
        Set<String> untrackk;
        File inFile = new File(".gitlet/" + UNTRACKED);
        try {
            ObjectInputStream inp = new ObjectInputStream(new FileInputStream(inFile));
            untrackk = (Set<String>) inp.readObject();
            inp.close();
        } catch (IOException | ClassNotFoundException excp) {
            untrackk = null;
        }
        return untrackk;
    }

    /**
     * Deserialize and return the allBranches mapping.
     * Source: https://cs61bl.org/su18/projects/gitlet/
     */
    protected TreeMap<String, String> deserializeAllBranches() {
        TreeMap<String, String> allBranchess;
        File inFile = new File(".gitlet/" + ALL_BRANCHES);
        try {
            ObjectInputStream inp = new ObjectInputStream(new FileInputStream(inFile));
            allBranchess = (TreeMap<String, String>) inp.readObject();
            inp.close();
        } catch (IOException | ClassNotFoundException excp) {
            allBranchess = null;
        }
        return allBranchess;
    }

    /**
     * Deserialize and return what branch HEAD is currently pointing to.
     * Source: https://cs61bl.org/su18/projects/gitlet/
     */
    protected String deserializeCurrBranch() {
        String currBranchh;
        File inFile = new File(".gitlet/" + CURR_BRANCH);
        try {
            ObjectInputStream inp = new ObjectInputStream(new FileInputStream(inFile));
            currBranchh = (String) inp.readObject();
            inp.close();
        } catch (IOException | ClassNotFoundException excp) {
            currBranchh = null;
        }
        return currBranchh;
    }


    /**
     * Uses generics to deserialize whatever (DO NOT USE OUTSIDE OF allBlah).
     * Source: https://cs61bl.org/su18/projects/gitlet/
     */
    protected <Out> Out deserializeBlah(String fileID) {
        Out deserialObject;
        File inFile = new File(".gitlet/" + fileID);
        try {
            ObjectInputStream inp = new ObjectInputStream(new FileInputStream(inFile));
            deserialObject = (Out) inp.readObject();
            inp.close();
        } catch (IOException | ClassNotFoundException excp) {
            deserialObject = null;
        }
        return deserialObject;
    }

}
