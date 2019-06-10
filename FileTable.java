/** FileTable.java
 *Name: Jordan Brown and Aaron Hays
 * Class: CSS 430
 * Project: P5
 * Description: FileTable holds a set of FileTableEntries in a vector.
 */

import java.util.Vector;

public class FileTable {

    private Vector table;         // the actual entity of this file table
    private Directory dir;        // the root directory

    /*----------------------------Constructor--------------------------------*/
    public FileTable(Directory directory) {
        table = new Vector( );     // instantiate a file (structure) table
        dir = directory;           // receive a reference to the Director
    }                             // from the file system

    /*------------------------------falloc-----------------------------------*/
    // Allocate a new file (structure) table entry for this file name
    public synchronized FileTableEntry falloc(String filename, String mode) {
        Inode inode;
        short inumber;
        while (true) {
            if (filename.equals("/")) { // root directory
                inumber = 0;
            }
            else {
                inumber = dir.namei(filename);
            }

            // invalid inumber
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

        // increment this inode's count
        inode.count++;
        // immediately write back this inode to the disk
        inode.toDisk(inumber);
        // allocate/retrieve and register the corresponding inode using dir
        FileTableEntry entry = new FileTableEntry(inode, inumber, mode);
        table.addElement(entry);
        // return a reference to this file (structure) table entry
        return entry;
    }

    /*------------------------------ffree------------------------------------*/
    // Frees a FileTableEntry in the FileTable and writes back the file to disk
    public synchronized boolean ffree(FileTableEntry e) {
        // check if the entry is in the table
        if (table.removeElement(e)) {
            e.inode.count--;
            if (e.inode.flag == 1 || e.inode.flag == 2){
                e.inode.flag = 0;
            }
            if (e.inode.flag == 4 || e.inode.flag == 5){
                e.inode.flag = 3;
            }

            // save the corresponding inode to the disk
            e.inode.toDisk(e.iNumber);
            notify();
            return true;
        }
        return false;
    }

    /*------------------------------fempty-----------------------------------*/
    // Check if the table is empty. Should be called before starting a format.
    public synchronized boolean fempty() {
        return table.isEmpty( );
    }
}