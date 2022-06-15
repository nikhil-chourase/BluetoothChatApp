package com.nikhil6920.bluetoothchatapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.stream.Stream;
//import java.util.logging.Handler;

public class ChatUtils {
    private Context context;
    private final Handler handler;
    private BluetoothAdapter bluetoothAdapter;
    private ConnectThread connectThread;
    private AcceptThread acceptThread;

    private ConnectedThread connectedThread;

    private final UUID APP_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private final String APP_NAME = "bluetoothChatApp";


    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;


    private int state;

    public ChatUtils(Context context, Handler handler){
        this.context = context;
        this.handler = handler;

        state = STATE_NONE;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    }

    public int getState() {
        return state;
    }

    public synchronized void setState(int state) {
        this.state = state;
        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGED,state,-1).sendToTarget();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private synchronized void start(){
        if(connectThread!=null){
            connectThread.cancel();
            connectThread = null;
        }
        if(acceptThread == null){
            acceptThread = new AcceptThread();
            acceptThread.start();
        }

        if(connectedThread!=null){
            connectedThread.cancel();
            connectedThread = null;
        }
        setState(STATE_LISTEN);
    }

    public synchronized void stop(){
        if(connectThread!=null){
            connectThread.cancel();
            connectThread=null;
        }
        if(acceptThread!=null){
            acceptThread.cancel();
            acceptThread = null;
        }
        if(connectedThread!=null){
            connectedThread.cancel();
            connectedThread = null;
        }
        setState(STATE_NONE);

    }

    public void connect(BluetoothDevice device){
        if(state == STATE_CONNECTING){
            connectThread.cancel();
            connectThread=null;
        }
        connectThread = new ConnectThread(device);
        connectThread.start();

        if(connectedThread!=null){
            connectedThread.cancel();
            connectedThread = null;
        }
        setState(STATE_CONNECTING);
    }

    public void write(byte[] buffer){
        ConnectedThread conThread;
        synchronized (this){
            if(state!= STATE_CONNECTED){
                return;
            }
            conThread = connectedThread;

        }
        conThread.write(buffer);

    }

    private class AcceptThread extends Thread{
        private BluetoothServerSocket serverSocket;
        @RequiresApi(api = Build.VERSION_CODES.Q)
        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            try{
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,APP_UUID);
            }catch (IOException e){
                Log.e("Accept-> Constructor",e.toString());
            }
            serverSocket = tmp;

        }
        public void run(){
            BluetoothSocket socket = null;
            try{
                socket = serverSocket.accept();
            }catch(IOException e){
                Log.e("Accept-> run",e.toString());
                try{
                    serverSocket.close();
                }catch (IOException e1){
                    Log.e("Accept-> Close",e.toString());

                }
                if(socket!=null){
                    switch (state){
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            connect(socket.getRemoteDevice());
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            try {
                                socket.close();
                            }catch (IOException e1){
                                Log.e("Accept-> CloseSocket",e.toString());
                            }
                            break;

                    }
                }

            }

        }
        public void cancel(){
            try{
                serverSocket.close();
            }catch (IOException e){
                Log.e("Accept-> CloseServer",e.toString());


            }

        }
    }


    private class ConnectThread extends Thread{
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device){
            this.device = device;

            BluetoothSocket tnp = null;
            try{
                tnp = device.createRfcommSocketToServiceRecord(APP_UUID);
            }
            catch (IOException e) {
                Log.e("Connect Constructor",e.toString());
                e.printStackTrace();
            }

            socket = tnp;
        }
        public void run(){
            try{
                socket.connect();
            }catch (IOException e){
                Log.e("Connect Constructor",e.toString());
                try {
                    socket.close();
                }catch (IOException e1){
                    Log.e("Connect-> Construtor",e1.toString());
                }
                connectionFailed();
                return;

            }
            synchronized (ChatUtils.this){
                connectThread = null;
            }
            connected(socket,device);
        }
        public void cancel(){
            try{
                socket.close();
            }catch (IOException e1){
                Log.e("Connect->Cancel", e1.toString());

            }
        }
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;


        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;

            InputStream tnpIn = null;
            OutputStream tnpOut = null;

            try{
                tnpIn = socket.getInputStream();
                tnpOut = socket.getOutputStream();
            }catch (IOException e){

            }

            inputStream = tnpIn;
            outputStream = tnpOut;



         }
         public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            try{
                bytes = inputStream.read(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_READ,bytes,-1,buffer).sendToTarget();
            }catch (IOException e){
                connectionLost();

            }
         }

         public void write(byte[] buffer){
            try{
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
            }catch (IOException e){

            }

         }
         public void cancel(){
            try {
                socket.close();
            }catch (IOException e){


            }
         }
    }

    private void connectionLost(){
        Message message = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST,"ConnectionLost");
        message.setData(bundle);
        handler.sendMessage(message);

        ChatUtils.this.start();
    }


    private synchronized void connectionFailed(){
        Message message = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST,"Cant connect to the device");
        message.setData(bundle);
        handler.sendMessage(message);

        ChatUtils.this.start();
    }

    private synchronized void connected(BluetoothSocket socket,BluetoothDevice device){
        if(connectThread!=null){
            connectThread.cancel();
            connectThread=null;
        }
        if(connectedThread!=null){
            connectedThread.cancel();
            connectedThread = null;
        }

        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        Message message = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        message.setData(bundle);
        handler.sendMessage(message);

        setState(STATE_CONNECTED);

    }

}
