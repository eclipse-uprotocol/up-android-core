package org.eclipse.uprotocol.simulatorproxy.vehicleservice;

import com.google.protobuf.Descriptors;

import org.covesa.uservice.vehicle.body.cabin_climate.v1.CabinClimateService;
import org.covesa.uservice.vehicle.v1.VehicleService;
import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;

public class Vehicle extends BaseService {
    Descriptors.ServiceDescriptor serviceDescriptor = VehicleService.getDescriptor().findServiceByName("Vehicle");

    @Override
    public void onCreate() {
        super.onCreate();
        initializeUPClient(serviceDescriptor);
    }

}
