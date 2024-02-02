/*
 * Copyright (C) GM Global Technology Operations LLC 2021.
 * All Rights Reserved.
 * GM Confidential Restricted.
 */

package org.eclipse.uprotocol.simulatorproxy;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.uprotocol.simulatorproxy.utils.Constants;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.BodyMirrors;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Braking;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.CabinClimate;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Chassis;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Engine;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Exterior;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.HelloWorld;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Horn;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Suspension;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Transmission;
import org.eclipse.uprotocol.simulatorproxy.vehicleservice.Vehicle;

public class MainActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Access properties from gradle.properties
        String appVersionName = BuildConfig.VERSION_NAME;
        TextView tv = (TextView) findViewById(R.id.tvVersion);
        tv.setText(getString(R.string.version) + appVersionName);

        // Start the SocketServerService when the activity is created
        startService(new Intent(this, SimulatorProxyService.class));
        //vehicle service will start only when there is request from host
        Constants.ENTITY_SERVICE_MAP.put("body.cabin_climate", CabinClimate.class);
        Constants.ENTITY_SERVICE_MAP.put("body.mirrors", BodyMirrors.class);
        Constants.ENTITY_SERVICE_MAP.put("chassis.braking", Braking.class);
        Constants.ENTITY_SERVICE_MAP.put("chassis", Chassis.class);
        Constants.ENTITY_SERVICE_MAP.put("propulsion.engine", Engine.class);
        Constants.ENTITY_SERVICE_MAP.put("vehicle.exterior", Exterior.class);
        Constants.ENTITY_SERVICE_MAP.put("example.hello_world", HelloWorld.class);
        Constants.ENTITY_SERVICE_MAP.put("body.horn", Horn.class);
        Constants.ENTITY_SERVICE_MAP.put("chassis.suspension", Suspension.class);
        Constants.ENTITY_SERVICE_MAP.put("propulsion.transmission", Transmission.class);
        Constants.ENTITY_SERVICE_MAP.put("vehicle", Vehicle.class);


    }
}