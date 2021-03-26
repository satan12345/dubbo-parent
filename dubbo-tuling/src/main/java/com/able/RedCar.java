package com.able;

import org.apache.dubbo.common.URL;

public class RedCar implements Car {
    @Override
    public String getCarName(URL url) {
        return "red";
    }
}
