package org.eclipse.uprotocol.simulatorproxy.vehicleservice;

import com.google.protobuf.Descriptors;

import org.covesa.uservice.vehicle.body.cabin_climate.v1.CabinClimateService;
import org.covesa.uservice.vehicle.body.mirrors.v1.MirrorsService;
import org.eclipse.uprotocol.simulatorproxy.BaseService;
import org.eclipse.uprotocol.simulatorproxy.utils.Constants;

public class BodyMirrors extends BaseService {
    Descriptors.ServiceDescriptor serviceDescriptor = MirrorsService.getDescriptor().findServiceByName("BodyMirrors");

    @Override
    public void onCreate() {
        super.onCreate();
        initializeUPClient(serviceDescriptor);
    }

}
