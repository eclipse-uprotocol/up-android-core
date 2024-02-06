package org.eclipse.uprotocol.simulatorproxy.vehicleservice;

import com.google.protobuf.Descriptors;

import org.covesa.uservice.vehicle.body.cabin_climate.v1.CabinClimateService;
import org.covesa.uservice.vehicle.chassis.v1.ChassisService;
import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;

public class Chassis extends BaseService {
    Descriptors.ServiceDescriptor serviceDescriptor = ChassisService.getDescriptor().findServiceByName("Chassis");

    @Override
    public void onCreate() {
        super.onCreate();
        initializeUPClient(serviceDescriptor);
    }

}
