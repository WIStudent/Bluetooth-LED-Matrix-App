package de.tobiastrumm.bluetoothledmatrix;

import android.util.Log;

public class LedMatrix {

    private final static String TAG = LedMatrix.class.getSimpleName();

    private byte[][] data;


    public LedMatrix(){
        this.data = new byte[8][8];
    }

    /**
     * Set the values of a row.
     * @param row Row which should be changed
     * @param data Data array for the row. Length must be 2.
     */
    public synchronized void setRow(int row, byte[] data){
        if(data.length != 2){
            throw new IllegalArgumentException("Length of data must be 2");
        }
        this.data[row] = data;
    }

    /**
     * Returns a copy of bytes with the pixel at column set to the passed color
     * @param col column (between 0 and 7)
     * @param color color (between 0 and 3)
     * @return a copy of bytes with the pixel at column set to the passed color
     */
    public synchronized byte[] setColor(int row, int col, int color) {
        byte[] original = data[row].clone();
        int half = col / 4;
        int offset = col % 4;
        int temp_byte = 0;
        switch(offset) {
            case 0:
                temp_byte = (data[row][half] & 0x3F) | (color << 6);
                break;
            case 1:
                temp_byte = (data[row][half] & 0xCF) | (color << 4);
                break;
            case 2:
                temp_byte = (data[row][half] & 0xF3) | (color << 2);
                break;
            case 3:
                temp_byte = (data[row][half] & 0xFC) | color;
                break;
        }

        data[row][half] = (byte) temp_byte;

        Log.d(TAG, "Old: Col " + col + " Color " + color + ": " + String.format("%16s", Integer.toBinaryString(original[0])).replace(' ', '0') + " " + String.format("%8s", Integer.toBinaryString(original[1])).replace(' ', '0'));
        Log.d(TAG, "New: Col " + col + " Color " + color + ": " + String.format("%16s", Integer.toBinaryString(data[row][0])).replace(' ', '0') + " " + String.format("%8s", Integer.toBinaryString(data[row][1])).replace(' ', '0'));

        return data[row].clone();
    }

}
