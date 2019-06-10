/** SuperBlock.java
 *Name: Jordan Brown and Aaron Hays
 * Class: CSS 430
 * Project: P5
 * Description: The SuperBlock is a file containing metadata of the filesystem.
 * It is represented on-disk as block 0
 *
 *
 */

public class SuperBlock {

    private final int NUM_OF_INODE_BLOCKS = 64;    // total num of inode blocks
    private final int BLOCK_SIZE = Disk.blockSize; // block size from Disk
    private final int INODE_LOC = 4;    // location of first inode
    private final int BLOCK_LOC = 0;    // location of superblock
    private final int FREELIST_LOC = 8; // location of freelist

    public int inodeBlocks;     // number of inodes
    public int totalBlocks;     // number of disk blocks
    public int freeList;        // the block number of the free list's head

    /*---------------------------------Constructor-----------------------------*/
    // Constructs a SuperBlock and sets the totalBlocks inodeBlocks,
    // and freeList member variables.
    public SuperBlock(int blockNum){

        byte[] superBlock = new byte[BLOCK_SIZE];
        SysLib.rawread(BLOCK_LOC, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, BLOCK_LOC);
        inodeBlocks = SysLib.bytes2int(superBlock, INODE_LOC);
        freeList = SysLib.bytes2int(superBlock, FREELIST_LOC);

        // if disk is invalid, format
        if(totalBlocks != blockNum || inodeBlocks <= 0 || freeList < 2){
            totalBlocks = blockNum;
            format();
        }
    }

    /*----------------------------------format-------------------------------*/
    // Default format formats default number of inode blocks
    public void format(){
        format(NUM_OF_INODE_BLOCKS);
    }

    /*----------------------------------format-------------------------------*/
    // Removes all data from disk and resets member variables
    public void format(int blockNum){

        inodeBlocks = blockNum;

        for (short i = 0; i < inodeBlocks; i++) {
            Inode tempInode = new Inode();
            tempInode.flag = 0;
            tempInode.toDisk(i);
        }

        freeList = 2 + inodeBlocks / 16;

        for (int i = freeList; i < totalBlocks; i++) {
            byte[] tempBytes = new byte[BLOCK_SIZE];

            for (int j = 0; j < BLOCK_SIZE; j++) {
                tempBytes[j] = 0;
            }

            SysLib.int2bytes(i + 1, tempBytes, 0);
            SysLib.rawwrite(i, tempBytes);
        }

        sync();
    }

    /*------------------------------------sync-------------------------------*/
    // Syncs the SuperBlock on disk with this SuperBlock object to write any
    // new updates to member variables
    public void sync(){
        byte[] blockData = new byte[BLOCK_SIZE];

        SysLib.int2bytes(totalBlocks, blockData, BLOCK_LOC);
        SysLib.int2bytes(inodeBlocks, blockData, INODE_LOC);
        SysLib.int2bytes(freeList, blockData, FREELIST_LOC);

        SysLib.rawwrite(BLOCK_LOC, blockData);
    }

    /*---------------------------------getFreeBlock--------------------------*/
    // Returns the first free block from the freeList
    public int getFreeBlock(){

        int freeBlock = freeList;

        // Check that the free block is in valid range
        if(freeBlock > 0 && freeBlock < totalBlocks){
            byte[] blockData = new byte[BLOCK_SIZE];
            SysLib.rawread(freeList, blockData);

            // update the free block
            freeList = SysLib.bytes2int(blockData, BLOCK_LOC);

            // update the new bree block with the old's data
            SysLib.int2bytes(BLOCK_LOC, blockData, BLOCK_LOC);
            SysLib.rawwrite(freeBlock, blockData);
        }
        return freeBlock;
    }

    /*--------------------------------returnBlock----------------------------*/
    // Adds a newly freed block back to the free list by setting the block at
    // blockNum to be the new free block and making the old free block to be
    // the next free block in the list
    public boolean returnBlock(int blockNum){
        if(blockNum < 0){
            return false;
        }

        byte[] blockData = new byte[BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            blockData[i] = 0;
        }

        SysLib.int2bytes(freeList, blockData, BLOCK_LOC);
        SysLib.rawwrite(blockNum, blockData);

        freeList = blockNum;
        return true;
    }
}
