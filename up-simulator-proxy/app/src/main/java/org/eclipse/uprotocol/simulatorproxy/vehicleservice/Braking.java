package org.eclipse.uprotocol.simulatorproxy.vehicleservice;

import com.google.protobuf.Descriptors;

import org.covesa.uservice.chassis.braking.v1.BrakingService;
import org.covesa.uservice.vehicle.body.cabin_climate.v1.CabinClimateService;
import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;

public class Braking extends BaseService {
    Descriptors.ServiceDescriptor serviceDescriptor = BrakingService.getDescriptor().findServiceByName("Braking");

    @Override
    public void onCreate() {
        super.onCreate();
        initializeUPClient(serviceDescriptor);
    }

}
