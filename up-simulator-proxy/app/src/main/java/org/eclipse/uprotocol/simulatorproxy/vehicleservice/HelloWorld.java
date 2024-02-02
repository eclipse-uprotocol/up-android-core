package org.eclipse.uprotocol.simulatorproxy.vehicleservice;

import com.google.protobuf.Descriptors;

import org.covesa.uservice.example.hello_world.v1.HelloWorldService;
import org.covesa.uservice.vehicle.body.cabin_climate.v1.CabinClimateService;
import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;

public class HelloWorld extends BaseService {
    Descriptors.ServiceDescriptor serviceDescriptor = HelloWorldService.getDescriptor().findServiceByName("HelloWorld");

    @Override
    public void onCreate() {
        super.onCreate();
        initializeUPClient(serviceDescriptor);
    }

}

