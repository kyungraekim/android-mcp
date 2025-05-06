// IModelContextService.aidl
package com.kyungrae.android.modelcontext;

import com.kyungrae.android.modelcontext.IServiceDiscoveryCallback;
import com.kyungrae.android.modelcontext.ServiceInfo;

// 서비스 관리자 인터페이스
interface IModelContextService {
    // 서비스 발견 요청
    void discoverServices(IServiceDiscoveryCallback callback);

    // 특정 유형의 서비스 목록 요청
    List<ServiceInfo> getServicesByType(String type);

    // 서비스 연결 요청
    boolean connectToService(in ServiceInfo serviceInfo);

    // 서비스 연결 해제 요청
    void disconnectFromService(in ServiceInfo serviceInfo);

    // 연결된 서비스를 통한 계산 요청
    String calculate(String serviceType, int value);

    // 특정 유형의 서비스가 연결되었는지 확인
    boolean isServiceTypeConnected(String serviceType);

    // 서비스 버전 정보 조회
    String getServiceVersion(String serviceType);
}