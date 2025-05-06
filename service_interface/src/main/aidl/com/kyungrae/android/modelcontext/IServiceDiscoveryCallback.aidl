// IServiceDiscoveryCallback.aidl
package com.kyungrae.android.modelcontext;

import com.kyungrae.android.modelcontext.ServiceInfo;

// 서비스 발견 결과 전달을 위한 콜백 인터페이스
interface IServiceDiscoveryCallback {
    void onServicesDiscovered(in List<ServiceInfo> services);
}
