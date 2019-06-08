import java.util.Vector;

public class FileTable {

    private Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory

    public FileTable(Directory directory) { // constructor
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    // major public methods
    public synchronized FileTableEntry falloc(String filename, String mode) {
        Inode inode = null;
        short inumber;
        while (true) {
            if (filename.equals("/")) {
                inumber = 0;
            }
            else {
                inumber = dir.namei(filename);
            }

            if (inumber < 0) {
                if (mode.equals("r")){
                    return null;
                }
            }
            else {
                inode = new Inode(inumber);
                if (mode.equals("r")){
                    if (inode.flag != 0 && inode.flag != 1) {
                        try {
                            wait();
                        } catch (InterruptedException e) {}
                        continue;
                    }
                    inode.flag = 1;
                    break;
                }
                if (inode.flag != 0 && inode.flag != 3) {
                    if (inode.flag == 1 || inode.flag == 2) {
                        inode.flag = (short)(inode.flag + 3);
                        inode.toDisk(inumber);
                    }
                    try {
                        wait();
                    } catch (InterruptedException e) {}
                }
                inode.flag = 2;
                break;
            }

            inumber = dir.ialloc(filename);
            inode = new Inode();
            inode.flag = 2;
            break;
        }

        inode.count++;
        inode.toDisk(inumber);
        FileTableEntry entry = new FileTableEntry(inode, inumber, mode);
        table.addElement(entry);
        return entry;
        // allocate a new file (structure) table entry for this file name
        // allocate/retrieve and register the corresponding inode using dir
        // increment this inode's count
        // immediately write back this inode to the disk
        // return a reference to this file (structure) table entry
    }

    public synchronized boolean ffree(FileTableEntry e) {
        if (table.removeElement(e)) {
            e.inode.count--;
            if (e.inode.flag == 1 || e.inode.flag == 2){
                e.inode.flag = 0;
            }
            if (e.inode.flag == 4 || e.inode.flag == 5){
                e.inode.flag = 3;
            }

            e.inode.toDisk(e.iNumber);
            notify();
            return true;
        }
        return false;
        // receive a file table entry reference
        // save the corresponding inode to the disk
        // free this file table entry.
        // return true if this file table entry found in my table
    }

    public synchronized boolean fempty() {
        return table.isEmpty( );  // return if table is empty
    }                            // should be called before starting a format
}