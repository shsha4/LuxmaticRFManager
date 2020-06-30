package com.mat.luxmaticrfmanager;

import java.util.HashMap;

public class CL420UUID {
    private static HashMap<String, String> attributes = new HashMap<>();
    public static String MAT_CL420 = "589294b8-7d2c-11ea-bc55-0242ac130003";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        attributes.put(MAT_CL420, "MAT_CL420 SERVICES");
        attributes.put("e1422d8e-9b07-11ea-bb37-0242ac130002", "디밍 제어");
        attributes.put("e1423072-9b07-11ea-bb37-0242ac130002", "일출 일몰 오프셋 설정");
        attributes.put("e142331a-9b07-11ea-bb37-0242ac130002", "예약 점소등 시간");
        attributes.put("e14235ae-9b07-11ea-bb37-0242ac130002", "심야 점소등 시간");
        attributes.put("e14236b2-9b07-11ea-bb37-0242ac130002", "심야 점소등 기간");
        attributes.put("e142377a-9b07-11ea-bb37-0242ac130002", "기기 상태");
        attributes.put("e1423860-9b07-11ea-bb37-0242ac130002", "DEV ADDRESS SET");
        attributes.put("e142393c-9b07-11ea-bb37-0242ac130002", "DEV APP KEY SET");
        attributes.put("e1423c20-9b07-11ea-bb37-0242ac130002", "DEV NET KEY SET");
    }
}
