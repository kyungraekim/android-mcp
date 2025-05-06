// IModelContextApp.aidl
package com.kyungrae.android.modelcontext;

interface IModelContextApp {
    String getServiceType();

    String calculate(int value);

    String getServiceVersion();
}