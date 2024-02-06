package org.eclipse.uprotocol.simulatorproxy.vehicleservice;

import com.google.protobuf.Descriptors;

import org.covesa.uservice.propulsion.transmission.v1.TransmissionService;
import org.covesa.uservice.vehicle.body.cabin_climate.v1.CabinClimateService;
import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;

public class Transmission extends BaseService {
    Descriptors.ServiceDescriptor serviceDescriptor = TransmissionService.getDescriptor().findServiceByName("Transmission");

    @Override
    public void onCreate() {
        super.onCreate();
        initializeUPClient(serviceDescriptor);
    }

}
