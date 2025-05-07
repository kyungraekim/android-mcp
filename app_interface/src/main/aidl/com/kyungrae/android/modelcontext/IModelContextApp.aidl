// IModelContextApp.aidl
package com.kyungrae.android.modelcontext;

interface IModelContextApp {
    String getServiceType();

    String calculate(String value);

    String getServiceVersion();
}