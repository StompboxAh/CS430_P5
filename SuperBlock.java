/**
 * Created by
 * Aaron
 * 2019-06-07
 */

public class SuperBlock {

    private final int NUM_OF_INODE_BLOCKS = 64;    // total no of inode blocks
    private final int BLOCK_SIZE = Disk.blockSize;  // block size from Disk class
    private final int INODE_LOC = 4;    // location of first inode
    private final int BLOCK_LOC = 0;    // location of superblock
    private final int FREELIST_LOC = 8; // location of freelist

    public int inodeBlocks;
    public int totalBlocks;
    public int freeList;





    public SuperBlock(int totalBlocks){

        byte[] superBlock = new byte[BLOCK_SIZE];
        SysLib.rawread(BLOCK_LOC, superBlock);
        this.totalBlocks = SysLib.bytes2int(superBlock, BLOCK_LOC);
        inodeBlocks = SysLib.bytes2int(superBlock, INODE_LOC);
        freeList = SysLib.bytes2int(superBlock, FREELIST_LOC);


        if(this.totalBlocks != totalBlocks || inodeBlocks <= 0 || freeList < 2){
            this.totalBlocks = totalBlocks;
            format(NUM_OF_INODE_BLOCKS);
            SysLib.cerr("formatting...");
        }
    }

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

            SysLib.int2bytes(i + 1, tempBytes, 0);
            SysLib.rawwrite(i, tempBytes);
        }

        sync();
    }

    public void sync(){
        byte[] blockData = new byte[BLOCK_SIZE];

        SysLib.int2bytes(totalBlocks, blockData, BLOCK_LOC);
        SysLib.int2bytes(inodeBlocks, blockData, INODE_LOC);
        SysLib.int2bytes(freeList, blockData, FREELIST_LOC);

        SysLib.rawread(BLOCK_LOC, blockData);
    }

    public int getFreeBlock(){

        int freeBlock = freeList;
        if(freeBlock > 0){
            byte[] blockData = new byte[BLOCK_SIZE];
            SysLib.rawread(freeList, blockData);

            freeList = SysLib.bytes2int(blockData, BLOCK_LOC);
            SysLib.int2bytes(BLOCK_LOC, blockData, BLOCK_LOC);
            SysLib.rawwrite(freeBlock, blockData);
        }
        return freeBlock;
    }

    public boolean setFreeBlock(int blockNum){
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
