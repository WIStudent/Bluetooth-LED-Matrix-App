package de.tobiastrumm.bluetoothledmatrix;


import android.content.Context;

import com.polidea.rxandroidble.RxBleClient;

public class RxBleClientSingelton {

    private static RxBleClient rxBleClient = null;

    private RxBleClientSingelton(){}

    public static RxBleClient getInstance(Context context){
        if(rxBleClient == null){
            rxBleClient = RxBleClient.create(context);
        }
        return rxBleClient;
    }
}
