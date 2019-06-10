/** FileSystem.java
 *Name: Jordan Brown and Aaron Hays
 * Class: CSS 430
 * Project: P5
 * Description: This class is used to perform all disk operations.
 * It also instantiates instances of all the other classes that were written.
 * This file system carries out all of the essential components of a basic
 * file system such as read, write, delete, etc.
 */

public class FileSystem {

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    private Directory directory;
    private SuperBlock superBlock;
    private FileTable fileTable;


    /*----------------------------Constructor--------------------------------*/
    // Takes in desired number of blocks as parameter.
    // Creates SuperBlock, Directory, and FileTable
    public FileSystem(int numBlocks){

        superBlock = new SuperBlock(numBlocks); // create superblock with number of blocks
        directory = new Directory(superBlock.inodeBlocks);  // new directory
        fileTable = new FileTable(directory);   // new filetable

        FileTableEntry dirEntry = this.open("/", "r");  // read root from disk
        int dirSize = fsize(dirEntry);  // get size of entry

        if(dirSize > 0){    // if directory not empty, get data
            byte[] dirData = new byte[dirSize]; // new byte array to hold data
            read(dirEntry, dirData);    // read data into array
            directory.bytes2directory(dirData); // add to directory
        }
        close(dirEntry);
    }

    /*--------------------------------open-----------------------------------*/
    // Opens a file of the specified fileName
    public FileTableEntry open(String fileName, String mode) {

        FileTableEntry fileTableEntry = fileTable.falloc(fileName, mode);

        // check if the the entry is in write mode and check if all blocks not allocated
        if ((mode.equals("w")) && !this.deallocateBlocks(fileTableEntry)) {
            return null;
        }
        else {
            return fileTableEntry;
        }
    }

    /*-------------------------------close-----------------------------------*/
    // Closes a file of the specified fileName
    public boolean close(FileTableEntry dirEntry) {
        synchronized (dirEntry){ // sync so only one entry at a time can access
            dirEntry.count--;   // decrement count of users
            if(dirEntry.count > 0) return true;
        }
        return fileTable.ffree(dirEntry);
    }

    /*-------------------------------fsize-----------------------------------*/
    // Return length of entry inode to get file size
    int fsize(FileTableEntry ftEntry){
        synchronized (ftEntry){
            return ftEntry.inode.length;
        }
    }

    /*--------------------------------sync-----------------------------------*/
    // Syncs the Filesystem back to the disk.
    // Writes the Directory information to the disk and syncs the SuperBlock
    public void sync(){
        FileTableEntry fileTableEntry = open("/", "w");
        byte[] dirData = directory.directory2bytes();
        write(fileTableEntry, dirData);
        close(fileTableEntry);
        superBlock.sync();
    }

    /*-------------------------------format----------------------------------*/
    // Formats all  contents on the disk and creates new
    // SuperBlock, Directory, and FileTable
    public boolean format(int files){
        superBlock.format(files); // format number of files in superblock
        directory = new Directory(superBlock.inodeBlocks); // create new directory for files
        fileTable = new FileTable(directory);   // create filetable to store directory
        return true;
    }


    /*--------------------------------seek-----------------------------------*/
    // Updates the sync pointer of a FileTableEntry
    public int seek(FileTableEntry ftEntry, int offset, int position){
        synchronized (ftEntry){
            switch (position){
                case SEEK_SET: // start of file
                    if(offset >= 0 && offset <= fsize(ftEntry)){
                        ftEntry.seekPtr = offset; // set ptr to beginning of file
                        break;
                    }
                    return -1;


                case SEEK_CUR: // current seek pos
                    if (ftEntry.seekPtr + offset >= 0 &&
                            ftEntry.seekPtr + offset <= fsize(ftEntry)) {
                        ftEntry.seekPtr += offset;  // set to file size + offset
                        break;
                    }
                    return -1;

                case SEEK_END: // end of file
                    if (fsize(ftEntry) + offset < 0 ||
                            fsize(ftEntry) + offset > fsize(ftEntry)) {
                        return -1;
                    }
                    // make seekptr to be at end of file plus offset
                    ftEntry.seekPtr = fsize(ftEntry) + offset;
            }
            return ftEntry.seekPtr;
        }
    }

