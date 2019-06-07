public class Directory {
    private static int maxChars = 30; // max characters of each file name
    private static int maxBytes = maxChars * 2; // max bytes of each file name

    // Directory entries
    private int[] fsize;        // each element stores a different file size.
    private char[][] fnames;    // each element stores a different file name.

    public Directory(int maxInumber) { // directory constructor
        fsize = new int[maxInumber];     // maxInumber = max files
        for ( int i = 0; i < maxInumber; i++ )
            fsize[i] = 0;                 // all file size initialized to 0
        fnames = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsize[0] = root.length( );        // fsize[0] is the size of "/".
        root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
    }

    public void bytes2directory(byte data[]) {
        // assumes data[] received directory information from disk
        int offset = 0;
        for (int i = 0; i < fsize.length; i++) {
            fsize[i] = SysLib.bytes2int(data, offset);
            offset += 4;
        }
        // initializes the Directory instance with this data[]
        for (int i = 0; i < fsize.length; i++) {
            String temp = new String(data, offset, maxBytes);
            temp.getChars(0, fsize[i], fnames[i], 0);
            offset += maxBytes;
        }
    }

    public byte[] directory2bytes() {
        int allocatedBytes = fsize.length * 4 + fnames.length;
        byte [] dir = new byte[allocatedBytes * maxBytes];
        int offset = 0;
        // converts and return Directory information into a plain byte array
        for (int i = 0; i < fsize.length; i++) {
            SysLib.int2bytes(fsize[i], dir, offset);
            offset += 4;
        }
        for (int i = 0; i < fsize.length; i++) {
            String temp = new String(fnames[i], 0, fsize[i]);
            byte[] bytes = temp.getBytes();
            System.arraycopy(bytes, 0, dir, offset, bytes.length);
            offset += maxBytes;
        }
        return dir;
    }

    public short ialloc(String filename) {
        // filename is the one of a file to be created.
        for (short i = 0; i < fsize.length; i++) {
            if (fsize[i] == 0) {
                // allocates a new inode number for this filename
                int file = Math.min(filename.length(), maxChars);
                fsize[i] = file;
                filename.getChars(0, fsize[i], fnames[i], 0);
                return i;
            }
        }
        return -1;
    }

    // Check if file is found at iNumber's location and deallocate it
    public boolean ifree(short iNumber) {
        if(iNumber < maxChars && fsize[iNumber] > 0) {
            fsize[iNumber] = 0;
            return true;
        }
        else {
            return false;
        }
    }

    // Returns the inumber corresponding to this filename
    public short namei(String filename) {
        for (short i = 0; i < fsize.length; i++){
            if (filename.length() == fsize[i]){
                String temp = new String(fnames[i], 0, fsize[i]);
                if(filename.equals(temp)){
                    return i;
                }
            }
        }
        return -1;
    }
}
