package com.able;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.ServiceLoader;


public class SpiTest {
    public static void main(String[] args) {
        //java 的spi机制
        ServiceLoader<Car> cars = ServiceLoader.load(Car.class);
        for (Car car : cars) {
            System.out.println(car.getCarName(null));
        }
        //dubbo spi扩展机制
        ExtensionLoader<Car> carExtensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
        Car red = carExtensionLoader.getExtension("red");
        System.err.println(red.getCarName(null));
        final Car black = carExtensionLoader.getExtension("black");
        System.err.println(black.getCarName(null));

//        //扩展点加载器
//        ExtensionLoader<Protocol> extensionLoader = ExtensionLoader.getExtensionLoader(Protocol.class);
//        //获取相应的扩展点
//        Protocol protocol = extensionLoader.getExtension("http");
//        System.out.println(protocol);

        ExtensionLoader<Person> extensionLoader = ExtensionLoader.getExtensionLoader(Person.class);
        Person person = extensionLoader.getExtension("black");

        URL url = new URL("x", "localhost", 8080);
        url = url.addParameter("car", "black");
        System.out.println(person.getCar().getCarName(url));


//        ExtensionLoader<Filter> extensionLoader = ExtensionLoader.getExtensionLoader(Filter.class);
//        URL url = new URL("http://", "localhost", 8080);
//        url = url.addParameter("cache", "test");
//        List<Filter> activateExtensions = extensionLoader.getActivateExtension(url, new String[]{"validation"}, CommonConstants.CONSUMER);
//        for (Filter activateExtension : activateExtensions) {
//            System.out.println(activateExtension);
//        }

    }
}