    /*--------------------------------read-----------------------------------*/
    // Reads a FileTableEntry into the passed buffer
    public int read(FileTableEntry ftEntry, byte[] buffer){

        // ensure that we are not in write or append mode
        if (!ftEntry.mode.equals("w") && !ftEntry.mode.equals("a")) {

            int bufferLength = buffer.length;
            int trackData = 0;
            int trackError = -1;
            int blockSize = Disk.blockSize;

            synchronized (ftEntry) {
                // read blocks of data and return
                while (bufferLength > 0 && ftEntry.seekPtr < fsize(ftEntry)) {
                    int currentBlock = ftEntry.inode.findBlock(ftEntry.seekPtr);
                    // return if currentblock = -1
                    if (currentBlock == trackError) break;

                    byte[] blockData = new byte[blockSize];
                    SysLib.rawread(currentBlock, blockData);

                    int offset = ftEntry.seekPtr % blockSize;
                    int remainingBlocks = blockSize - offset;
                    int remainingFile = fsize(ftEntry) - ftEntry.seekPtr;
                    // set remainingRead to the smallest between the three vars
                    int remainingRead = Math.min(Math.min(remainingBlocks, bufferLength), remainingFile);

                    System.arraycopy(blockData, offset, buffer, trackData, remainingRead);

                    ftEntry.seekPtr += remainingRead; // set seekptr to next point in the array
                    trackData += remainingRead; // add read data to trackdata
                    bufferLength -= remainingRead; // reduce buffer length
                }
                return trackData;
            }
        }
        else {
            return -1;
        }
    }

    /*-------------------------------write-----------------------------------*/
    // Writes the contents of buffer to the requested FileTableEntry
    public int write(FileTableEntry ftEntry, byte[] buffer){
        // ensure that we are not in read mode
        if(ftEntry.mode.equals("r") || buffer == null) return -1;

        synchronized (ftEntry){

            int blockSize = Disk.blockSize;
            int bufferLength = buffer.length;
            int offset = 0;

            while(bufferLength > 0){
                int currentBlock = ftEntry.inode.findBlock(ftEntry.seekPtr);
                if(currentBlock == -1){
                    short newBlock = (short)superBlock.getFreeBlock();
                    int index = ftEntry.inode.setTargetBlock(ftEntry.seekPtr, newBlock);

                    if(index == -3){ // indirect block unavailable

                        // find available free block
                        short freeBlock = (short)superBlock.getFreeBlock();

                        // return error if free block can't be updated
                        if(!ftEntry.inode.setIndexBlock(freeBlock)){
                            return -1;
                        }
                        // return error if update unsuccessful
                        if(ftEntry.inode.setTargetBlock(ftEntry.seekPtr, newBlock) != 0){
                            return -1;
                        }
                    }
                    // if index is not valid
                    if(index == -2 || index == -1){
                        return -1;
                    }

                    currentBlock = newBlock;
                }

                byte[] blockData = new byte[blockSize];
                if (SysLib.rawread(currentBlock, blockData) == -1){
                    System.exit(2);
                }

                // point to new beginning of data
                int newSeekPtr = ftEntry.seekPtr % blockSize;
                // end of data, so as to not overwrite
                int remaining = blockSize - newSeekPtr;

                int position = Math.min(remaining, bufferLength);
                System.arraycopy(buffer, offset, blockData, newSeekPtr, position);
                SysLib.rawwrite(currentBlock, blockData);

                // update variables to continue writing next iteration
                ftEntry.seekPtr += position;
                bufferLength -= position;
                offset += position;

                if (ftEntry.seekPtr > ftEntry.inode.length){
                    ftEntry.inode.length = ftEntry.seekPtr;
                }
            }
            // update inode
            ftEntry.inode.toDisk(ftEntry.iNumber);
            return offset;
        }
    }

    /*-------------------------------delete----------------------------------*/
    // Delete a file from the system
    public boolean delete(String name){
        FileTableEntry ftEntry = open(name, "w");
        // get inumber of points to the block to be deleted
        if(close(ftEntry) && directory.ifree(ftEntry.iNumber))
            return true;
        return false;
    }

    /*--------------------------deallocateBlocks-----------------------------*/
    // Deallocates all the blocks that ftEntry contains pointers to
    public boolean deallocateBlocks(FileTableEntry ftEntry){
        if(ftEntry.inode.count != 1)
            return false;

        byte[] data = ftEntry.inode.resetIndexBlock();
        if(data != null){
            int offset = 0;
            short blockNum;
            while((blockNum = SysLib.bytes2short(data, offset)) != -1){
                superBlock.returnBlock(blockNum);
            }
        }

        int blockID = 0;
        int inodeDirectSize = 11;

        while(true){
            if(blockID >= inodeDirectSize){
                ftEntry.inode.toDisk(ftEntry.iNumber);
                return true;
            }
            if(ftEntry.inode.direct[blockID] != -1){
                superBlock.returnBlock(ftEntry.inode.direct[blockID]);
                ftEntry.inode.direct[blockID] = -1;
            }
            blockID++;
        }
    }
}


