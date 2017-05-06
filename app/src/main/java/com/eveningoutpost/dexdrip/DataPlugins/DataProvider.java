package com.eveningoutpost.dexdrip.DataPlugins;

/**
 * Created by adrian on 09/12/15.
 */
public interface DataProvider{

    //TODO: write proper JavaDoc ;)

    /*
    * Register a DataReceiver that will receive callbacks when new data is available.
    * The registration of the first DataReceiver may start background services or tasks for data retrieval.
    * */
    public void registerDataReceiver(DataReceiver dataReceiver);


    /*
    * Like registerDataReceiver(DataReceiver dataReceiver) but only expects callbacks for a certain type.
    * This might save load as the DataProvider only has to retrieve data for types that are registered for
    * **/
    public void registerDataReceiver(DataReceiver dataReceiver, String type);


    /*
    * Register for several types of data.
    * */
    public void registerDataReceiver(DataReceiver dataReceiver, String[] types);


    /*
      Will deregister a DataReceiver.
    * In case the count of registered receivers reaches 0, all data retrieval services or tasks
    * that may have been started will get stopped.
    * */
    public void deregisterDataReceiver(DataReceiver dataReceiver);

    /*
    * Will deregister all DataReceivers.
    * In case a Service or Background task is started it will be stopped.
    * */
    public void tearDown();
}
