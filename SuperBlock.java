/**
 * Created by
 * Aaron
 * 2019-06-07
 */

public class SuperBlock {

    private final int INODE_BLOCKS = 64;
    private final int BLOCK_SIZE = Disk.blockSize;
    private final int INODE_LOC = 4;
    private final int BLOCK_LOC = 0;



    public SuperBlock(int totalBlocks){

        byte[] superBlock = new byte[BLOCK_SIZE];

    }

}
