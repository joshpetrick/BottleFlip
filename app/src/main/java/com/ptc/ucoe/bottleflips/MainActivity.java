package com.ptc.ucoe.bottleflips;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.module.Accelerometer;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private BtleService.LocalBinder serviceBinder;
    public Accelerometer accelerometer;

    public static String MW_MAC_ADDRESS= "50:EF:14:A2:78:84";
    private MetaWearBoard board;

    public void retrieveBoard(String macAddr) {
        final BluetoothManager btManager=
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice=
                btManager.getAdapter().getRemoteDevice(macAddr);

        // Create a MetaWear board object for the Bluetooth Device
        board= serviceBinder.getMetaWearBoard(remoteDevice);

    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        System.out.println(name.toString()+" Service Connected");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        System.out.println(name.toString()+" Service Disconnected");
    }

    public void startAccelerometer(View view)
    {
        if(accelerometer != null)
        {
            accelerometer.acceleration().start();
            accelerometer.start();
        }
    }

    public void stopAccelerometer(View view)
    {
        accelerometer.acceleration().stop();
        accelerometer.stop();
    }

    public void connectBTLE(View view)
    {
        System.out.println("button clicked!");
        //check mac address isnull
        Spinner macText = (Spinner) findViewById(R.id.macAddrSpinner);
        final TextView rawData= (TextView)findViewById(R.id.dataText);
        final String macAddr = String.valueOf(macText.getSelectedItem());
        if(macText.getSelectedItemPosition()<50)
        {
            //mac address has been changed
            Toast.makeText(MainActivity.this,"MAC Addr Selected: "+macAddr,Toast.LENGTH_LONG).show();
            retrieveBoard(macAddr);
        }

        //attempt to connect
        if(board != null)
        {
            board.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {

                @Override
                public Task<Route> then(Task<Void> task) throws Exception {

                            accelerometer = board.getModule(Accelerometer.class);
                            accelerometer.configure().odr(25f).commit();



                        return accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
                            @Override
                            public void configure(RouteComponent source) {
                                source.stream(new Subscriber() {
                                    @Override
                                    public void apply(Data data, Object... env) {
                                        rawData.setText(data.value(Accelerometer.class).toString());
                                    }
                                });
                            }
                        });
                }
            }).continueWith(new Continuation<Route, Void>() {

                @Override
                public Void then(Task<Route> task) throws Exception {
                    Button connectedButton = (Button)findViewById(R.id.connectButton);
                    if(task.isFaulted())
                    {
                        connectedButton.setBackgroundColor(Color.RED);
                        Toast.makeText(MainActivity.this,"Task Has Faulted",Toast.LENGTH_SHORT).show();
                    }
                    else
                    {
                        connectedButton.setBackgroundColor(Color.GREEN);
                    }
                    return null;
                }
            });
/*
            board.getModule(Accelerometer.class).start();
            board.getModule(Accelerometer.class).acceleration().addRouteAsync(new RouteBuilder() {
                @Override
                public void configure(RouteComponent source) {
                    source.stream(new Subscriber() {
                        @Override
                        public void apply(Data data, Object... env) {
                            System.out.println(data.value(Accelerometer.class));
                        }
                    });
                }
            });
            */
        }
        //in main loop read data and update textfield or syso at min
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync().continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) throws Exception {
                return task.isFaulted() ? reconnect(board) : task;
            }
        });
    }

}
