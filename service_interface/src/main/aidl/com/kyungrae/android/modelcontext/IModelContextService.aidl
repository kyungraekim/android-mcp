// IModelContextService.aidl
package com.kyungrae.android.modelcontext;

import com.kyungrae.android.modelcontext.IServiceDiscoveryCallback;
import com.kyungrae.android.modelcontext.ServiceInfo;
import com.kyungrae.android.modelcontext.ToolInfo;
import com.kyungrae.android.modelcontext.ResourceInfo;
import com.kyungrae.android.modelcontext.ContentItem;

/**
 * Enhanced service manager interface that provides MCP-like functionality
 * Manages the discovery and connection to MCP-compatible services
 */
interface IModelContextService {
    // Basic management operations
    void discoverServices(IServiceDiscoveryCallback callback);
    List<ServiceInfo> getServicesByType(String type);
    boolean connectToService(in ServiceInfo serviceInfo);
    void disconnectFromService(in ServiceInfo serviceInfo);

    // Basic calculation (backward compatibility)
    String calculate(String serviceType, String value);

    // Connection status information
    boolean isServiceTypeConnected(String serviceType);
    String getServiceVersion(String serviceType);

    // MCP-like tool operations
    List<ToolInfo> listTools(String serviceType);
    List<ContentItem> callTool(String serviceType, String toolName, String jsonArguments);

    // MCP-like resource operations
    List<ResourceInfo> listResources(String serviceType);
    List<ContentItem> readResource(String serviceType, String uri);

    // Capability checking
    boolean serviceHasCapability(String serviceType, String capability);
}