package org.eclipse.uprotocol.simulatorproxy.utils;

import android.content.Context;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import org.eclipse.uprotocol.simulatorproxy.R;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UUri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static void readResourceCatalog(Context ctx) {
        // Assuming you are in an Activity or other context
        InputStream inputStream = ctx.getResources().openRawResource(R.raw.resource_catalog);

        // Now you can use the InputStream to read the contents of the CSV file
        // For example, you can use a BufferedReader to read line by line
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        HashMap<String, String> resultList = new HashMap<>();
        try {
            CSVReader csvReader = new CSVReader(reader);


            String[] line;

            while ((line = csvReader.readNext()) != null) {
                // Add the value from the first column to the result list
                if (line.length > 0) {
                    resultList.put(line[0], line[1]);
                }
            }

            // Close the CSVReader
            csvReader.close();
        } catch (IOException | CsvValidationException ignored) {

        }

        Constants.RESOURCE_CATALOG = resultList;

    }

    public static List<UUri> readTopicsFromEntity(String entity) {
        List<UUri> topics= new ArrayList<>();
        // Iterate over the HashMap using Enhanced for loop
        for (Map.Entry<String, String> entry : Constants.RESOURCE_CATALOG.entrySet()) {
            String topic = entry.getKey();
            if(topic.contains("/" + entity + "/")) {
                topics.add(LongUriSerializer.instance().deserialize(topic));
            }
        }
        return topics;
    }

}
